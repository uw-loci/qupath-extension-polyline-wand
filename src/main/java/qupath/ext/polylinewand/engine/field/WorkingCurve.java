package qupath.ext.polylinewand.engine.field;

import qupath.lib.geom.Point2;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable polyline + parallel arc-length, tangent and normal arrays.
 * Recomputed by {@link #recomputeFromIndex(int)} after each per-frame edit.
 */
public final class WorkingCurve {

    private final ArrayList<Point2> points;
    private double[] arcLength;
    private double[] tangentX;
    private double[] tangentY;
    private double[] normalX;
    private double[] normalY;

    public WorkingCurve(List<Point2> initial) {
        this.points = new ArrayList<>(initial);
        ensureCapacity(this.points.size());
        recomputeFromIndex(0);
    }

    public int size() {
        return points.size();
    }

    public Point2 get(int i) {
        return points.get(i);
    }

    public void setPoint(int i, Point2 p) {
        points.set(i, p);
    }

    public void insertPoint(int i, Point2 p) {
        points.add(i, p);
        ensureCapacity(points.size());
    }

    public List<Point2> snapshot() {
        return new ArrayList<>(points);
    }

    public List<Point2> view() {
        return points;
    }

    public double arcLengthAt(int i) {
        return arcLength[i];
    }

    public double normalX(int i) { return normalX[i]; }
    public double normalY(int i) { return normalY[i]; }
    public double tangentX(int i) { return tangentX[i]; }
    public double tangentY(int i) { return tangentY[i]; }

    private void ensureCapacity(int n) {
        if (arcLength == null || arcLength.length < n) {
            arcLength = new double[Math.max(n, 16)];
            tangentX = new double[Math.max(n, 16)];
            tangentY = new double[Math.max(n, 16)];
            normalX = new double[Math.max(n, 16)];
            normalY = new double[Math.max(n, 16)];
        }
    }

    /**
     * Recompute arcLength / tangent / normal from {@code from} to the end.
     * O(N - from).
     */
    public void recomputeFromIndex(int from) {
        int n = points.size();
        ensureCapacity(n);
        if (from < 0) from = 0;
        if (from >= n) return;
        if (from == 0) {
            arcLength[0] = 0.0;
            from = 1;
        }
        for (int i = from; i < n; i++) {
            Point2 prev = points.get(i - 1);
            Point2 cur = points.get(i);
            double dx = cur.getX() - prev.getX();
            double dy = cur.getY() - prev.getY();
            double seg = Math.hypot(dx, dy);
            arcLength[i] = arcLength[i - 1] + seg;
        }
        // Tangent / normal at each vertex: average of incoming and outgoing segment directions.
        for (int i = 0; i < n; i++) {
            double tx = 0, ty = 0;
            if (i > 0) {
                double dx = points.get(i).getX() - points.get(i - 1).getX();
                double dy = points.get(i).getY() - points.get(i - 1).getY();
                double len = Math.hypot(dx, dy);
                if (len > 1e-9) {
                    tx += dx / len;
                    ty += dy / len;
                }
            }
            if (i < n - 1) {
                double dx = points.get(i + 1).getX() - points.get(i).getX();
                double dy = points.get(i + 1).getY() - points.get(i).getY();
                double len = Math.hypot(dx, dy);
                if (len > 1e-9) {
                    tx += dx / len;
                    ty += dy / len;
                }
            }
            double mag = Math.hypot(tx, ty);
            if (mag > 1e-9) {
                tx /= mag;
                ty /= mag;
            } else {
                tx = 1; ty = 0;
            }
            tangentX[i] = tx;
            tangentY[i] = ty;
            normalX[i] = -ty;
            normalY[i] = tx;
        }
    }
}
