package qupath.ext.polylinewand;

import java.awt.geom.Point2D;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import qupath.ext.polylinewand.engine.BrushEngine;
import qupath.ext.polylinewand.engine.EngineFactory;
import qupath.lib.geom.Point2;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.List;

/**
 * Mouse / scroll / key dispatch for the Polyline Wand.
 * <p>
 * On press: identify the selected line/polyline annotation, promote LineROI -> PolylineROI
 * if needed, decide brush mode, build a {@link StrokeContext}, instantiate the active
 * {@link BrushEngine}, and start a {@link CommitThrottler}.
 * On drag: feed the engine; the throttler handles mid-stroke commits.
 * On release: ask the engine for the final point list, atomically commit one final ROI
 * and fire one {@code isChanging=false} hierarchy event.
 */
public final class PolylineWandEventHandler implements EventHandler<MouseEvent> {

    private QuPathViewer viewer;
    private PolylineWandOverlay overlay;
    private final EventHandler<KeyEvent> keyEscapeHandler = this::handleKeyEvent;

    // Per-stroke state -- all null when no stroke is active
    private BrushEngine engine;
    private StrokeContext ctx;
    private CommitThrottler throttler;
    private PathObject targetAnnotation;
    private ImagePlane targetPlane;
    private double brushRadiusForStroke;
    private LocalRegion targetRegion;

    public boolean isStrokeActive() {
        return engine != null;
    }

    public void attach(QuPathViewer viewer) {
        this.viewer = viewer;
        this.overlay = new PolylineWandOverlay(viewer);
        viewer.getCustomOverlayLayers().add(overlay);
        viewer.getView().addEventFilter(KeyEvent.KEY_PRESSED, keyEscapeHandler);
    }

    public void detach(QuPathViewer viewer) {
        if (engine != null) {
            cancelStroke();
        }
        if (overlay != null) {
            viewer.getCustomOverlayLayers().remove(overlay);
            overlay = null;
        }
        viewer.getView().removeEventFilter(KeyEvent.KEY_PRESSED, keyEscapeHandler);
        if (this.viewer == viewer) {
            this.viewer = null;
        }
    }

    public void resetState() {
        if (engine != null) {
            cancelStroke();
        }
    }

    @Override
    public void handle(MouseEvent e) {
        if (viewer == null) {
            return;
        }
        if (e.getEventType() == MouseEvent.MOUSE_MOVED) {
            updateCursorOverlay(e);
        } else if (e.getEventType() == MouseEvent.MOUSE_EXITED) {
            if (overlay != null) {
                overlay.hideCursor();
            }
        } else if (e.getEventType() == MouseEvent.MOUSE_PRESSED) {
            if (e.getButton() == MouseButton.PRIMARY) {
                onPress(e);
            }
        } else if (e.getEventType() == MouseEvent.MOUSE_DRAGGED) {
            if (engine != null) {
                onDrag(e);
            }
        } else if (e.getEventType() == MouseEvent.MOUSE_RELEASED) {
            if (engine != null && e.getButton() == MouseButton.PRIMARY) {
                onRelease(e);
            }
        }
    }

