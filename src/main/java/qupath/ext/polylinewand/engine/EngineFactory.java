package qupath.ext.polylinewand.engine;

import qupath.ext.polylinewand.EngineKind;
import qupath.ext.polylinewand.engine.direct.DirectVertexEngine;
import qupath.ext.polylinewand.engine.field.DisplacementFieldEngine;

/**
 * Produces a fresh {@link BrushEngine} for the requested kind. One engine
 * instance per stroke -- engines are not expected to outlive the stroke.
 */
public final class EngineFactory {

    private EngineFactory() {}

    public static BrushEngine create(EngineKind kind) {
        switch (kind) {
            case DISPLACEMENT_FIELD:
                return new DisplacementFieldEngine();
            case DIRECT_VERTEX:
            default:
                return new DirectVertexEngine();
        }
    }
}
