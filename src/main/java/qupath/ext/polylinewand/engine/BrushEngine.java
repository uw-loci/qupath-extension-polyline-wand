package qupath.ext.polylinewand.engine;

import qupath.ext.polylinewand.StrokeContext;
import qupath.lib.geom.Point2;

import java.util.List;

/**
 * Common contract for the three brush engines that ship inside the
 * Polyline Wand and Brush extension. The event handler owns one engine
 * per stroke; engines are instantiated by {@link EngineFactory}.
 */
public interface BrushEngine {

    /**
     * Called once on {@code mousePressed}. The engine seeds its working
     * copy from the current annotation's polyline points.
     */
    void beginStroke(StrokeContext ctx, Point2 imgPt);

    /**
     * Called per {@code mouseDragged} frame. The engine should update its
     * internal working copy and mark itself dirty.
     */
    void applyDrag(StrokeContext ctx, Point2 imgPt, long nowNs);

    /**
     * Called once on {@code mouseReleased}. Returns the final list of
     * points ready to feed {@code ROIs.createPolylineROI(...)}.
     * <p>
     * Must always return at least 2 points; never null.
     */
    List<Point2> endStroke(StrokeContext ctx);

    /**
     * Called when the stroke is abandoned mid-drag (tool deregistered,
     * Esc pressed, viewer changed). Engine must release any background
     * resources but is not expected to commit anything.
     */
    void cancel(StrokeContext ctx);

    /**
     * Read-only snapshot of the current working polyline, for the overlay
     * to paint between throttled commits. Must be safe to call from the
     * JavaFX paint thread; engines should publish this via a volatile or
     * AtomicReference.
     */
    List<Point2> previewSnapshot();

    /**
     * Whether the engine has changed since the last commit. Used by the
     * throttler to skip no-op commits.
     */
    boolean isDirty();

    /**
     * Marks the engine as clean. Called by the throttler after a commit.
     */
    void markClean();
}
