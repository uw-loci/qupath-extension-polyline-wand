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
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Custom overlay drawing the brush-radius cursor at the current cursor
 * position. Engine-owned working-polyline preview is painted by the
 * regular hierarchy overlay (we throttle setROI to keep that view fresh).
 */
public final class PolylineWandOverlay extends AbstractOverlay implements PathOverlay {

    private final QuPathViewer viewer;
    private volatile double cursorImageX = Double.NaN;
    private volatile double cursorImageY = Double.NaN;
    private volatile double cursorImageRadius = 0.0;
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
        viewer.repaintEntireImage();
    }

    public void hideCursor() {
        this.visible.set(false);
        viewer.repaintEntireImage();
    }

    @Override
    public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor,
                             ImageData<BufferedImage> imageData, boolean paintCompletely) {
        if (!visible.get() || Double.isNaN(cursorImageX) || cursorImageRadius <= 0.0) {
            return;
        }
        double r = cursorImageRadius;
        Ellipse2D.Double ellipse = new Ellipse2D.Double(
                cursorImageX - r, cursorImageY - r, r * 2.0, r * 2.0);
        Color outlineColor = resolveOutlineColor(currentMode);
        Object oldAa = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g2d.getStroke();
        Color oldColor = g2d.getColor();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float strokeWidth = (float) Math.max(1.0, downsampleFactor);
            g2d.setStroke(new BasicStroke(strokeWidth));
            g2d.setColor(outlineColor);
            g2d.draw(ellipse);
        } finally {
            g2d.setColor(oldColor);
            g2d.setStroke(oldStroke);
            if (oldAa != null) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
            }
        }
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
