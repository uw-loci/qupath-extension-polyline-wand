package qupath.ext.polylinewand.engine.direct;

import qupath.lib.geom.Point2;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.viewer.QuPathViewer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Pixel-similarity field used to modulate the direct vertex push.
 * <p>
 * On (re)build, paints a square patch centered on the brush via the viewer's
 * region store at the viewer's current downsample, applies a light box blur,
 * captures the seed color at the patch center, and computes a per-channel
 * standard deviation. The standard deviation scales the per-channel tolerance
 * so the same sensitivity value behaves consistently across high- and
 * low-contrast tissue regions -- matching the shape of QuPath's built-in
 * wand sensitivity.
 * <p>
 * Per-vertex weight: {@code 1 - clamp(maxChanDist / threshold, 0, 1)}, where
 * {@code threshold = stddev * sensitivity}. Vertices on tissue similar to the
 * seed get full displacement; vertices on dissimilar pixels get no push.
 */
public final class PixelSensitivityField {

    private static final int PATCH_SIZE = 149;
    private static final int BLUR_PASSES = 1;

    private final QuPathViewer viewer;
    private final double downsample;
    private final int z;
    private final int t;

    private final double sensitivity;

    private double patchOriginX;
    private double patchOriginY;
    private double patchSpanImage;
    private byte[] r;
    private byte[] g;
    private byte[] b;
    private double seedR;
    private double seedG;
    private double seedB;
    private double thresholdR;
    private double thresholdG;
    private double thresholdB;

    public PixelSensitivityField(QuPathViewer viewer, double sensitivity) {
        this.viewer = viewer;
        this.downsample = Math.max(1.0, viewer == null ? 1.0 : viewer.getDownsampleFactor());
        this.z = viewer == null ? 0 : viewer.getZPosition();
        this.t = viewer == null ? 0 : viewer.getTPosition();
        this.sensitivity = Math.max(0.01, sensitivity);
    }

