package qupath.ext.polylinewand.engine.proxy;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_ximgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.simplify.VWSimplifier;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

/**
 * Rasterize a JTS area geometry to a binary mask, run OpenCV
 * {@code ximgproc.thinning(THINNING_ZHANGSUEN)}, trace the skeleton back
 * into image-coordinate {@link LineString}s.
 */
public final class RasterSkeletonizer {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private RasterSkeletonizer() {}

    public static Geometry skeletonize(Geometry proxyArea,
                                       List<EndpointAnchor> endpointAnchors,
                                       SkeletonizationParams params) {
        if (proxyArea == null || proxyArea.isEmpty()) {
            return null;
        }
        Envelope env = proxyArea.getEnvelopeInternal();
        double pad = params.padPx();
        double minX = env.getMinX() - pad;
        double minY = env.getMinY() - pad;
        double maxX = env.getMaxX() + pad;
        double maxY = env.getMaxY() + pad;
        double width = maxX - minX;
        double height = maxY - minY;
        if (width <= 0 || height <= 0) {
            return null;
        }
        double envMaxDim = Math.max(width, height);
        double dsRaster = 1.0;
        if (params.targetMaskMaxDimensionPx() > 0
                && envMaxDim > params.targetMaskMaxDimensionPx()) {
            dsRaster = envMaxDim / params.targetMaskMaxDimensionPx();
        }
        int rasterW = Math.max(1, (int) Math.ceil(width / dsRaster));
        int rasterH = Math.max(1, (int) Math.ceil(height / dsRaster));

        // Rasterize via Java2D into a TYPE_BYTE_GRAY image.
        BufferedImage img = new BufferedImage(rasterW, rasterH, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = img.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, rasterW, rasterH);
            g2d.setColor(Color.WHITE);
            AffineTransform tx = new AffineTransform();
            tx.scale(1.0 / dsRaster, 1.0 / dsRaster);
            tx.translate(-minX, -minY);
            g2d.setTransform(tx);
            ShapeWriter sw = new ShapeWriter();
            Shape shape = sw.toShape(proxyArea);
            g2d.fill(shape);
        } finally {
            g2d.dispose();
        }

        byte[] bytes = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();

        try (Mat mat = new Mat(rasterH, rasterW, opencv_core.CV_8UC1)) {
            mat.data().put(bytes);

            if (params.closeBeforeThinning()) {
                try (Mat kernel = opencv_imgproc.getStructuringElement(
                        opencv_imgproc.MORPH_RECT, new Size(3, 3))) {
                    opencv_imgproc.morphologyEx(mat, mat, opencv_imgproc.MORPH_CLOSE, kernel);
                }
            }

            try (Mat skeleton = new Mat(rasterH, rasterW, opencv_core.CV_8UC1, new Scalar(0))) {
                opencv_ximgproc.thinning(mat, skeleton, opencv_ximgproc.THINNING_ZHANGSUEN);
                byte[] outBytes = new byte[rasterW * rasterH];
                skeleton.data().get(outBytes);

                List<List<int[]>> components = SkeletonTracer.traceComponents(outBytes, rasterW, rasterH);
                if (components.isEmpty()) {
                    return null;
                }
                // Apply disconnection policy
                List<List<int[]>> selected = applyDisconnectionPolicy(components,
                        params.disconnectionPolicy());
                List<LineString> lines = new ArrayList<>(selected.size());
                for (List<int[]> path : selected) {
                    if (path.size() < 2) continue;
                    Coordinate[] coords = SkeletonTracer.toImageCoords(path, minX, minY, dsRaster);
                    if (params.preserveEndpoints() && endpointAnchors != null && !endpointAnchors.isEmpty()) {
                        coords = anchorEndpoints(coords, endpointAnchors, dsRaster);
                    }
                    if (params.simplifyTolerance() > 0) {
                        LineString ls = FACTORY.createLineString(coords);
                        Geometry simp = VWSimplifier.simplify(ls, params.simplifyTolerance());
                        coords = simp.getCoordinates();
                    }
                    if (coords.length >= 2) {
                        lines.add(FACTORY.createLineString(coords));
                    }
                }
                if (lines.isEmpty()) return null;
                if (lines.size() == 1) return lines.get(0);
                return new MultiLineString(lines.toArray(new LineString[0]), FACTORY);
            }
        }
    }

    private static List<List<int[]>> applyDisconnectionPolicy(List<List<int[]>> components,
                                                              DisconnectionPolicy policy) {
        if (components.size() <= 1) {
            return components;
        }
        switch (policy) {
            case REJECT_EDIT:
                // Caller checks for null/empty and reverts; we still return the longest so
                // the caller has something to compare against if it wants to.
            case KEEP_ALL_FALLBACK:
            case KEEP_LONGEST:
            default: {
                int bestIdx = 0;
                int bestLen = -1;
                for (int i = 0; i < components.size(); i++) {
                    int len = components.get(i).size();
                    if (len > bestLen) {
                        bestLen = len;
                        bestIdx = i;
                    }
                }
                List<List<int[]>> out = new ArrayList<>(1);
                out.add(components.get(bestIdx));
                return out;
            }
        }
    }

    /**
     * If either endpoint of the traced line is within {@code 2 * dsRaster}
     * of an anchor, replace it with the anchor's exact image-space coordinate.
     */
    private static Coordinate[] anchorEndpoints(Coordinate[] coords,
                                                List<EndpointAnchor> anchors,
                                                double dsRaster) {
        if (coords.length < 2) {
            return coords;
        }
        double snap = Math.max(2.0 * dsRaster, 1.0);
        Coordinate[] out = coords.clone();
        for (EndpointAnchor a : anchors) {
            double dStart = out[0].distance(a.point());
            double dEnd = out[out.length - 1].distance(a.point());
            if (dStart <= snap && dStart <= dEnd) {
                out[0] = new Coordinate(a.point().x, a.point().y);
            } else if (dEnd <= snap) {
                out[out.length - 1] = new Coordinate(a.point().x, a.point().y);
            }
        }
        return out;
    }
}
