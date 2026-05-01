package qupath.ext.polylinewand;

import qupath.lib.geom.Point2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits a polyline into an immutable head, an editable body, and an
 * immutable tail. Computed once at {@code mousePressed} based on the
 * brush position; locked for the duration of the stroke.
 * <p>
 * This is the mechanism that lets all three engines edit only a local
 * region of a long polyline. Vertices outside the body are guaranteed
 * unchanged at commit -- they are spliced back in bit-exact.
 */
public final class LocalRegion {

    /** Inclusive index of the first body vertex in the original polyline. */
    public final int bodyStart;
    /** Inclusive index of the last body vertex in the original polyline. */
    public final int bodyEnd;
    public final List<Point2> head;
    public final List<Point2> body;
    public final List<Point2> tail;

    private LocalRegion(int bodyStart, int bodyEnd,
                        List<Point2> head, List<Point2> body, List<Point2> tail) {
        this.bodyStart = bodyStart;
        this.bodyEnd = bodyEnd;
        this.head = head;
        this.body = body;
        this.tail = tail;
    }

    /**
     * Find the contiguous index range whose vertices are within
     * {@code maxDistance} of {@code brushImg}, then expand by one vertex
     * on each side to give engines a tangent-continuous boundary at the
     * splice. If the brush is entirely outside the polyline's bounding
     * neighborhood, returns a small region around the closest vertex.
     */
    public static LocalRegion extract(List<Point2> all, Point2 brushImg, double maxDistance) {
        if (all == null || all.isEmpty()) {
            return new LocalRegion(0, -1, Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());
        }
        int n = all.size();
        double maxSq = maxDistance * maxDistance;
        int closest = 0;
        double bestSq = Double.MAX_VALUE;
        // First pass: find the closest vertex (anchor for the body).
        for (int i = 0; i < n; i++) {
            double dx = all.get(i).getX() - brushImg.getX();
            double dy = all.get(i).getY() - brushImg.getY();
            double d2 = dx * dx + dy * dy;
            if (d2 < bestSq) {
                bestSq = d2;
                closest = i;
            }
        }
        // Walk left from the closest vertex while the next one is in range.
        int start = closest;
        while (start > 0) {
            int candidate = start - 1;
            double dx = all.get(candidate).getX() - brushImg.getX();
            double dy = all.get(candidate).getY() - brushImg.getY();
            if (dx * dx + dy * dy > maxSq) {
                break;
            }
            start = candidate;
        }
        // Walk right.
        int end = closest;
        while (end < n - 1) {
            int candidate = end + 1;
            double dx = all.get(candidate).getX() - brushImg.getX();
            double dy = all.get(candidate).getY() - brushImg.getY();
            if (dx * dx + dy * dy > maxSq) {
                break;
            }
            end = candidate;
        }
        // Expand by one vertex on each side for tangent continuity at the splice.
        if (start > 0) start--;
        if (end < n - 1) end++;
        // Body must have at least 2 vertices for downstream code; expand if needed.
        while (end - start < 1 && (start > 0 || end < n - 1)) {
            if (start > 0) start--;
            else if (end < n - 1) end++;
        }

        List<Point2> head = start == 0 ? Collections.emptyList()
                : new ArrayList<>(all.subList(0, start));
        List<Point2> body = new ArrayList<>(all.subList(start, end + 1));
        List<Point2> tail = end == n - 1 ? Collections.emptyList()
                : new ArrayList<>(all.subList(end + 1, n));
        return new LocalRegion(start, end, head, body, tail);
    }

    /**
     * Splice an edited body back together with the immutable head and tail.
     * Returns a fresh list. If {@code body} starts/ends with a vertex that
     * exactly matches the boundary, no duplicate insertion occurs.
     */
    public List<Point2> splice(List<Point2> editedBody) {
        if (editedBody == null || editedBody.isEmpty()) {
            // Body collapsed -- fall back to head + tail (joined directly).
            ArrayList<Point2> out = new ArrayList<>(head.size() + tail.size());
            out.addAll(head);
            out.addAll(tail);
            return out;
        }
        ArrayList<Point2> out = new ArrayList<>(head.size() + editedBody.size() + tail.size());
        out.addAll(head);
        out.addAll(editedBody);
        out.addAll(tail);
        return out;
    }

    public boolean isWholePolyline() {
        return head.isEmpty() && tail.isEmpty();
    }

    public int totalSize() {
        return head.size() + body.size() + tail.size();
    }
}
