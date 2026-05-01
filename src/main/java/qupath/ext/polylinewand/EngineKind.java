package qupath.ext.polylinewand;

/**
 * Selectable brush engines. Switched at runtime via the right-click
 * context menu or the preference pane.
 */
public enum EngineKind {

    /** Direct vertex displacement with a uniform-grid spatial index. */
    DIRECT_VERTEX("Direct vertex push"),

    /** Buffer to a thin area, brush as area, skeletonize back. */
    AREA_PROXY("Area proxy + skeletonize"),

    /** Arc-length parameterized displacement field with a locked active window. */
    DISPLACEMENT_FIELD("Displacement field");

    private final String displayName;

    EngineKind(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
