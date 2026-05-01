package qupath.ext.polylinewand;

import qupath.lib.geom.Point2;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;

/**
 * Tests whether a brush position is "near an endpoint" of a polyline ROI.
 * Used to decide whether to enter ERASE_FROM_END mode at stroke start.
 */
public final class EndpointDetector {

    private EndpointDetector() {}

    public static EndpointSide hitTest(ROI roi, Point2 cursor, double thresholdImagePx) {
        if (roi == null) {
            return EndpointSide.NONE;
        }
        Point2 first;
        Point2 last;
        if (roi instanceof LineROI line) {
            first = new Point2(line.getX1(), line.getY1());
            last = new Point2(line.getX2(), line.getY2());
        } else if (roi instanceof PolylineROI poly) {
            List<Point2> pts = poly.getAllPoints();
            if (pts.isEmpty()) {
                return EndpointSide.NONE;
            }
            first = pts.get(0);
            last = pts.get(pts.size() - 1);
        } else {
            return EndpointSide.NONE;
        }
        double dStart = distance(first, cursor);
        double dEnd = distance(last, cursor);
        double t = Math.max(0.0, thresholdImagePx);
        if (dStart <= t && dStart <= dEnd) {
            return EndpointSide.START;
        }
        if (dEnd <= t) {
            return EndpointSide.END;
        }
        return EndpointSide.NONE;
    }

    private static double distance(Point2 a, Point2 b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return Math.hypot(dx, dy);
    }
}
