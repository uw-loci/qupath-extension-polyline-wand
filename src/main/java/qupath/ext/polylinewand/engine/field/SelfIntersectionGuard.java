package qupath.ext.polylinewand.engine.field;

import qupath.lib.geom.Point2;

/**
 * Local segment-segment intersection check restricted to the active
 * range. Refuses any per-vertex move that would produce a crossing with
 * another segment in the same range (or with the immediate boundary
 * segments touching the immutable head/tail).
 */
public final class SelfIntersectionGuard {

    private SelfIntersectionGuard() {}

    /**
     * Returns true if moving vertex {@code idx} to {@code candidate} would
     * cause either segment incident to {@code idx} to intersect any other
     * segment in {@code [start..end]} (excluding immediate neighbors).
     */
    public static boolean wouldSelfIntersect(WorkingCurve wc, int start, int end,
                                             int idx, Point2 candidate) {
        int n = wc.size();
        if (idx <= 0 || idx >= n - 1) {
            return false;
        }
        Point2 left = wc.get(idx - 1);
        Point2 right = wc.get(idx + 1);
        // The two "candidate" segments
        // (left, candidate) and (candidate, right)
        int boundStart = Math.max(0, start - 1);
        int boundEnd = Math.min(n - 2, end);
        for (int i = boundStart; i <= boundEnd; i++) {
            // Skip neighboring segments
            if (i == idx - 1 || i == idx) {
                continue;
            }
            Point2 a = wc.get(i);
            Point2 b = wc.get(i + 1);
            if (segmentsIntersect(left, candidate, a, b) || segmentsIntersect(candidate, right, a, b)) {
                return true;
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(Point2 p1, Point2 p2, Point2 p3, Point2 p4) {
        double d1 = direction(p3, p4, p1);
        double d2 = direction(p3, p4, p2);
        double d3 = direction(p1, p2, p3);
        double d4 = direction(p1, p2, p4);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        return false;
    }

    private static double direction(Point2 a, Point2 b, Point2 c) {
        return (c.getX() - a.getX()) * (b.getY() - a.getY())
             - (b.getX() - a.getX()) * (c.getY() - a.getY());
    }
}