    public void handleScroll(ScrollEvent e) {
        if (viewer == null) {
            return;
        }
        // Alt+wheel adjusts brush radius. Plain wheel (and other modifiers) is left
        // alone so QuPath's normal pan/zoom continues to work as expected.
        if (!e.isAltDown() || e.isControlDown() || e.isShiftDown()) {
            return;
        }
        double current = PolylineWandParameters.getBrushRadius();
        double step = Math.max(1.0, current * 0.1);
        double next = current + (e.getDeltaY() > 0 ? step : -step);
        next = Math.max(1.0, next);
        PolylineWandParameters.brushRadiusProperty().set(next);
        e.consume();
        if (overlay != null) {
            Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, false);
            overlay.updateCursor(p.getX(), p.getY(), effectiveImageRadius(next),
                    PolylineWandParameters.getBrushMode());
        }
    }

    private void handleKeyEvent(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE && engine != null) {
            cancelStroke();
            e.consume();
        }
    }

    // ------------------------------------------------------------------
    // Stroke lifecycle
    // ------------------------------------------------------------------

    private void onPress(MouseEvent e) {
        PathObject selected = viewer.getSelectedObject();
        if (selected == null || !selected.isAnnotation() || selected.isLocked()) {
            return;
        }
        ROI roi = selected.getROI();
        if (!(roi instanceof LineROI) && !(roi instanceof PolylineROI)) {
            return;
        }

        // Promote LineROI -> PolylineROI in place so engines uniformly see polylines.
        if (roi instanceof LineROI line && selected instanceof PathROIObject roiObj) {
            PolylineROI promoted = LineRoiPromoter.promote(line,
                    PolylineWandParameters.getLineConversionVertexCount());
            roiObj.setROI(promoted);
            roi = promoted;
            PathObjectHierarchy hierarchy = viewer.getHierarchy();
            if (hierarchy != null) {
                hierarchy.fireObjectsChangedEvent(this, List.of(selected), true);
            }
        }

        Point2D imgPt2D = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, false);
        Point2 imgPt = new Point2(imgPt2D.getX(), imgPt2D.getY());

        brushRadiusForStroke = effectiveImageRadius(PolylineWandParameters.getBrushRadius());

        BrushMode mode = decideMode(e, roi, imgPt, brushRadiusForStroke);

        // Scissors / cut-at-click is a one-shot: split the polyline and
        // return without starting a stroke.
        if (mode == BrushMode.CUT_AT_POINT && roi instanceof PolylineROI poly) {
            performCut(selected, poly, imgPt);
            e.consume();
            return;
        }

        StrokeVelocityTracker velocity = new StrokeVelocityTracker();
        velocity.reset(imgPt, System.nanoTime());

        // Extract the locked editable body. The head and tail are spliced
        // back at commit so unmodified segments stay bit-exact.
        List<Point2> allPoints = ((PolylineROI) roi).getAllPoints();
        double regionMult = PolylineWandParameters.getLocalRegionRadiusMultiplier();
        if (regionMult <= 0.0) {
            // Disabled -> body = entire polyline.
            targetRegion = LocalRegion.extract(allPoints, imgPt, Double.POSITIVE_INFINITY);
        } else {
            double regionDist = Math.max(brushRadiusForStroke, brushRadiusForStroke * regionMult);
            targetRegion = LocalRegion.extract(allPoints, imgPt, regionDist);
        }

        targetAnnotation = selected;
        targetPlane = roi.getImagePlane();
        ctx = new StrokeContext(viewer, targetAnnotation, targetPlane, mode,
                brushRadiusForStroke, velocity, targetRegion);
        engine = EngineFactory.create(PolylineWandParameters.getEngineKind());

        try {
            engine.beginStroke(ctx, imgPt);
        } catch (RuntimeException ex) {
            PolylineWandLogging.LOG.warn("Polyline Wand engine beginStroke failed: {}", ex.getMessage(), ex);
            engine = null;
            ctx = null;
            targetAnnotation = null;
            targetPlane = null;
            return;
        }

        throttler = new CommitThrottler(viewer, targetAnnotation, targetPlane, engine,
                targetRegion, PolylineWandParameters.getCommitThrottleMs());
        throttler.start();

        if (overlay != null) {
            overlay.updateCursor(imgPt.getX(), imgPt.getY(), brushRadiusForStroke, mode);
        }
        e.consume();
    }

    private void onDrag(MouseEvent e) {
        Point2D imgPt2D = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, false);
        Point2 imgPt = new Point2(imgPt2D.getX(), imgPt2D.getY());
        long now = System.nanoTime();
        ctx.getVelocity().push(imgPt, now);
        try {
            engine.applyDrag(ctx, imgPt, now);
        } catch (RuntimeException ex) {
            PolylineWandLogging.LOG.warn("Polyline Wand engine applyDrag failed: {}", ex.getMessage(), ex);
        }
        if (overlay != null) {
            overlay.updateCursor(imgPt.getX(), imgPt.getY(), brushRadiusForStroke, ctx.getMode());
        }
        e.consume();
    }

    private void onRelease(MouseEvent e) {
        try {
            commitFinal();
        } finally {
            tearDownStroke();
        }
        e.consume();
    }

    private void cancelStroke() {
        if (engine != null && ctx != null) {
            try {
                engine.cancel(ctx);
            } catch (RuntimeException ex) {
                PolylineWandLogging.LOG.warn("Polyline Wand engine cancel failed: {}", ex.getMessage(), ex);
            }
        }
        tearDownStroke();
    }

    private void tearDownStroke() {
        if (throttler != null) {
            throttler.stop();
            throttler = null;
        }
        engine = null;
        ctx = null;
        targetAnnotation = null;
        targetPlane = null;
        targetRegion = null;
        if (overlay != null) {
            overlay.hideCursor();
        }
    }

    private void commitFinal() {
        List<Point2> editedBody;
        try {
            editedBody = engine.endStroke(ctx);
        } catch (RuntimeException ex) {
            PolylineWandLogging.LOG.warn("Polyline Wand engine endStroke failed: {}", ex.getMessage(), ex);
            return;
        }
        if (editedBody == null) {
            PolylineWandLogging.LOG.debug("Polyline Wand: engine returned null body; skipping final commit");
            return;
        }
        // End-of-stroke cleanup pipeline:
        //   1. Compact body (drop crowded vertices) -- bounded to body only
        //   2. Splice head + body + tail
        //   3. LoopRemover -- runs on full polyline so cross-region loops still
        //      get cleaned (e.g., body bulged into the tail region)
        //   4. Simplify body indices only -- DO NOT simplify across the splice
        //      seam, so head and tail remain bit-exact
        double minSpacing = Math.max(0.5, brushRadiusForStroke * 0.05);
        editedBody = PolylineCompactor.compact(editedBody, minSpacing);
        double tol = PolylineWandParameters.getEndStrokeSimplifyTolerance();
        if (tol > 0.0) {
            editedBody = StrokeSimplifier.simplifyRange(editedBody, tol);
        }
        List<Point2> finalPoints = targetRegion.splice(editedBody);
        finalPoints = LoopRemover.untangle(finalPoints);
        if (finalPoints.size() < 2) {
            PolylineWandLogging.LOG.debug("Polyline Wand: cleanup left <2 points; skipping commit");
            return;
        }
        ROI finalRoi = ROIs.createPolylineROI(finalPoints, targetPlane);
        if (targetAnnotation instanceof PathROIObject roiObj) {
            roiObj.setROI(finalRoi);
        }
        PathObjectHierarchy hierarchy = viewer.getHierarchy();
        if (hierarchy != null) {
            hierarchy.fireObjectsChangedEvent(this, List.of(targetAnnotation), false);
        }
        viewer.repaintEntireImage();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private BrushMode decideMode(MouseEvent e, ROI roi, Point2 imgPt, double radius) {
        BrushMode pref = PolylineWandParameters.getBrushMode();
        if (pref == BrushMode.AUTO) {
            if (PolylineWandParameters.getEraseAtEndpoints() && !e.isShiftDown()) {
                double prox = PolylineWandParameters.getEndpointEraseProximity() * radius;
                EndpointSide hit = EndpointDetector.hitTest(roi, imgPt, prox);
                if (hit != EndpointSide.NONE) {
                    return BrushMode.ERASE_FROM_END;
                }
            }
            return BrushMode.PUSH;
        }
        return pref;
    }

    /**
     * Split the selected polyline into two PolylineROI annotations at the
     * point on the polyline closest to {@code clickImagePt}. The original
     * annotation is removed; the two new pieces inherit its path class.
     */
    private void performCut(PathObject annotation, PolylineROI poly, Point2 clickImagePt) {
        List<Point2> pts = poly.getAllPoints();
        if (pts.size() < 2) {
            return;
        }
        // Find closest segment + closest point on it
        int bestSeg = -1;
        double bestDistSq = Double.MAX_VALUE;
        Point2 bestPt = null;
        double bestT = 0.0;
        for (int i = 0; i < pts.size() - 1; i++) {
            Point2 a = pts.get(i);
            Point2 b = pts.get(i + 1);
            double abx = b.getX() - a.getX();
            double aby = b.getY() - a.getY();
            double abLen2 = abx * abx + aby * aby;
            double t = abLen2 < 1e-12 ? 0.0
                    : ((clickImagePt.getX() - a.getX()) * abx
                     + (clickImagePt.getY() - a.getY()) * aby) / abLen2;
            t = Math.max(0.0, Math.min(1.0, t));
            double px = a.getX() + t * abx;
            double py = a.getY() + t * aby;
            double dx = px - clickImagePt.getX();
            double dy = py - clickImagePt.getY();
            double d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestPt = new Point2(px, py);
                bestSeg = i;
                bestT = t;
            }
        }
        if (bestSeg < 0 || bestPt == null) {
            return;
        }
        // Build the two halves. If t is at an endpoint of the segment, the
        // existing vertex serves as the split point (avoid duplicate insert).
        List<Point2> first = new ArrayList<>();
        for (int i = 0; i <= bestSeg; i++) {
            first.add(pts.get(i));
        }
        if (bestT > 1e-3) {
            first.add(bestPt);
        }
        List<Point2> second = new ArrayList<>();
        if (bestT < 1.0 - 1e-3) {
            second.add(bestPt);
        }
        for (int i = bestSeg + 1; i < pts.size(); i++) {
            second.add(pts.get(i));
        }
        if (first.size() < 2 || second.size() < 2) {
            PolylineWandLogging.LOG.debug("Polyline Wand: cut would leave a degenerate piece; skipping");
            return;
        }
        PolylineROI roi1 = qupath.lib.roi.ROIs.createPolylineROI(first, poly.getImagePlane());
        PolylineROI roi2 = qupath.lib.roi.ROIs.createPolylineROI(second, poly.getImagePlane());
        PathObject obj1 = PathObjects.createAnnotationObject(roi1, annotation.getPathClass());
        PathObject obj2 = PathObjects.createAnnotationObject(roi2, annotation.getPathClass());
        // Preserve color/name where possible
        if (annotation.getName() != null) {
            obj1.setName(annotation.getName());
            obj2.setName(annotation.getName());
        }
        if (annotation.getColor() != null) {
            obj1.setColor(annotation.getColor());
            obj2.setColor(annotation.getColor());
        }

        PathObjectHierarchy hierarchy = viewer.getHierarchy();
        if (hierarchy == null) {
            return;
        }
        hierarchy.removeObject(annotation, false);
        hierarchy.addObjects(List.of(obj1, obj2));
        hierarchy.fireHierarchyChangedEvent(this);
        viewer.setSelectedObject(obj1);
        viewer.repaintEntireImage();
        PolylineWandLogging.LOG.info("Polyline Wand: cut polyline into {} + {} vertices",
                first.size(), second.size());
    }

    private void updateCursorOverlay(MouseEvent e) {
        if (overlay == null) {
            return;
        }
        Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, false);
        double r = effectiveImageRadius(PolylineWandParameters.getBrushRadius());
        overlay.updateCursor(p.getX(), p.getY(), r, PolylineWandParameters.getBrushMode());
    }

    private double effectiveImageRadius(double prefRadius) {
        // Brush radius interpretation depends on radiusFollowsZoom:
        //   true  -> screen pixels: image-space radius = prefRadius * downsample,
        //            so the on-screen size stays constant and zoom-out covers a
        //            larger region of the image (matches QuPath's built-in brush
        //            with brushScaleByMag = true).
        //   false -> image pixels: image-space radius = prefRadius regardless of
        //            zoom (the on-screen circle grows when you zoom in).
        if (!PolylineWandParameters.getRadiusFollowsZoom()) {
            return Math.max(1.0, prefRadius);
        }
        double ds = viewer == null ? 1.0 : viewer.getDownsampleFactor();
        return Math.max(1.0, prefRadius * ds);
    }
}
