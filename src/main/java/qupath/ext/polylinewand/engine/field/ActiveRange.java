package qupath.ext.polylinewand.engine.field;

import qupath.lib.geom.Point2;

/**
 * Locked editable index window into a {@link WorkingCurve}. Computed at
 * stroke start; expanded only by densification (which inserts vertices
 * inside the original arc-length bounds).
 */
public final class ActiveRange {

    public int startIdx;
    public int endIdx;
    public final double sLo;
    public final double sHi;
    public final boolean leftEndpointActive;
    public final boolean rightEndpointActive;

    ActiveRange(int startIdx, int endIdx, double sLo, double sHi,
                boolean leftEndpointActive, boolean rightEndpointActive) {
        this.startIdx = startIdx;
        this.endIdx = endIdx;
        this.sLo = sLo;
        this.sHi = sHi;
        this.leftEndpointActive = leftEndpointActive;
        this.rightEndpointActive = rightEndpointActive;
    }

    /**
     * Find the closest vertex to {@code brushImagePt}, then walk left/right
     * collecting vertices until cumulative arc-length distance exceeds
     * {@code arcLengthHalfRange}.
     */
    /**
     * Build an ActiveRange that spans the entire curve. Used when the
     * working curve is rebuilt mid-stroke (e.g. after erase).
     */
    public static ActiveRange wholeCurve(WorkingCurve wc) {
        int n = wc.size();
        if (n == 0) {
            return new ActiveRange(0, -1, 0, 0, false, false);
        }
        return new ActiveRange(0, n - 1, wc.arcLengthAt(0), wc.arcLengthAt(n - 1),
                true, true);
    }

    public static ActiveRange compute(WorkingCurve wc, Point2 brushImagePt,
                                      double arcLengthHalfRange) {
        int n = wc.size();
        if (n == 0) {
            return new ActiveRange(0, -1, 0, 0, false, false);
        }
        // Closest vertex
        int closest = 0;
        double bestSq = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Point2 p = wc.get(i);
            double dx = p.getX() - brushImagePt.getX();
            double dy = p.getY() - brushImagePt.getY();
            double d2 = dx * dx + dy * dy;
            if (d2 < bestSq) {
                bestSq = d2;
                closest = i;
            }
        }
        double s0 = wc.arcLengthAt(closest);
        int start = closest;
        while (start > 0 && (s0 - wc.arcLengthAt(start)) < arcLengthHalfRange) {
            start--;
        }
        int end = closest;
        while (end < n - 1 && (wc.arcLengthAt(end) - s0) < arcLengthHalfRange) {
            end++;
        }
        return new ActiveRange(start, end,
                wc.arcLengthAt(start), wc.arcLengthAt(end),
                start == 0, end == n - 1);
    }
}
