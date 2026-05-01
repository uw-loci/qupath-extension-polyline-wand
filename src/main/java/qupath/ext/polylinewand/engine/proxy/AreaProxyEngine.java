package qupath.ext.polylinewand.engine.proxy;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import qupath.ext.polylinewand.BrushMode;
import qupath.ext.polylinewand.EndpointSide;
import qupath.ext.polylinewand.PolylineWandLogging;
import qupath.ext.polylinewand.PolylineWandParameters;
import qupath.ext.polylinewand.StrokeContext;
import qupath.ext.polylinewand.engine.BrushEngine;
import qupath.lib.geom.Point2;
import qupath.lib.roi.GeometryTools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Engine B. Buffers the polyline to a thin area, accumulates brush stamps
 * via JTS union/difference, and on stroke release runs OpenCV Zhang-Suen
 * thinning + raster trace to recover a polyline.
 */
public final class AreaProxyEngine implements BrushEngine {

    /**
     * Single shared background executor so {@link #beginStroke},
     * {@link #applyDrag}, and {@link #endStroke} never block the FX thread
     * with JTS buffer/overlay or Zhang-Suen thinning. One thread total --
     * tasks queue serially per stroke.
     */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "polyline-wand-area-proxy");
        t.setDaemon(true);
        return t;
    });

    private List<Point2> originalPoints;
    private volatile Geometry workingArea;
    private double bufferDistance;
    private List<EndpointAnchor> endpointAnchors;
    private Point2 lastBrushPt;
    private final AtomicReference<List<Point2>> previewRef = new AtomicReference<>(List.of());
    private volatile boolean dirty = false;
    private int stampsSinceCleanup = 0;
    private EndpointSide eraseEnd = EndpointSide.NONE;
    private double brushRadius;
    private Future<?> pendingTask;
    private volatile boolean cancelled = false;

    @Override
    public void beginStroke(StrokeContext ctx, Point2 imgPt) {
        // Operate on the LocalRegion's body only -- the head and tail of the
        // original polyline are spliced back at commit by the event handler.
        // This prevents the skeletonization round-trip from re-shaping
        // untouched parts of long polylines.
        List<Point2> body = ctx.getRegion().body;
        if (body.size() < 2) {
            originalPoints = List.of(imgPt, imgPt);
        } else {
            originalPoints = new ArrayList<>(body);
        }
        previewRef.set(new ArrayList<>(originalPoints));

        brushRadius = ctx.getBrushRadius();
        bufferDistance = Math.max(
                PolylineWandParameters.getProxyBufferMinPx(),
                PolylineWandParameters.getProxyBufferWidthFraction() * brushRadius);

        // Capture endpoint anchors immediately so applyDrag has them on first stamp.
        endpointAnchors = new ArrayList<>();
        if (PolylineWandParameters.getProxyAnchorEndpoints() && originalPoints.size() >= 2) {
            Point2 first = originalPoints.get(0);
            Point2 last = originalPoints.get(originalPoints.size() - 1);
            endpointAnchors.add(new EndpointAnchor(new Coordinate(first.getX(), first.getY()), bufferDistance));
            endpointAnchors.add(new EndpointAnchor(new Coordinate(last.getX(), last.getY()), bufferDistance));
        }

        eraseEnd = decideEraseEnd(ctx, imgPt);
        lastBrushPt = imgPt;
        dirty = false;
        cancelled = false;
        workingArea = null;

        // Buffer the polyline off the FX thread. applyDrag will queue stamps
        // behind this task on the same single-thread executor.
        final List<Point2> ptsCopy = new ArrayList<>(originalPoints);
        final double bufDist = bufferDistance;
        pendingTask = EXECUTOR.submit(() -> {
            if (cancelled) return;
            Geometry lineGeom = polylineToJts(ptsCopy);
            Geometry buffered;
            try {
                buffered = lineGeom.buffer(bufDist,
                        BufferParameters.DEFAULT_QUADRANT_SEGMENTS, BufferParameters.CAP_ROUND);
            } catch (RuntimeException ex) {
                PolylineWandLogging.LOG.warn("AreaProxyEngine: buffer failed: {}", ex.getMessage(), ex);
                buffered = lineGeom.buffer(bufDist);
            }
            if (!cancelled) {
                workingArea = buffered;
            }
        });
    }

    private EndpointSide decideEraseEnd(StrokeContext ctx, Point2 imgPt) {
        if (ctx.getMode() != BrushMode.ERASE_FROM_END || originalPoints.size() < 2) {
            return EndpointSide.NONE;
        }
        Point2 first = originalPoints.get(0);
        Point2 last = originalPoints.get(originalPoints.size() - 1);
        return distance(first, imgPt) <= distance(last, imgPt) ? EndpointSide.START : EndpointSide.END;
    }

    @Override
    public void applyDrag(StrokeContext ctx, Point2 imgPt, long nowNs) {
        boolean subtract = ctx.getMode() == BrushMode.ERASE_FROM_END;
        double diameter = brushRadius * 2.0;
        // Build the stamp on the FX thread (cheap: a small ellipse / capsule).
        final Geometry stamp;
        if (lastBrushPt == null) {
            stamp = BrushStamper.stampForFirstPoint(imgPt.getX(), imgPt.getY(), diameter);
        } else {
            stamp = BrushStamper.stampForSegment(lastBrushPt.getX(), lastBrushPt.getY(),
                    imgPt.getX(), imgPt.getY(), diameter);
        }
        lastBrushPt = imgPt;
        // Defer the (potentially expensive) overlay to the background thread.
        final boolean doSubtract = subtract;
        pendingTask = EXECUTOR.submit(() -> {
            if (cancelled || workingArea == null) {
                return;
            }
            Geometry next;
            try {
                next = doSubtract
                        ? OverlayNGRobust.overlay(workingArea, stamp, OverlayNG.DIFFERENCE)
                        : OverlayNGRobust.overlay(workingArea, stamp, OverlayNG.UNION);
            } catch (RuntimeException ex) {
                PolylineWandLogging.LOG.debug("AreaProxyEngine: stamp overlay failed: {}", ex.getMessage());
                try {
                    next = doSubtract ? workingArea.difference(stamp) : workingArea.union(stamp);
                } catch (RuntimeException ex2) {
                    PolylineWandLogging.LOG.warn("AreaProxyEngine: fallback overlay also failed: {}", ex2.getMessage());
                    return;
                }
            }
            workingArea = next;
            stampsSinceCleanup++;
            if (stampsSinceCleanup >= 10) {
                try {
                    workingArea = org.locationtech.jts.simplify.VWSimplifier.simplify(
                            workingArea, 0.25 * bufferDistance);
                } catch (RuntimeException ex) {
                    // ignore
                }
                stampsSinceCleanup = 0;
            }
        });
        dirty = true;
        // Mid-stroke preview = original points until end-of-stroke. Cheap and avoids
        // skeletonizing per-frame.
    }

    @Override
    public List<Point2> endStroke(StrokeContext ctx) {
        // Drain any in-flight overlay tasks before reading workingArea.
        // Cap the wait so a pathological JTS hang cannot freeze the UI forever;
        // if we time out we fall back to the original polyline.
        if (pendingTask != null) {
            try {
                pendingTask.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                PolylineWandLogging.LOG.warn("AreaProxyEngine: stamp pipeline did not drain in time; reverting");
                cancelled = true;
                pendingTask.cancel(true);
                return new ArrayList<>(originalPoints);
            } catch (Exception ex) {
                PolylineWandLogging.LOG.warn("AreaProxyEngine: stamp pipeline failed: {}", ex.getMessage());
            }
        }
        if (workingArea == null || workingArea.isEmpty()) {
            return new ArrayList<>(originalPoints);
        }
        SkeletonizationParams params = new SkeletonizationParams(
                PolylineWandParameters.getProxyMaskMaxDim(),
                Math.max(2.0, bufferDistance * 2.0),
                PolylineWandParameters.getProxyCloseGapsBeforeThinning(),
                PolylineWandParameters.getProxySimplifyTolerance(),
                PolylineWandParameters.getProxyDisconnectionPolicy(),
                PolylineWandParameters.getProxyAnchorEndpoints());
        // Skeletonize off the FX thread with a 5s cap so a runaway thinning
        // pass cannot lock the UI.
        Geometry skeleton;
        try {
            final Geometry snapshot = workingArea;
            Future<Geometry> f = EXECUTOR.submit(() -> RasterSkeletonizer.skeletonize(
                    snapshot, endpointAnchors, params));
            skeleton = f.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            PolylineWandLogging.LOG.warn("AreaProxyEngine: skeletonization timed out; reverting");
            return new ArrayList<>(originalPoints);
        } catch (Exception ex) {
            PolylineWandLogging.LOG.warn("AreaProxyEngine: skeletonization failed: {}", ex.getMessage(), ex);
            return new ArrayList<>(originalPoints);
        }
        if (skeleton == null || skeleton.isEmpty()) {
            return new ArrayList<>(originalPoints);
        }
        // Disconnection-policy = REJECT_EDIT forces revert to original
        if (skeleton instanceof MultiLineString
                && params.disconnectionPolicy() == DisconnectionPolicy.REJECT_EDIT) {
            PolylineWandLogging.LOG.info("AreaProxyEngine: rejecting edit (would split polyline)");
            return new ArrayList<>(originalPoints);
        }
        List<Coordinate> pickedCoords = new ArrayList<>();
        if (skeleton instanceof LineString ls) {
            for (Coordinate c : ls.getCoordinates()) {
                pickedCoords.add(c);
            }
        } else if (skeleton instanceof MultiLineString mls) {
            // KEEP_LONGEST behavior (the rasterizer may return a MultiLineString
            // depending on its policy implementation).
            int bestIdx = 0;
            int bestLen = -1;
            for (int i = 0; i < mls.getNumGeometries(); i++) {
                LineString cand = (LineString) mls.getGeometryN(i);
                if (cand.getCoordinates().length > bestLen) {
                    bestLen = cand.getCoordinates().length;
                    bestIdx = i;
                }
            }
            for (Coordinate c : mls.getGeometryN(bestIdx).getCoordinates()) {
                pickedCoords.add(c);
            }
        } else {
            return new ArrayList<>(originalPoints);
        }
        if (pickedCoords.size() < 2) {
            return new ArrayList<>(originalPoints);
        }
        List<Point2> out = new ArrayList<>(pickedCoords.size());
        for (Coordinate c : pickedCoords) {
            out.add(new Point2(c.x, c.y));
        }
        return out;
    }

    @Override
    public void cancel(StrokeContext ctx) {
        cancelled = true;
        if (pendingTask != null) {
            pendingTask.cancel(true);
        }
        workingArea = null;
        endpointAnchors = null;
        originalPoints = null;
    }

    @Override
    public List<Point2> previewSnapshot() {
        return previewRef.get();
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void markClean() {
        dirty = false;
    }

    private static Geometry polylineToJts(List<Point2> pts) {
        Coordinate[] coords = new Coordinate[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            coords[i] = new Coordinate(pts.get(i).getX(), pts.get(i).getY());
        }
        return GeometryTools.getDefaultFactory().createLineString(coords);
    }

    private static double distance(Point2 a, Point2 b) {
        return Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
    }
}
