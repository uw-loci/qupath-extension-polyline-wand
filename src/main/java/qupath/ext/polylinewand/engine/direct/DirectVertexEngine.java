package qupath.ext.polylinewand.engine.direct;

import qupath.ext.polylinewand.BrushMode;
import qupath.ext.polylinewand.EndpointSide;
import qupath.ext.polylinewand.PolylineCompactor;
import qupath.ext.polylinewand.PolylineWandParameters;
import qupath.ext.polylinewand.StrokeContext;
import qupath.ext.polylinewand.WorkingPolyline;
import qupath.ext.polylinewand.engine.BrushEngine;
import qupath.lib.geom.Point2;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Engine A. Direct displacement of polyline vertices using a uniform-grid
 * spatial index, with optional local densification and end-of-stroke
 * simplification.
 */
public final class DirectVertexEngine implements BrushEngine {

    private WorkingPolyline working;
    private UniformGridIndex index;
    private final AtomicReference<List<Point2>> previewRef = new AtomicReference<>(List.of());
    private volatile boolean dirty = false;
    private int firstTouched = Integer.MAX_VALUE;
    private int lastTouched = Integer.MIN_VALUE;
    private int densifyBudget = 0;
    private Point2 lastBrushCenter = null;
    private EndpointSide eraseEnd = EndpointSide.NONE;
    private int indexedTopologyVersion = -1;

    @Override
    public void beginStroke(StrokeContext ctx, Point2 imgPt) {
        ROI roi = ctx.getAnnotation().getROI();
        if (!(roi instanceof PolylineROI poly)) {
            // Defensive -- the event handler should have already promoted LineROI.
            working = new WorkingPolyline(List.of(imgPt, imgPt));
        } else {
            working = new WorkingPolyline(poly.getAllPoints());
        }
        publishPreview();
        rebuildIndex(ctx.getBrushRadius());
        densifyBudget = PolylineWandParameters.getDirectMaxInsertionsPerStroke();
        lastBrushCenter = imgPt;
        firstTouched = Integer.MAX_VALUE;
        lastTouched = Integer.MIN_VALUE;
        eraseEnd = decideEraseEnd(ctx, imgPt);
    }

    private EndpointSide decideEraseEnd(StrokeContext ctx, Point2 imgPt) {
        if (ctx.getMode() != BrushMode.ERASE_FROM_END || working.size() < 2) {
            return EndpointSide.NONE;
        }
        Point2 first = working.get(0);
        Point2 last = working.get(working.size() - 1);
        double dStart = distance(first, imgPt);
        double dEnd = distance(last, imgPt);
        return dStart <= dEnd ? EndpointSide.START : EndpointSide.END;
    }

    @Override
    public void applyDrag(StrokeContext ctx, Point2 imgPt, long nowNs) {
        if (working == null || working.size() < 2) {
            return;
        }
        double radius = ctx.getBrushRadius();
        Point2 motion = lastBrushCenter == null
                ? new Point2(0, 0)
                : new Point2(imgPt.getX() - lastBrushCenter.getX(),
                             imgPt.getY() - lastBrushCenter.getY());
        lastBrushCenter = imgPt;

        switch (ctx.getMode()) {
            case ERASE_FROM_END:
                applyEraseFromEnd(imgPt, radius);
                break;
            case SMOOTH:
                applySmooth(imgPt, radius);
                break;
            case PUSH:
            case AUTO:
            default:
                if (PolylineWandParameters.getDirectDensifyEnabled()) {
                    densifyAroundBrush(imgPt, radius);
                }
                applyPush(imgPt, motion, radius, ctx);
                break;
        }
        publishPreview();
    }

