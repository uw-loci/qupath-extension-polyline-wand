package qupath.ext.polylinewand;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import qupath.ext.polylinewand.engine.direct.FalloffProfile;
import qupath.ext.polylinewand.engine.field.KernelType;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Persistent preferences for the Polyline Wand and Brush.
 * <p>
 * All settings survive QuPath restarts via PathPrefs.
 * Engine-specific keys are namespaced (direct*, field*) so a single
 * Reset call can restore every value to its default.
 */
public final class PolylineWandParameters {

    private PolylineWandParameters() {}

    // ------------------------------------------------------------------
    // Shared (engine-agnostic)
    // ------------------------------------------------------------------

    private static final ObjectProperty<EngineKind> engineKind =
            PathPrefs.createPersistentPreference("polylineWandEngineKind",
                    EngineKind.DIRECT_VERTEX, EngineKind.class);

    private static final ObjectProperty<BrushMode> brushMode =
            PathPrefs.createPersistentPreference("polylineWandBrushMode",
                    BrushMode.AUTO, BrushMode.class);

    private static final DoubleProperty brushRadius =
            PathPrefs.createPersistentPreference("polylineWandBrushRadius", 40.0);

    private static final BooleanProperty radiusFollowsZoom =
            PathPrefs.createPersistentPreference("polylineWandRadiusFollowsZoom", true);

    private static final DoubleProperty cursorEffectiveScale =
            PathPrefs.createPersistentPreference("polylineWandCursorEffectiveScale", 0.75);

    private static final IntegerProperty commitThrottleMs =
            PathPrefs.createPersistentPreference("polylineWandCommitThrottleMs", 33);

    private static final BooleanProperty eraseAtEndpoints =
            PathPrefs.createPersistentPreference("polylineWandEraseAtEndpoints", true);

    private static final DoubleProperty endpointEraseProximity =
            PathPrefs.createPersistentPreference("polylineWandEndpointEraseProximity", 1.0);

    private static final IntegerProperty lineConversionVertexCount =
            PathPrefs.createPersistentPreference("polylineWandLineConversionVertexCount", 32);

    private static final DoubleProperty endStrokeSimplifyTolerance =
            PathPrefs.createPersistentPreference("polylineWandEndStrokeSimplifyTolerance", 0.5);

    private static final ObjectProperty<CursorOutlineColor> cursorOutlineColor =
            PathPrefs.createPersistentPreference("polylineWandCursorOutlineColor",
                    CursorOutlineColor.THEME, CursorOutlineColor.class);

    /**
     * Multiplier on brush radius used to size the locked editable body of
     * the LocalRegion at mousePressed. 0 disables localization (whole
     * polyline is the body, original behavior).
     */
    private static final DoubleProperty localRegionRadiusMultiplier =
            PathPrefs.createPersistentPreference("polylineWandLocalRegionRadiusMultiplier", 3.0);

    // ------------------------------------------------------------------
    // Engine A: Direct vertex push
    // ------------------------------------------------------------------

    private static final ObjectProperty<FalloffProfile> directFalloffProfile =
            PathPrefs.createPersistentPreference("polylineWandDirectFalloff",
                    FalloffProfile.COSINE, FalloffProfile.class);

    private static final DoubleProperty directRadialBias =
            PathPrefs.createPersistentPreference("polylineWandDirectRadialBias", 0.25);

    private static final BooleanProperty directDensifyEnabled =
            PathPrefs.createPersistentPreference("polylineWandDirectDensifyEnabled", true);

    private static final DoubleProperty directDensifySpacingRatio =
            PathPrefs.createPersistentPreference("polylineWandDirectDensifySpacingRatio", 0.25);

    private static final IntegerProperty directMaxInsertionsPerStroke =
            PathPrefs.createPersistentPreference("polylineWandDirectMaxInsertionsPerStroke", 5000);

    private static final DoubleProperty directVelocityDampingStrength =
            PathPrefs.createPersistentPreference("polylineWandDirectVelocityDamping", 0.0);

