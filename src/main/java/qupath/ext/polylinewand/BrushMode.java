package qupath.ext.polylinewand;

/**
 * Brush behavior shared across all engines.
 * <p>
 * AUTO: push, but auto-enter ERASE_FROM_END when the stroke begins near a polyline endpoint.
 * PUSH: push only.
 * SMOOTH: smooth/average affected vertices.
 * ERASE_FROM_END: shorten from whichever endpoint is closer to the brush.
 */
public enum BrushMode {

    AUTO("Auto (push, erase-near-endpoint)"),
    PUSH("Push"),
    SMOOTH("Smooth"),
    ERASE_FROM_END("Erase from end");

    private final String displayName;

    BrushMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