    private void applyPush(Point2 brushCenter, Point2 motion, double radius, StrokeContext ctx) {
        ensureIndex(radius);
        Set<Integer> hits = new HashSet<>();
        index.query(brushCenter.getX(), brushCenter.getY(), radius, hits::add);
        if (hits.isEmpty()) {
            return;
        }
        FalloffProfile falloff = PolylineWandParameters.getDirectFalloffProfile();
        double radialBias = clamp01(PolylineWandParameters.getDirectRadialBias());
        double speed = ctx.getVelocity().getSpeed();
        double damping = computeDamping(motion, speed);
        double motionMag = Math.hypot(motion.getX(), motion.getY());
        for (int idx : hits) {
            Point2 v = working.get(idx);
            double dx = v.getX() - brushCenter.getX();
            double dy = v.getY() - brushCenter.getY();
            double dist = Math.hypot(dx, dy);
            double w = falloff.weight(dist, radius);
            if (w <= 0) {
                continue;
            }
            double radialUx = 0, radialUy = 0;
            if (dist > 1e-9) {
                radialUx = dx / dist;
                radialUy = dy / dist;
            }
            double moveX = (1 - radialBias) * motion.getX() + radialBias * radialUx * motionMag;
            double moveY = (1 - radialBias) * motion.getY() + radialBias * radialUy * motionMag;
            double scale = w * damping;
            working.setPoint(idx, new Point2(v.getX() + moveX * scale, v.getY() + moveY * scale));
            firstTouched = Math.min(firstTouched, idx);
            lastTouched = Math.max(lastTouched, idx);
        }
        // Per-frame compactor pass: drop vertices that pile up within the brush
        // footprint as a result of pushing one part of the line toward another.
        // Limited to the touched range so untouched regions of long polylines
        // pay no cost.
        if (firstTouched <= lastTouched) {
            double minSpacing = Math.max(0.5, radius * 0.05);
            int from = Math.max(0, firstTouched - 1);
            int to = Math.min(working.size() - 1, lastTouched + 1);
            int dropped = PolylineCompactor.compactRangeInPlace(working, from, to, minSpacing);
            if (dropped > 0) {
                indexedTopologyVersion = -1;
                lastTouched -= dropped;
            }
        }
        dirty = true;
    }

    private void applySmooth(Point2 brushCenter, double radius) {
        ensureIndex(radius);
        Set<Integer> hits = new HashSet<>();
        index.query(brushCenter.getX(), brushCenter.getY(), radius, hits::add);
        if (hits.size() < 2) {
            return;
        }
        FalloffProfile falloff = PolylineWandParameters.getDirectFalloffProfile();
        // Single-pass weighted Laplacian smoothing: shift each affected vertex toward its neighbors' midpoint.
        for (int idx : hits) {
            if (idx == 0 || idx == working.size() - 1) {
                continue;
            }
            Point2 prev = working.get(idx - 1);
            Point2 next = working.get(idx + 1);
            Point2 v = working.get(idx);
            double midX = 0.5 * (prev.getX() + next.getX());
            double midY = 0.5 * (prev.getY() + next.getY());
            double dx = v.getX() - brushCenter.getX();
            double dy = v.getY() - brushCenter.getY();
            double w = falloff.weight(Math.hypot(dx, dy), radius);
            if (w <= 0) {
                continue;
            }
            working.setPoint(idx, new Point2(
                    v.getX() + (midX - v.getX()) * w * 0.5,
                    v.getY() + (midY - v.getY()) * w * 0.5));
            firstTouched = Math.min(firstTouched, idx);
            lastTouched = Math.max(lastTouched, idx);
        }
        dirty = true;
    }

