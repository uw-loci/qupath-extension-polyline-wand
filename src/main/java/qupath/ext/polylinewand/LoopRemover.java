package qupath.ext.polylinewand;

import qupath.lib.geom.Point2;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects self-intersections in an open polyline and splices out the loops.
 * <p>
 * When non-adjacent segments [i, i+1] and [j, j+1] intersect at point P
 * (with i+1 < j), the polyline is rewritten as
 * {@code points[0..i] ++ P ++ points[j+1..end]}. This collapses the loop
 * the user created by pushing one part of the line over another.
 * <p>
 * The pass is iterated until no intersections remain (or a safety cap is hit).
 */
public final class LoopRemover {

    private static final int MAX_PASSES = 32;

    private LoopRemover() {}

    public static List<Point2> untangle(List<Point2> points) {
        if (points == null || points.size() < 4) {
            return points == null ? List.of() : new ArrayList<>(points);
        }
        ArrayList<Point2> work = new ArrayList<>(points);
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            int[] hit = findFirstIntersection(work);
            if (hit == null) {
                break;
            }
            int i = hit[0];
            int j = hit[1];
            Point2 p = computeIntersection(work.get(i), work.get(i + 1),
                    work.get(j), work.get(j + 1));
            if (p == null) {
                break;
            }
            // Replace points (i+1 .. j) with the single intersection point.
            ArrayList<Point2> replaced = new ArrayList<>(work.size() - (j - i) + 1);
            for (int k = 0; k <= i; k++) replaced.add(work.get(k));
            replaced.add(p);
            for (int k = j + 1; k < work.size(); k++) replaced.add(work.get(k));
            work = replaced;
            if (work.size() < 4) {
                break;
            }
        }
        return work;
    }

    private static int[] findFirstIntersection(List<Point2> pts) {
        int n = pts.size();
        for (int i = 0; i < n - 1; i++) {
            Point2 a = pts.get(i);
            Point2 b = pts.get(i + 1);
            // Skip the immediate neighbor segment to avoid touching at the shared vertex.
            for (int j = i + 2; j < n - 1; j++) {
                Point2 c = pts.get(j);
                Point2 d = pts.get(j + 1);
                if (segmentsIntersect(a, b, c, d)) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }

    private static boolean segmentsIntersect(Point2 p1, Point2 p2, Point2 p3, Point2 p4) {
        double d1 = direction(p3, p4, p1);
        double d2 = direction(p3, p4, p2);
        double d3 = direction(p1, p2, p3);
        double d4 = direction(p1, p2, p4);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double direction(Point2 a, Point2 b, Point2 c) {
        return (c.getX() - a.getX()) * (b.getY() - a.getY())
             - (b.getX() - a.getX()) * (c.getY() - a.getY());
    }

    private static Point2 computeIntersection(Point2 p1, Point2 p2, Point2 p3, Point2 p4) {
        double x1 = p1.getX(), y1 = p1.getY();
        double x2 = p2.getX(), y2 = p2.getY();
        double x3 = p3.getX(), y3 = p3.getY();
        double x4 = p4.getX(), y4 = p4.getY();
        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(denom) < 1e-12) {
            return null;
        }
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        return new Point2(x1 + t * (x2 - x1), y1 + t * (y2 - y1));
    }
}
