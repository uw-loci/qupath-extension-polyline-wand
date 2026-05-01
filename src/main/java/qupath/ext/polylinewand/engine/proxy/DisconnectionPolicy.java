package qupath.ext.polylinewand.engine.proxy;

/**
 * What to do when skeletonization produces more than one connected component
 * (the brush split the proxy area).
 */
public enum DisconnectionPolicy {

    /** Keep the longest traced component, drop the others. */
    KEEP_LONGEST,

    /** Revert the entire stroke; show a notification. */
    REJECT_EDIT,

    /** Reserved for future MultiPolylineROI support; currently falls back to KEEP_LONGEST. */
    KEEP_ALL_FALLBACK
}
