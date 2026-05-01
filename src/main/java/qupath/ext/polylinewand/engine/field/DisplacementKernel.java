package qupath.ext.polylinewand.engine.field;

/**
 * Per-vertex displacement weight kernels. Returns 0 for r >= radius.
 */
public final class DisplacementKernel {

    private DisplacementKernel() {}

    public static double dispatch(KernelType type, double r, double radius, double sigmaFraction) {
        if (radius <= 0 || r >= radius) {
            return 0.0;
        }
        switch (type) {
            case LINEAR:
                return 1.0 - r / radius;
            case GAUSSIAN: {
                double sigma = Math.max(0.05, sigmaFraction);
                double t = r / radius;
                return Math.exp(-(t * t) / (2.0 * sigma * sigma));
            }
            case COSINE:
            default:
                return 0.5 * (1.0 + Math.cos(Math.PI * r / radius));
        }
    }
}
