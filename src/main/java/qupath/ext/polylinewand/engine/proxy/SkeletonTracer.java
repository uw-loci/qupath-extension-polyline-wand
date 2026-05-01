package qupath.ext.polylinewand.engine.proxy;

import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks a 1-pixel-wide binary skeleton mask into ordered coordinate lists.
 * <p>
 * Strategy: find an endpoint pixel (foreground pixel with &le; 1 foreground
 * 8-neighbor), then walk neighbor-to-neighbor, marking visited. If no
 * endpoint exists (closed loop), break the loop arbitrarily.
 */
public final class SkeletonTracer {

    private SkeletonTracer() {}

    /**
     * Trace all connected components in a binary mask. Each component
     * becomes one coordinate list (in raster pixel coordinates).
     */
    public static List<List<int[]>> traceComponents(byte[] mask, int width, int height) {
        boolean[] visited = new boolean[width * height];
        List<List<int[]>> components = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (visited[idx] || (mask[idx] & 0xff) == 0) {
                    continue;
                }
                // Find an endpoint pixel in this component first via flood fill.
                int[] endpoint = findEndpointInComponent(mask, visited, width, height, x, y);
                List<int[]> trace;
                if (endpoint != null) {
                    // Reset visited flags for this component, then walk from the endpoint.
                    clearComponentVisited(mask, visited, width, height, x, y);
                    trace = walk(mask, visited, width, height, endpoint[0], endpoint[1]);
                } else {
                    // Closed loop: walk from this seed pixel.
                    trace = walk(mask, visited, width, height, x, y);
                }
                if (!trace.isEmpty()) {
                    components.add(trace);
                }
            }
        }
        return components;
    }

    private static int[] findEndpointInComponent(byte[] mask, boolean[] visited,
                                                 int width, int height, int sx, int sy) {
        // BFS over the component to find an endpoint (degree <= 1).
        int[] queueX = new int[width * height];
        int[] queueY = new int[width * height];
        int head = 0, tail = 0;
        queueX[tail] = sx;
        queueY[tail] = sy;
        tail++;
        boolean[] seen = new boolean[width * height];
        seen[sy * width + sx] = true;
        int[] endpoint = null;
        while (head < tail) {
            int cx = queueX[head];
            int cy = queueY[head];
            head++;
            int degree = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    int nidx = ny * width + nx;
                    if ((mask[nidx] & 0xff) == 0) continue;
                    degree++;
                    if (!seen[nidx]) {
                        seen[nidx] = true;
                        queueX[tail] = nx;
                        queueY[tail] = ny;
                        tail++;
                    }
                }
            }
            if (degree <= 1 && endpoint == null) {
                endpoint = new int[]{cx, cy};
            }
        }
        // Mark this component as touched in the *visited* array so the outer
        // loop skips it on subsequent iterations.
        for (int i = 0; i < width * height; i++) {
            if (seen[i]) {
                visited[i] = true;
            }
        }
        return endpoint;
    }

    private static void clearComponentVisited(byte[] mask, boolean[] visited,
                                              int width, int height, int sx, int sy) {
        // Walk the component and clear visited so the next walk can re-traverse.
        int[] queueX = new int[width * height];
        int[] queueY = new int[width * height];
        int head = 0, tail = 0;
        queueX[tail] = sx;
        queueY[tail] = sy;
        tail++;
        visited[sy * width + sx] = false;
        while (head < tail) {
            int cx = queueX[head];
            int cy = queueY[head];
            head++;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    int nidx = ny * width + nx;
                    if ((mask[nidx] & 0xff) == 0) continue;
                    if (!visited[nidx]) continue;
                    visited[nidx] = false;
                    queueX[tail] = nx;
                    queueY[tail] = ny;
                    tail++;
                }
            }
        }
    }

    private static List<int[]> walk(byte[] mask, boolean[] visited,
                                    int width, int height, int sx, int sy) {
        List<int[]> path = new ArrayList<>();
        int cx = sx, cy = sy;
        path.add(new int[]{cx, cy});
        visited[cy * width + cx] = true;
        // Greedy walk: at each step, prefer a neighbor that is foreground and unvisited;
        // when there are multiple choices, prefer 4-neighbors over diagonals.
        while (true) {
            int[] next = chooseNextPixel(mask, visited, width, height, cx, cy);
            if (next == null) {
                break;
            }
            cx = next[0];
            cy = next[1];
            path.add(new int[]{cx, cy});
            visited[cy * width + cx] = true;
        }
        return path;
    }

    private static int[] chooseNextPixel(byte[] mask, boolean[] visited,
                                         int width, int height, int cx, int cy) {
        // 4-neighbors first, diagonals second.
        int[][] cand4 = {{1,0},{0,1},{-1,0},{0,-1}};
        int[][] candD = {{1,1},{1,-1},{-1,1},{-1,-1}};
        int[] hit = tryNeighbors(mask, visited, width, height, cx, cy, cand4);
        if (hit != null) {
            return hit;
        }
        return tryNeighbors(mask, visited, width, height, cx, cy, candD);
    }

    private static int[] tryNeighbors(byte[] mask, boolean[] visited,
                                      int width, int height, int cx, int cy, int[][] cands) {
        for (int[] d : cands) {
            int nx = cx + d[0];
            int ny = cy + d[1];
            if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
            int nidx = ny * width + nx;
            if ((mask[nidx] & 0xff) != 0 && !visited[nidx]) {
                return new int[]{nx, ny};
            }
        }
        return null;
    }

    /**
     * Convert a traced pixel-coordinate path into image-coordinate JTS coordinates
     * via {@code (x, y) -> (x * dsRaster + originX, y * dsRaster + originY)}.
     */
    public static Coordinate[] toImageCoords(List<int[]> path, double originX, double originY,
                                             double dsRaster) {
        Coordinate[] out = new Coordinate[path.size()];
        for (int i = 0; i < path.size(); i++) {
            int[] p = path.get(i);
            out[i] = new Coordinate(originX + p[0] * dsRaster, originY + p[1] * dsRaster);
        }
        return out;
    }
}
