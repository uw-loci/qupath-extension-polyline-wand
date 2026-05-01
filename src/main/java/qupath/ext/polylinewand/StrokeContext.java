package qupath.ext.polylinewand;

import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImagePlane;

/**
 * Per-stroke read-mostly bag of objects an engine needs.
 * <p>
 * Created on {@code mousePressed} and discarded on {@code mouseReleased}.
 */
public final class StrokeContext {

    private final QuPathViewer viewer;
    private final PathObject annotation;
    private final ImagePlane plane;
    private final BrushMode mode;
    private final double brushRadius;
    private final StrokeVelocityTracker velocity;
    private final LocalRegion region;

    public StrokeContext(QuPathViewer viewer, PathObject annotation, ImagePlane plane,
                         BrushMode mode, double brushRadius, StrokeVelocityTracker velocity,
                         LocalRegion region) {
        this.viewer = viewer;
        this.annotation = annotation;
        this.plane = plane;
        this.mode = mode;
        this.brushRadius = brushRadius;
        this.velocity = velocity;
        this.region = region;
    }

    public QuPathViewer getViewer() { return viewer; }
    public PathObject getAnnotation() { return annotation; }
    public ImagePlane getPlane() { return plane; }
    public BrushMode getMode() { return mode; }
    public double getBrushRadius() { return brushRadius; }
    public StrokeVelocityTracker getVelocity() { return velocity; }

    /** Locked head/body/tail split for this stroke. Engines edit body only. */
    public LocalRegion getRegion() { return region; }
}
