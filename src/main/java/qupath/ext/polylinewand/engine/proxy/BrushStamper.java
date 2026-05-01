package qupath.ext.polylinewand.engine.proxy;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.util.GeometricShapeFactory;

/**
 * Builds the per-mouse-move JTS geometry stamp the area proxy unions into
 * its working area.
 */
public final class BrushStamper {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private BrushStamper() {}

    public static Geometry stampForFirstPoint(double x, double y, double diameterImagePx) {
        GeometricShapeFactory gsf = new GeometricShapeFactory(FACTORY);
        gsf.setCentre(new Coordinate(x, y));
        gsf.setSize(diameterImagePx);
        gsf.setNumPoints(24);
        return gsf.createCircle();
    }

    public static Geometry stampForSegment(double fromX, double fromY,
                                           double toX, double toY,
                                           double diameterImagePx) {
        Coordinate[] coords = {new Coordinate(fromX, fromY), new Coordinate(toX, toY)};
        return FACTORY.createLineString(coords)
                .buffer(diameterImagePx / 2.0,
                        BufferParameters.DEFAULT_QUADRANT_SEGMENTS,
                        BufferParameters.CAP_ROUND);
    }
}
