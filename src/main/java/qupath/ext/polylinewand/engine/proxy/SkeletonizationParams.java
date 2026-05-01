package qupath.ext.polylinewand.engine.proxy;

/**
 * Parameters controlling the raster + thinning skeletonization pipeline.
 */
public record SkeletonizationParams(
        int targetMaskMaxDimensionPx,
        double padPx,
        boolean closeBeforeThinning,
        double simplifyTolerance,
        DisconnectionPolicy disconnectionPolicy,
        boolean preserveEndpoints) {}
