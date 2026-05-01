package qupath.ext.polylinewand;

import qupath.lib.geom.Point2;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes adjacent polyline vertices that fall within {@code minSpacing} of
 * each other. Used by the brush engines to prevent vertex pile-up when the
 * user pushes one part of the polyline against another, and as an end-of-
 * stroke pass to keep vertex density bounded.
 */
public final class PolylineCompactor {

    private PolylineCompactor() {}

    /**
     * Walk the polyline and drop the second vertex of any adjacent pair
     * whose distance is below {@code minSpacing}. Always preserves the first
     * and last vertex.
     */
    public static List<Point2> compact(List<Point2> points, double minSpacing) {
        if (points == null || points.size() < 3 || minSpacing <= 0.0) {
            return points == null ? List.of() : new ArrayList<>(points);
        }
        double minSq = minSpacing * minSpacing;
        ArrayList<Point2> out = new ArrayList<>(points.size());
        out.add(points.get(0));
        for (int i = 1; i < points.size() - 1; i++) {
            Point2 last = out.get(out.size() - 1);
            Point2 cand = points.get(i);
            double dx = cand.getX() - last.getX();
            double dy = cand.getY() - last.getY();
            if (dx * dx + dy * dy < minSq) {
                continue;
            }
            out.add(cand);
        }
        // Always keep the last vertex; drop the previous one if it crowds it.
        Point2 last = points.get(points.size() - 1);
        Point2 prev = out.get(out.size() - 1);
        double dx = last.getX() - prev.getX();
        double dy = last.getY() - prev.getY();
        if (out.size() > 1 && dx * dx + dy * dy < minSq) {
            out.set(out.size() - 1, last);
        } else {
            out.add(last);
        }
        return out;
    }

    /**
     * In-place compaction restricted to a sub-range of a {@link WorkingPolyline}.
     * Collapses runs of adjacent vertices closer than {@code minSpacing}, leaving
     * the boundary vertices untouched.
     */
    public static int compactRangeInPlace(WorkingPolyline working, int from, int to, double minSpacing) {
        if (working.size() < 3 || minSpacing <= 0.0 || from < 0 || to >= working.size() || to - from < 2) {
            return 0;
        }
        double minSq = minSpacing * minSpacing;
        int removed = 0;
        // Preserve start and end of the sub-range.
        int i = from + 1;
        while (i < to - removed) {
            Point2 prev = working.get(i - 1);
            Point2 cur = working.get(i);
            double dx = cur.getX() - prev.getX();
            double dy = cur.getY() - prev.getY();
            if (dx * dx + dy * dy < minSq) {
                working.removeRange(i, i);
                removed++;
            } else {
                i++;
            }
        }
        return removed;
    }
}
