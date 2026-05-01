package qupath.ext.polylinewand;

import qupath.lib.geom.Point2;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable working copy of a polyline's vertex list, used by engines that
 * displace individual vertices in place (engines A and C).
 * <p>
 * Tracks a "dirty range" (first/last touched index since last clear) and a
 * monotonic topology version that downstream spatial indexes key off.
 */
public final class WorkingPolyline {

    private final ArrayList<Point2> points;
    private int firstDirty = -1;
    private int lastDirty = -1;
    private int topologyVersion = 0;

    public WorkingPolyline(List<Point2> initial) {
        // Defensive copy -- PolylineROI.getAllPoints() is unmodifiable.
        this.points = new ArrayList<>(initial);
    }

    public int size() { return points.size(); }
    public Point2 get(int i) { return points.get(i); }

    public void setPoint(int i, Point2 p) {
        points.set(i, p);
        markDirty(i, i);
    }

    public void insertPoint(int i, Point2 p) {
        points.add(i, p);
        markDirty(i, i);
        topologyVersion++;
    }

    public void removeRange(int fromInclusive, int toInclusive) {
        if (fromInclusive < 0 || toInclusive >= points.size() || fromInclusive > toInclusive) {
            return;
        }
        for (int i = toInclusive; i >= fromInclusive; i--) {
            points.remove(i);
        }
        markDirty(Math.max(0, fromInclusive - 1), Math.min(points.size() - 1, fromInclusive));
        topologyVersion++;
    }

    public void markDirty(int from, int to) {
        if (firstDirty < 0 || from < firstDirty) firstDirty = from;
        if (to > lastDirty) lastDirty = to;
    }

    public boolean hasDirty() { return firstDirty >= 0; }
    public int getFirstDirty() { return firstDirty; }
    public int getLastDirty() { return lastDirty; }

    public void clearDirty() {
        firstDirty = -1;
        lastDirty = -1;
    }

    public int getTopologyVersion() { return topologyVersion; }

    /**
     * Snapshot of the current point list (defensive copy).
     */
    public List<Point2> snapshot() {
        return new ArrayList<>(points);
    }

    /**
     * Direct read-only view (NOT a copy). Callers must not mutate the returned list.
     * Useful for inner loops that need to avoid allocation.
     */
    public List<Point2> view() {
        return points;
    }
}
