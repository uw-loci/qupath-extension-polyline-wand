package qupath.ext.polylinewand;

import qupath.lib.common.ColorTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.regions.ImageRegion;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Custom overlay drawing the brush-radius cursor at the current cursor
 * position. Engine-owned working-polyline preview is painted by the
 * regular hierarchy overlay (we throttle setROI to keep that view fresh).
 */
public final class PolylineWandOverlay extends AbstractOverlay implements PathOverlay {

    private static final double CURSOR_STROKE_SCREEN_PX = 1.5;
    private static final double CURSOR_DASH_SCREEN_PX = 5.0;
    // Scissors mode: crosshair at the cursor, ring on the polyline where the cut lands.
    private static final double CROSSHAIR_ARM_SCREEN_PX = 9.0;
    private static final double CROSSHAIR_GAP_SCREEN_PX = 3.0;
    private static final double CUT_MARK_RADIUS_SCREEN_PX = 5.0;
    private static final double CUT_CONNECTOR_MIN_SCREEN_PX = 8.0;

    private final QuPathViewer viewer;
    private volatile double cursorImageX = Double.NaN;
    private volatile double cursorImageY = Double.NaN;
    private volatile double cursorImageRadius = 0.0;
    private volatile double cutImageX = Double.NaN;
    private volatile double cutImageY = Double.NaN;
    private volatile BrushMode currentMode = BrushMode.AUTO;
    private final AtomicBoolean visible = new AtomicBoolean(false);

    public PolylineWandOverlay(QuPathViewer viewer) {
        super(viewer.getOverlayOptions());
        this.viewer = viewer;
    }

    public void updateCursor(double imageX, double imageY, double imageRadius, BrushMode mode) {
        this.cursorImageX = imageX;
        this.cursorImageY = imageY;
        this.cursorImageRadius = imageRadius;
        this.currentMode = mode;
        this.visible.set(true);
        viewer.repaint();
    }

    /**
     * Show the scissors cursor: a crosshair at the pointer and, when a
     * polyline is selected, a mark at the point on it that a click would cut.
     *
     * @param imageX pointer x in image coordinates
     * @param imageY pointer y in image coordinates
     * @param cutX   cut point x, or NaN when nothing cuttable is selected
     * @param cutY   cut point y, or NaN when nothing cuttable is selected
     */
    public void updateCutCursor(double imageX, double imageY, double cutX, double cutY) {
        this.cursorImageX = imageX;
        this.cursorImageY = imageY;
        this.cutImageX = cutX;
        this.cutImageY = cutY;
        this.currentMode = BrushMode.CUT_AT_POINT;
        this.visible.set(true);
        viewer.repaint();
    }

    public void hideCursor() {
        this.visible.set(false);
        viewer.repaint();
    }

