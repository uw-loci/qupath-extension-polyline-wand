package qupath.ext.polylinewand;

import qupath.lib.geom.Point2;

import java.util.List;

/**
 * Closest point on a polyline to a query point.
 *
 * @param segment index of the segment (from vertex {@code segment} to {@code segment + 1})
 * @param t       position along that segment in [0, 1]
 * @param point   the projected point in image coordinates
 * @param distSq  squared distance from the query point to {@code point}
 */
public record PolylineProjection(int segment, double t, Point2 point, double distSq) {

    /**
     * Project {@code query} onto the nearest segment of {@code pts}.
     *
     * @param pts   polyline vertices, at least two
     * @param query point to project
     * @return the projection, or {@code null} if the polyline has fewer than two vertices
     */
    public static PolylineProjection nearest(List<Point2> pts, Point2 query) {
        if (pts == null || pts.size() < 2) {
            return null;
        }
        int bestSeg = -1;
        double bestDistSq = Double.MAX_VALUE;
        double bestT = 0.0;
        double bestX = 0.0;
        double bestY = 0.0;
        for (int i = 0; i < pts.size() - 1; i++) {
            Point2 a = pts.get(i);
            Point2 b = pts.get(i + 1);
            double abx = b.getX() - a.getX();
            double aby = b.getY() - a.getY();
            double abLen2 = abx * abx + aby * aby;
            double t = abLen2 < 1e-12 ? 0.0
                    : ((query.getX() - a.getX()) * abx
                     + (query.getY() - a.getY()) * aby) / abLen2;
            t = Math.max(0.0, Math.min(1.0, t));
            double px = a.getX() + t * abx;
            double py = a.getY() + t * aby;
            double dx = px - query.getX();
            double dy = py - query.getY();
            double d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestSeg = i;
                bestT = t;
                bestX = px;
                bestY = py;
            }
        }
        if (bestSeg < 0) {
            return null;
        }
        return new PolylineProjection(bestSeg, bestT, new Point2(bestX, bestY), bestDistSq);
    }
}
