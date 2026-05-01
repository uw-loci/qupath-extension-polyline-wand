package qupath.ext.polylinewand;

import qupath.lib.geom.Point2;

/**
 * Small ring buffer of recent brush positions + timestamps.
 * Computes a one-Euler-filtered instantaneous velocity in image px / ms.
 */
public final class StrokeVelocityTracker {

    private static final int CAPACITY = 4;

    private final double[] xs = new double[CAPACITY];
    private final double[] ys = new double[CAPACITY];
    private final long[] ts = new long[CAPACITY];
    private int count = 0;
    private int head = 0;

    private double smoothedVx = 0.0;
    private double smoothedVy = 0.0;

    public void reset(Point2 p, long nowNs) {
        count = 0;
        head = 0;
        smoothedVx = 0.0;
        smoothedVy = 0.0;
        push(p, nowNs);
    }

    public void push(Point2 p, long nowNs) {
        xs[head] = p.getX();
        ys[head] = p.getY();
        ts[head] = nowNs;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }
        recomputeSmoothedVelocity();
    }

    private void recomputeSmoothedVelocity() {
        if (count < 2) {
            smoothedVx = 0.0;
            smoothedVy = 0.0;
            return;
        }
        // Most-recent vs oldest-in-buffer
        int newest = (head - 1 + CAPACITY) % CAPACITY;
        int oldest = (head - count + CAPACITY) % CAPACITY;
        double dtMs = (ts[newest] - ts[oldest]) / 1_000_000.0;
        if (dtMs <= 0.0) {
            return;
        }
        double rawVx = (xs[newest] - xs[oldest]) / dtMs;
        double rawVy = (ys[newest] - ys[oldest]) / dtMs;
        // One-Euler smoothing: alpha = 0.5
        smoothedVx = 0.5 * smoothedVx + 0.5 * rawVx;
        smoothedVy = 0.5 * smoothedVy + 0.5 * rawVy;
    }

    public double getVx() { return smoothedVx; }
    public double getVy() { return smoothedVy; }

    public double getSpeed() {
        return Math.hypot(smoothedVx, smoothedVy);
    }

    /**
     * Unit velocity vector. Returns {@code new double[]{0, 0}} when speed is zero.
     */
    public double[] unitDirection() {
        double s = getSpeed();
        if (s <= 1e-9) {
            return new double[]{0.0, 0.0};
        }
        return new double[]{smoothedVx / s, smoothedVy / s};
    }
}
