package qupath.ext.polylinewand.engine.direct;

import qupath.lib.geom.Point2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Uniform-grid spatial index over a polyline's vertex array.
 * <p>
 * Cell size is set to the brush radius at index-build time. Range queries
 * scan a 3x3 (or 5x5 if r > 1.5*cell) cell neighborhood around the query
 * point and return vertex indices whose distance to the query is &le; r.
 */
public final class UniformGridIndex {

    private final double cellSize;
    private final Map<Long, int[]> cells;
    private final List<Point2> points;

    public UniformGridIndex(List<Point2> points, double cellSize) {
        this.points = points;
        this.cellSize = Math.max(1.0, cellSize);
        Map<Long, List<Integer>> tmp = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            Point2 p = points.get(i);
            long key = cellKey(p.getX(), p.getY());
            tmp.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        cells = new HashMap<>(tmp.size());
        for (Map.Entry<Long, List<Integer>> e : tmp.entrySet()) {
            List<Integer> bucket = e.getValue();
            int[] arr = new int[bucket.size()];
            for (int i = 0; i < bucket.size(); i++) {
                arr[i] = bucket.get(i);
            }
            cells.put(e.getKey(), arr);
        }
    }

    private long cellKey(double x, double y) {
        long ix = (long) Math.floor(x / cellSize);
        long iy = (long) Math.floor(y / cellSize);
        return (ix << 32) ^ (iy & 0xffffffffL);
    }

    private long packCell(long ix, long iy) {
        return (ix << 32) ^ (iy & 0xffffffffL);
    }

    public void query(double cx, double cy, double r, IntConsumer hit) {
        long ix = (long) Math.floor(cx / cellSize);
        long iy = (long) Math.floor(cy / cellSize);
        int span = r > cellSize * 1.5 ? 2 : 1;
        double r2 = r * r;
        for (long dx = -span; dx <= span; dx++) {
            for (long dy = -span; dy <= span; dy++) {
                int[] bucket = cells.get(packCell(ix + dx, iy + dy));
                if (bucket == null) {
                    continue;
                }
                for (int idx : bucket) {
                    Point2 p = points.get(idx);
                    double ex = p.getX() - cx;
                    double ey = p.getY() - cy;
                    if (ex * ex + ey * ey <= r2) {
                        hit.accept(idx);
                    }
                }
            }
        }
    }
}
