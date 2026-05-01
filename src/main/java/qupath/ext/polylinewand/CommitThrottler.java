package qupath.ext.polylinewand;

import javafx.animation.AnimationTimer;
import qupath.ext.polylinewand.engine.BrushEngine;
import qupath.lib.geom.Point2;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;

/**
 * Throttled mid-drag commits of the engine's working polyline back to the
 * authoritative {@code PathObject.setROI(...)} + hierarchy event.
 * <p>
 * Owns one {@link AnimationTimer}. Runs on the JavaFX pulse and decimates
 * to the user-configured commit interval. Mid-drag commits use
 * {@code isChanging=true} so they fold into a single undo entry; the
 * authoritative final commit (called explicitly by the event handler on
 * {@code mouseReleased}) uses {@code isChanging=false}.
 */
public final class CommitThrottler {

    private final QuPathViewer viewer;
    private final PathObject annotation;
    private final ImagePlane plane;
    private final BrushEngine engine;
    private final LocalRegion region;
    private final long minIntervalNs;

    private long lastCommitNs = 0L;
    private AnimationTimer timer;

    public CommitThrottler(QuPathViewer viewer, PathObject annotation, ImagePlane plane,
                           BrushEngine engine, LocalRegion region, int minIntervalMs) {
        this.viewer = viewer;
        this.annotation = annotation;
        this.plane = plane;
        this.engine = engine;
        this.region = region;
        this.minIntervalNs = Math.max(1L, (long) minIntervalMs) * 1_000_000L;
    }

    public void start() {
        if (timer != null) {
            return;
        }
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastCommitNs < minIntervalNs) {
                    return;
                }
                if (!engine.isDirty()) {
                    return;
                }
                commitMidStroke();
                lastCommitNs = now;
            }
        };
        timer.start();
        lastCommitNs = System.nanoTime();
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void commitMidStroke() {
        List<Point2> bodySnapshot = engine.previewSnapshot();
        if (bodySnapshot == null) {
            return;
        }
        // Splice the engine's current body into the locked head + tail so the
        // mid-drag setROI shows the complete polyline.
        List<Point2> spliced = region.splice(bodySnapshot);
        if (spliced.size() < 2) {
            return;
        }
        ROI newRoi = ROIs.createPolylineROI(spliced, plane);
        if (annotation instanceof PathROIObject roiObj) {
            roiObj.setROI(newRoi);
        }
        engine.markClean();
        PathObjectHierarchy hierarchy = viewer.getHierarchy();
        if (hierarchy != null) {
            hierarchy.fireObjectsChangedEvent(this, List.of(annotation), true);
        }
        viewer.repaintEntireImage();
    }
}