    private static final BooleanProperty directPixelSensitivityEnabled =
            PathPrefs.createPersistentPreference("polylineWandDirectPixelSensitivityEnabled", false);

    private static final DoubleProperty directPixelSensitivity =
            PathPrefs.createPersistentPreference("polylineWandDirectPixelSensitivity", 2.0);

    // ------------------------------------------------------------------
    // Engine B: Arc-length displacement field
    // ------------------------------------------------------------------

    private static final ObjectProperty<KernelType> fieldKernelType =
            PathPrefs.createPersistentPreference("polylineWandFieldKernelType",
                    KernelType.COSINE, KernelType.class);

    private static final DoubleProperty fieldKernelSigmaFraction =
            PathPrefs.createPersistentPreference("polylineWandFieldKernelSigmaFraction", 0.33);

    private static final DoubleProperty fieldDisplacementStrength =
            PathPrefs.createPersistentPreference("polylineWandFieldDisplacementStrength", 1.0);

    private static final DoubleProperty fieldDensifyDivisor =
            PathPrefs.createPersistentPreference("polylineWandFieldDensifyDivisor", 4.0);

    private static final BooleanProperty fieldUseCatmullRomDensify =
            PathPrefs.createPersistentPreference("polylineWandFieldUseCatmullRomDensify", false);

    private static final BooleanProperty fieldSelfIntersectionGuard =
            PathPrefs.createPersistentPreference("polylineWandFieldSelfIntersectionGuard", true);

    // ------------------------------------------------------------------
    // Property accessors (FX bindings)
    // ------------------------------------------------------------------

    public static ObjectProperty<EngineKind> engineKindProperty() { return engineKind; }
    public static ObjectProperty<BrushMode> brushModeProperty() { return brushMode; }
    public static DoubleProperty brushRadiusProperty() { return brushRadius; }
    public static BooleanProperty radiusFollowsZoomProperty() { return radiusFollowsZoom; }
    public static IntegerProperty commitThrottleMsProperty() { return commitThrottleMs; }
    public static BooleanProperty eraseAtEndpointsProperty() { return eraseAtEndpoints; }
    public static DoubleProperty endpointEraseProximityProperty() { return endpointEraseProximity; }
    public static IntegerProperty lineConversionVertexCountProperty() { return lineConversionVertexCount; }
    public static DoubleProperty endStrokeSimplifyToleranceProperty() { return endStrokeSimplifyTolerance; }
    public static ObjectProperty<CursorOutlineColor> cursorOutlineColorProperty() { return cursorOutlineColor; }
    public static DoubleProperty localRegionRadiusMultiplierProperty() { return localRegionRadiusMultiplier; }
    public static DoubleProperty cursorEffectiveScaleProperty() { return cursorEffectiveScale; }

    public static ObjectProperty<FalloffProfile> directFalloffProfileProperty() { return directFalloffProfile; }
    public static DoubleProperty directRadialBiasProperty() { return directRadialBias; }
    public static BooleanProperty directDensifyEnabledProperty() { return directDensifyEnabled; }
    public static DoubleProperty directDensifySpacingRatioProperty() { return directDensifySpacingRatio; }
    public static IntegerProperty directMaxInsertionsPerStrokeProperty() { return directMaxInsertionsPerStroke; }
    public static DoubleProperty directVelocityDampingStrengthProperty() { return directVelocityDampingStrength; }
    public static BooleanProperty directPixelSensitivityEnabledProperty() { return directPixelSensitivityEnabled; }
    public static DoubleProperty directPixelSensitivityProperty() { return directPixelSensitivity; }

    public static ObjectProperty<KernelType> fieldKernelTypeProperty() { return fieldKernelType; }
    public static DoubleProperty fieldKernelSigmaFractionProperty() { return fieldKernelSigmaFraction; }
    public static DoubleProperty fieldDisplacementStrengthProperty() { return fieldDisplacementStrength; }
    public static DoubleProperty fieldDensifyDivisorProperty() { return fieldDensifyDivisor; }
    public static BooleanProperty fieldUseCatmullRomDensifyProperty() { return fieldUseCatmullRomDensify; }
    public static BooleanProperty fieldSelfIntersectionGuardProperty() { return fieldSelfIntersectionGuard; }

