package qupath.ext.polylinewand.engine.proxy;

import org.locationtech.jts.geom.Coordinate;

/**
 * A point on the original polyline that should be spliced back onto the
 * skeleton output, compensating for Zhang-Suen's 1-2 pixel endpoint erosion.
 */
public record EndpointAnchor(Coordinate point, double anchorRadiusPx) {}