    /** Build (or rebuild) the patch around {@code center}. */
    public void rebuildAt(Point2 center) {
        if (viewer == null) {
            r = g = b = null;
            return;
        }
        DefaultImageRegionStore store = viewer.getImageRegionStore();
        if (store == null || viewer.getServer() == null) {
            r = g = b = null;
            return;
        }
        patchSpanImage = PATCH_SIZE * downsample;
        patchOriginX = Math.round(center.getX() - patchSpanImage * 0.5);
        patchOriginY = Math.round(center.getY() - patchSpanImage * 0.5);

        BufferedImage img = new BufferedImage(PATCH_SIZE, PATCH_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, PATCH_SIZE, PATCH_SIZE);
            Rectangle2D bounds = new Rectangle2D.Double(
                    patchOriginX, patchOriginY, patchSpanImage, patchSpanImage);
            g2d.scale(1.0 / downsample, 1.0 / downsample);
            g2d.translate(-patchOriginX, -patchOriginY);
            store.paintRegion(viewer.getServer(), g2d, bounds, z, t, downsample, null, null,
                    viewer.getImageDisplay());
        } finally {
            g2d.dispose();
        }
        extractChannels(img);
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            r = blur3x3(r);
            g = blur3x3(g);
            b = blur3x3(b);
        }
        captureSeedAndStddev();
    }

    private void extractChannels(BufferedImage img) {
        int n = PATCH_SIZE * PATCH_SIZE;
        if (r == null || r.length != n) {
            r = new byte[n];
            g = new byte[n];
            b = new byte[n];
        }
        int[] rgb = img.getRGB(0, 0, PATCH_SIZE, PATCH_SIZE, null, 0, PATCH_SIZE);
        for (int i = 0; i < n; i++) {
            int v = rgb[i];
            r[i] = (byte) ((v >> 16) & 0xFF);
            g[i] = (byte) ((v >> 8) & 0xFF);
            b[i] = (byte) (v & 0xFF);
        }
    }

    private static byte[] blur3x3(byte[] src) {
        byte[] dst = new byte[src.length];
        int w = PATCH_SIZE;
        for (int y = 0; y < w; y++) {
            int y0 = Math.max(0, y - 1);
            int y1 = Math.min(w - 1, y + 1);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - 1);
                int x1 = Math.min(w - 1, x + 1);
                int sum = 0;
                sum += src[y0 * w + x0] & 0xFF;
                sum += src[y0 * w + x] & 0xFF;
                sum += src[y0 * w + x1] & 0xFF;
                sum += src[y * w + x0] & 0xFF;
                sum += src[y * w + x] & 0xFF;
                sum += src[y * w + x1] & 0xFF;
                sum += src[y1 * w + x0] & 0xFF;
                sum += src[y1 * w + x] & 0xFF;
                sum += src[y1 * w + x1] & 0xFF;
                dst[y * w + x] = (byte) (sum / 9);
            }
        }
        return dst;
    }

    private void captureSeedAndStddev() {
        int n = PATCH_SIZE * PATCH_SIZE;
        int center = (PATCH_SIZE / 2) * PATCH_SIZE + (PATCH_SIZE / 2);
        seedR = r[center] & 0xFF;
        seedG = g[center] & 0xFF;
        seedB = b[center] & 0xFF;

        double sumR = 0, sumG = 0, sumB = 0;
        double sumR2 = 0, sumG2 = 0, sumB2 = 0;
        for (int i = 0; i < n; i++) {
            int rv = r[i] & 0xFF;
            int gv = g[i] & 0xFF;
            int bv = b[i] & 0xFF;
            sumR += rv; sumG += gv; sumB += bv;
            sumR2 += rv * rv; sumG2 += gv * gv; sumB2 += bv * bv;
        }
        double meanR = sumR / n;
        double meanG = sumG / n;
        double meanB = sumB / n;
        double varR = Math.max(0.0, sumR2 / n - meanR * meanR);
        double varG = Math.max(0.0, sumG2 / n - meanG * meanG);
        double varB = Math.max(0.0, sumB2 / n - meanB * meanB);
        // Floor stddev so flat regions don't divide by zero (matches the
        // built-in wand's behavior when local variance collapses to 0).
        double sR = Math.max(2.0, Math.sqrt(varR));
        double sG = Math.max(2.0, Math.sqrt(varG));
        double sB = Math.max(2.0, Math.sqrt(varB));
        thresholdR = sR * sensitivity;
        thresholdG = sG * sensitivity;
        thresholdB = sB * sensitivity;
    }

    /** True if the given image-coordinate point is outside the cached patch. */
    public boolean isOutsidePatch(double imgX, double imgY) {
        if (r == null) return true;
        double margin = patchSpanImage * 0.05;
        return imgX < patchOriginX + margin
                || imgX >= patchOriginX + patchSpanImage - margin
                || imgY < patchOriginY + margin
                || imgY >= patchOriginY + patchSpanImage - margin;
    }

    /**
     * Per-vertex pixel-similarity weight in [0, 1].
     * 1 = same color as seed; 0 = beyond the threshold on any channel.
     */
    public double weightAt(double imgX, double imgY) {
        if (r == null) return 1.0;
        int px = (int) Math.floor((imgX - patchOriginX) / downsample);
        int py = (int) Math.floor((imgY - patchOriginY) / downsample);
        if (px < 0 || px >= PATCH_SIZE || py < 0 || py >= PATCH_SIZE) {
            return 0.0;
        }
        int idx = py * PATCH_SIZE + px;
        double dr = Math.abs((r[idx] & 0xFF) - seedR);
        double dg = Math.abs((g[idx] & 0xFF) - seedG);
        double db = Math.abs((b[idx] & 0xFF) - seedB);
        double wr = 1.0 - clamp(dr / thresholdR, 0.0, 1.0);
        double wg = 1.0 - clamp(dg / thresholdG, 0.0, 1.0);
        double wb = 1.0 - clamp(db / thresholdB, 0.0, 1.0);
        // Take the minimum so any one channel exceeding threshold stops the push.
        return Math.min(wr, Math.min(wg, wb));
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