    // ------------------------------------------------------------------
    // Plain-value getters (engines read these per-stroke)
    // ------------------------------------------------------------------

    public static EngineKind getEngineKind() { return engineKind.get(); }
    public static BrushMode getBrushMode() { return brushMode.get(); }
    public static double getBrushRadius() { return brushRadius.get(); }
    public static boolean getRadiusFollowsZoom() { return radiusFollowsZoom.get(); }
    public static int getCommitThrottleMs() { return commitThrottleMs.get(); }
    public static boolean getEraseAtEndpoints() { return eraseAtEndpoints.get(); }
    public static double getEndpointEraseProximity() { return endpointEraseProximity.get(); }
    public static int getLineConversionVertexCount() { return lineConversionVertexCount.get(); }
    public static double getEndStrokeSimplifyTolerance() { return endStrokeSimplifyTolerance.get(); }
    public static CursorOutlineColor getCursorOutlineColor() { return cursorOutlineColor.get(); }
    public static double getLocalRegionRadiusMultiplier() { return localRegionRadiusMultiplier.get(); }
    public static double getCursorEffectiveScale() { return cursorEffectiveScale.get(); }

    public static FalloffProfile getDirectFalloffProfile() { return directFalloffProfile.get(); }
    public static double getDirectRadialBias() { return directRadialBias.get(); }
    public static boolean getDirectDensifyEnabled() { return directDensifyEnabled.get(); }
    public static double getDirectDensifySpacingRatio() { return directDensifySpacingRatio.get(); }
    public static int getDirectMaxInsertionsPerStroke() { return directMaxInsertionsPerStroke.get(); }
    public static double getDirectVelocityDampingStrength() { return directVelocityDampingStrength.get(); }
    public static boolean getDirectPixelSensitivityEnabled() { return directPixelSensitivityEnabled.get(); }
    public static double getDirectPixelSensitivity() { return directPixelSensitivity.get(); }

    public static KernelType getFieldKernelType() { return fieldKernelType.get(); }
    public static double getFieldKernelSigmaFraction() { return fieldKernelSigmaFraction.get(); }
    public static double getFieldDisplacementStrength() { return fieldDisplacementStrength.get(); }
    public static double getFieldDensifyDivisor() { return fieldDensifyDivisor.get(); }
    public static boolean getFieldUseCatmullRomDensify() { return fieldUseCatmullRomDensify.get(); }
    public static boolean getFieldSelfIntersectionGuard() { return fieldSelfIntersectionGuard.get(); }

    // ------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------

    public static void resetDefaults() {
        engineKind.set(EngineKind.DIRECT_VERTEX);
        brushMode.set(BrushMode.AUTO);
        brushRadius.set(40.0);
        radiusFollowsZoom.set(true);
        cursorEffectiveScale.set(0.75);
        commitThrottleMs.set(33);
        eraseAtEndpoints.set(true);
        endpointEraseProximity.set(1.0);
        lineConversionVertexCount.set(32);
        endStrokeSimplifyTolerance.set(0.5);
        cursorOutlineColor.set(CursorOutlineColor.THEME);
        localRegionRadiusMultiplier.set(3.0);

        directFalloffProfile.set(FalloffProfile.COSINE);
        directRadialBias.set(0.25);
        directDensifyEnabled.set(true);
        directDensifySpacingRatio.set(0.25);
        directMaxInsertionsPerStroke.set(5000);
        directVelocityDampingStrength.set(0.0);
        directPixelSensitivityEnabled.set(false);
        directPixelSensitivity.set(2.0);

        fieldKernelType.set(KernelType.COSINE);
        fieldKernelSigmaFraction.set(0.33);
        fieldDisplacementStrength.set(1.0);
        fieldDensifyDivisor.set(4.0);
        fieldUseCatmullRomDensify.set(false);
        fieldSelfIntersectionGuard.set(true);
    }
}
