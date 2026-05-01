package qupath.ext.polylinewand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared SLF4J logger and ASCII-only formatting helpers.
 * <p>
 * QuPath's runtime is Windows + cp1252 -- non-ASCII characters in log
 * messages have hung workflows in this project before, so all logging
 * goes through this class.
 */
public final class PolylineWandLogging {

    public static final Logger LOG = LoggerFactory.getLogger("qupath.ext.polylinewand");

    private PolylineWandLogging() {}
}
