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
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

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
        // Mouse-wheel adjusts brush radius; modifier keys leave wheel for zoom.
        if (e.isControlDown() || e.isShiftDown() || e.isAltDown()) {
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

        StrokeVelocityTracker velocity = new StrokeVelocityTracker();
        velocity.reset(imgPt, System.nanoTime());

        targetAnnotation = selected;
        targetPlane = roi.getImagePlane();
        ctx = new StrokeContext(viewer, targetAnnotation, targetPlane, mode,
                brushRadiusForStroke, velocity);
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
                PolylineWandParameters.getCommitThrottleMs());
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
        if (overlay != null) {
            overlay.hideCursor();
        }
    }

    private void commitFinal() {
        List<Point2> finalPoints;
        try {
            finalPoints = engine.endStroke(ctx);
        } catch (RuntimeException ex) {
            PolylineWandLogging.LOG.warn("Polyline Wand engine endStroke failed: {}", ex.getMessage(), ex);
            return;
        }
        if (finalPoints == null || finalPoints.size() < 2) {
            PolylineWandLogging.LOG.debug("Polyline Wand: engine returned <2 points; skipping final commit");
            return;
        }
        // End-of-stroke cleanup pipeline (shared across all engines):
        //   1. Compact crowded vertices (vertex pile-up)
        //   2. Remove self-intersection loops (yarn-ball cleanup)
        //   3. Visvalingam-Whyatt simplification
        double minSpacing = Math.max(0.5, brushRadiusForStroke * 0.05);
        finalPoints = PolylineCompactor.compact(finalPoints, minSpacing);
        finalPoints = LoopRemover.untangle(finalPoints);
        double tol = PolylineWandParameters.getEndStrokeSimplifyTolerance();
        if (tol > 0.0) {
            finalPoints = StrokeSimplifier.simplifyRange(finalPoints, tol);
        }
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

    private void updateCursorOverlay(MouseEvent e) {
        if (overlay == null) {
            return;
        }
        Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, false);
        double r = effectiveImageRadius(PolylineWandParameters.getBrushRadius());
        overlay.updateCursor(p.getX(), p.getY(), r, PolylineWandParameters.getBrushMode());
    }

    private double effectiveImageRadius(double prefRadius) {
        // Brush radius is interpreted as SCREEN pixels. Image-space radius scales
        // with the current downsample so zoom-out automatically makes the brush
        // affect a larger region of the image. On-screen size stays constant.
        double ds = viewer == null ? 1.0 : viewer.getDownsampleFactor();
        return Math.max(1.0, prefRadius * ds);
    }
}