    @Override
    public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor,
                             ImageData<BufferedImage> imageData, boolean paintCompletely) {
        if (!visible.get() || Double.isNaN(cursorImageX)) {
            return;
        }
        if (currentMode == BrushMode.CUT_AT_POINT) {
            paintScissorsCursor(g2d, downsampleFactor);
            return;
        }
        if (cursorImageRadius <= 0.0) {
            return;
        }
        // The brush physically reaches to cursorImageRadius, but for engines using
        // a tapered falloff (cosine / gaussian) the effect is small near the edge.
        // Draw the SOLID cursor at the effective-scale radius so it visually matches
        // the felt push area; draw a faint dashed outer at the true maximum reach.
        double rOuter = cursorImageRadius;
        double scale = clamp(PolylineWandParameters.getCursorEffectiveScale(), 0.25, 1.0);
        double rInner = rOuter * scale;
        // Erase mode uses a hard radius -- no taper, so skip the inner-vs-outer
        // split (the full circle IS the effect).
        boolean hardEdge = currentMode == BrushMode.ERASE_FROM_END;

        Color outlineColor = resolveOutlineColor(currentMode);
        Object oldAa = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g2d.getStroke();
        Color oldColor = g2d.getColor();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Graphics are in image space; scale by downsample for a constant on-screen width.
            float strokeWidth = (float) (CURSOR_STROKE_SCREEN_PX * downsampleFactor);
            g2d.setColor(outlineColor);
            if (hardEdge) {
                g2d.setStroke(new BasicStroke(strokeWidth));
                g2d.draw(ellipseAt(cursorImageX, cursorImageY, rOuter));
            } else {
                // Solid inner: where the brush has significant strength.
                g2d.setStroke(new BasicStroke(strokeWidth));
                g2d.draw(ellipseAt(cursorImageX, cursorImageY, rInner));
                // Dashed outer: maximum reach (effect tapers to ~0 here).
                float dash = (float) (CURSOR_DASH_SCREEN_PX * downsampleFactor);
                g2d.setStroke(new BasicStroke(strokeWidth * 0.75f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10f, new float[]{dash, dash}, 0f));
                g2d.setColor(new Color(outlineColor.getRed(), outlineColor.getGreen(),
                        outlineColor.getBlue(), 140));
                g2d.draw(ellipseAt(cursorImageX, cursorImageY, rOuter));
            }
        } finally {
            g2d.setColor(oldColor);
            g2d.setStroke(oldStroke);
            if (oldAa != null) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
            }
        }
    }

    /**
     * Scissors mode has no radius: the click is projected onto the nearest
     * point of the selected polyline. Draw a small crosshair at the pointer,
     * a ring where the cut will land, and a faint connector between them when
     * they are visibly apart.
     */
    private void paintScissorsCursor(Graphics2D g2d, double downsampleFactor) {
        Color outlineColor = resolveOutlineColor(currentMode);
        Object oldAa = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g2d.getStroke();
        Color oldColor = g2d.getColor();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float strokeWidth = (float) (CURSOR_STROKE_SCREEN_PX * downsampleFactor);
            double arm = CROSSHAIR_ARM_SCREEN_PX * downsampleFactor;
            double gap = CROSSHAIR_GAP_SCREEN_PX * downsampleFactor;
            double cx = cursorImageX;
            double cy = cursorImageY;

            g2d.setColor(outlineColor);
            g2d.setStroke(new BasicStroke(strokeWidth));
            g2d.draw(new Line2D.Double(cx - arm, cy, cx - gap, cy));
            g2d.draw(new Line2D.Double(cx + gap, cy, cx + arm, cy));
            g2d.draw(new Line2D.Double(cx, cy - arm, cx, cy - gap));
            g2d.draw(new Line2D.Double(cx, cy + gap, cx, cy + arm));

            if (Double.isNaN(cutImageX) || Double.isNaN(cutImageY)) {
                return;
            }
            double rMark = CUT_MARK_RADIUS_SCREEN_PX * downsampleFactor;
            double dx = cutImageX - cx;
            double dy = cutImageY - cy;
            double distScreen = Math.hypot(dx, dy) / downsampleFactor;
            if (distScreen > CUT_CONNECTOR_MIN_SCREEN_PX) {
                float dash = (float) (CURSOR_DASH_SCREEN_PX * downsampleFactor);
                g2d.setStroke(new BasicStroke(strokeWidth * 0.75f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10f, new float[]{dash, dash}, 0f));
                g2d.setColor(new Color(outlineColor.getRed(), outlineColor.getGreen(),
                        outlineColor.getBlue(), 140));
                g2d.draw(new Line2D.Double(cx, cy, cutImageX, cutImageY));
            }
            g2d.setColor(outlineColor);
            g2d.setStroke(new BasicStroke(strokeWidth));
            g2d.draw(ellipseAt(cutImageX, cutImageY, rMark));
            g2d.fill(ellipseAt(cutImageX, cutImageY, rMark * 0.35));
        } finally {
            g2d.setColor(oldColor);
            g2d.setStroke(oldStroke);
            if (oldAa != null) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
            }
        }
    }

    private static Ellipse2D.Double ellipseAt(double cx, double cy, double r) {
        return new Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0);
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static Color resolveOutlineColor(BrushMode mode) {
        if (mode == BrushMode.ERASE_FROM_END) {
            return Color.RED;
        }
        CursorOutlineColor pref = PolylineWandParameters.getCursorOutlineColor();
        switch (pref) {
            case RED:   return Color.RED;
            case WHITE: return Color.WHITE;
            case BLACK: return Color.BLACK;
            case THEME:
            default:
                Integer rgb = PathPrefs.colorDefaultObjectsProperty().get();
                if (rgb == null) {
                    return Color.WHITE;
                }
                return new Color(ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb));
        }
    }
}
