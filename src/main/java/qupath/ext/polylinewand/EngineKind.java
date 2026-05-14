package qupath.ext.polylinewand;

/**
 * Selectable brush engines. Switched at runtime via the right-click
 * context menu or the preference pane.
 */
public enum EngineKind {

    /** Direct vertex displacement with a uniform-grid spatial index. */
    DIRECT_VERTEX("Direct vertex push"),

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
