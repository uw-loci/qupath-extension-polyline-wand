package qupath.ext.polylinewand.engine.direct;

/**
 * Per-vertex displacement weight as a function of normalized radius r/R in [0, 1].
 * Returns 0 at r >= R for all profiles so callers do not need a clamp.
 */
public enum FalloffProfile {

    /** w = 1 - r/R. Sharp inner peak, sharp outer edge. */
    LINEAR,

    /** w = 0.5 * (1 + cos(pi * r / R)). Zero slope at both ends. Default. */
    COSINE,

    /** w = exp(-(r/R)^2 / (2 * sigma^2)) with sigma = 1/3, truncated at R. */
    GAUSSIAN;

    public double weight(double r, double radius) {
        if (r >= radius || radius <= 0) {
            return 0.0;
        }
        double t = r / radius;
        switch (this) {
            case LINEAR:
                return 1.0 - t;
            case COSINE:
                return 0.5 * (1.0 + Math.cos(Math.PI * t));
            case GAUSSIAN:
                double sigma = 1.0 / 3.0;
                return Math.exp(-(t * t) / (2.0 * sigma * sigma));
            default:
                return 0.0;
        }
    }
}
