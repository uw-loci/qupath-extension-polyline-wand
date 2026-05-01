package qupath.ext.polylinewand;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.simplify.VWSimplifier;
import qupath.lib.geom.Point2;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps JTS Visvalingam-Whyatt simplification, applied to a sub-list of
 * polyline points. Endpoints of the sub-list are preserved (VW preserves
 * the first and last coordinate by construction).
 */
public final class StrokeSimplifier {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private StrokeSimplifier() {}

    /**
     * Simplify the given point sub-list. The first and last vertices are preserved.
     * Tolerance 0 (or fewer than 4 points) returns the input unchanged.
     */
    public static List<Point2> simplifyRange(List<Point2> points, double tolerance) {
        if (points == null || points.size() < 4 || tolerance <= 0.0) {
            return points == null ? List.of() : new ArrayList<>(points);
        }
        Coordinate[] coords = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Point2 p = points.get(i);
            coords[i] = new Coordinate(p.getX(), p.getY());
        }
        LineString line = FACTORY.createLineString(coords);
        Geometry simplified = VWSimplifier.simplify(line, tolerance);
        Coordinate[] outCoords = simplified.getCoordinates();
        List<Point2> out = new ArrayList<>(outCoords.length);
        for (Coordinate c : outCoords) {
            out.add(new Point2(c.x, c.y));
        }
        // Defensive: VW should preserve endpoints, but on degenerate input it can collapse.
        if (out.size() < 2) {
            return new ArrayList<>(points);
        }
        return out;
    }
}
