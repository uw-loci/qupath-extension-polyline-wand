package qupath.ext.polylinewand;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.ROIs;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a {@link LineROI} into a densified {@link PolylineROI} so
 * brush engines have interior vertices to work with.
 */
public final class LineRoiPromoter {

    private LineRoiPromoter() {}

    /**
     * Build a polyline densified to {@code totalVertexCount} evenly spaced
     * vertices (including both endpoints). Minimum is 2 (returns the bare endpoints).
     */
    public static PolylineROI promote(LineROI line, int totalVertexCount) {
        int n = Math.max(2, totalVertexCount);
        ImagePlane plane = line.getImagePlane();
        double x1 = line.getX1();
        double y1 = line.getY1();
        double x2 = line.getX2();
        double y2 = line.getY2();
        List<Point2> pts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            pts.add(new Point2(x1 + t * (x2 - x1), y1 + t * (y2 - y1)));
        }
        return ROIs.createPolylineROI(pts, plane);
    }
}