    private void applyEraseFromEnd(Point2 brushCenter, double radius) {
        if (working.size() <= 2 || eraseEnd == EndpointSide.NONE) {
            return;
        }
        if (eraseEnd == EndpointSide.START) {
            int removeUpTo = -1;
            for (int i = 0; i < working.size(); i++) {
                Point2 v = working.get(i);
                if (distance(v, brushCenter) <= radius) {
                    removeUpTo = i;
                } else {
                    break;
                }
            }
            int maxRemovable = Math.max(0, working.size() - 2);
            removeUpTo = Math.min(removeUpTo, maxRemovable - 1);
            if (removeUpTo >= 0) {
                working.removeRange(0, removeUpTo);
                indexedTopologyVersion = -1;
                dirty = true;
            }
        } else {
            int removeFrom = working.size();
            for (int i = working.size() - 1; i >= 0; i--) {
                Point2 v = working.get(i);
                if (distance(v, brushCenter) <= radius) {
                    removeFrom = i;
                } else {
                    break;
                }
            }
            int minIndex = working.size() - Math.max(0, working.size() - 2);
            if (removeFrom < working.size() && removeFrom >= minIndex) {
                working.removeRange(removeFrom, working.size() - 1);
                indexedTopologyVersion = -1;
                dirty = true;
            }
        }
    }

    private void densifyAroundBrush(Point2 brushCenter, double radius) {
        if (densifyBudget <= 0) {
            return;
        }
        double targetSpacing = Math.max(1.0,
                radius * PolylineWandParameters.getDirectDensifySpacingRatio());
        // Walk segments whose midpoint is within (radius + segLen/2) of the brush center.
        // Insert in reverse so indexes stay valid.
        boolean topologyChanged = false;
        for (int i = working.size() - 2; i >= 0 && densifyBudget > 0; i--) {
            Point2 a = working.get(i);
            Point2 b = working.get(i + 1);
            double midX = 0.5 * (a.getX() + b.getX());
            double midY = 0.5 * (a.getY() + b.getY());
            double segLen = distance(a, b);
            if (Math.hypot(midX - brushCenter.getX(), midY - brushCenter.getY()) > radius + segLen / 2) {
                continue;
            }
            if (segLen <= targetSpacing) {
                continue;
            }
            // Refuse if we're already at half-spacing density.
            if (segLen < targetSpacing * 0.5) {
                continue;
            }
            int splits = (int) Math.floor(segLen / targetSpacing);
            int allowed = Math.min(splits - 1, densifyBudget);
            if (allowed <= 0) {
                continue;
            }
            for (int s = allowed; s >= 1; s--) {
                double t = s / (double) (allowed + 1);
                Point2 mid = new Point2(a.getX() + t * (b.getX() - a.getX()),
                                        a.getY() + t * (b.getY() - a.getY()));
                working.insertPoint(i + 1, mid);
                densifyBudget--;
                topologyChanged = true;
            }
        }
        if (topologyChanged) {
            indexedTopologyVersion = -1;
        }
    }

    @Override
    public List<Point2> endStroke(StrokeContext ctx) {
        if (working == null || working.size() < 2) {
            return List.of();
        }
        // The shared end-of-stroke simplifier in the event handler runs over the whole point list.
        // Engines may also do their own local-range simplify; we leave that to the shared pass to
        // keep behavior consistent across A/B/C.
        return working.snapshot();
    }

    @Override
    public void cancel(StrokeContext ctx) {
        // Nothing to release.
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

    private void publishPreview() {
        previewRef.set(new ArrayList<>(working.view()));
    }

    private void ensureIndex(double radius) {
        if (index == null || indexedTopologyVersion != working.getTopologyVersion()) {
            rebuildIndex(radius);
        }
    }

    private void rebuildIndex(double radius) {
        index = new UniformGridIndex(working.view(), Math.max(1.0, radius));
        indexedTopologyVersion = working.getTopologyVersion();
    }

    private double computeDamping(Point2 motion, double speed) {
        double s = PolylineWandParameters.getDirectVelocityDampingStrength();
        if (s <= 0.0 || speed <= 1e-9) {
            return 1.0;
        }
        // Without a stable per-vertex normal in this engine, fall back to scalar damping
        // proportional to speed (approximate "moving fast = damp").
        double damped = Math.max(0.25, 1.0 - s * Math.min(1.0, speed * 0.01));
        return damped;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static double distance(Point2 a, Point2 b) {
        return Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
    }
}
