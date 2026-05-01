package qupath.ext.polylinewand;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import qupath.ext.polylinewand.engine.direct.FalloffProfile;
import qupath.ext.polylinewand.engine.field.KernelType;
import qupath.ext.polylinewand.engine.proxy.DisconnectionPolicy;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Persistent preferences for the Polyline Wand and Brush.
 * <p>
 * All settings survive QuPath restarts via PathPrefs.
 * Engine-specific keys are namespaced (direct*, proxy*, field*) so a
 * single Reset call can restore every value to its default.
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
            PathPrefs.createPersistentPreference("polylineWandBrushRadius", 25.0);

    private static final BooleanProperty radiusFollowsZoom =
            PathPrefs.createPersistentPreference("polylineWandRadiusFollowsZoom", true);

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

    // ------------------------------------------------------------------
    // Engine B: Area proxy + skeletonize
    // ------------------------------------------------------------------

    private static final DoubleProperty proxyBufferWidthFraction =
            PathPrefs.createPersistentPreference("polylineWandProxyBufferWidthFraction", 0.25);

    private static final DoubleProperty proxyBufferMinPx =
            PathPrefs.createPersistentPreference("polylineWandProxyBufferMinPx", 0.5);

    private static final IntegerProperty proxyMaskMaxDim =
            PathPrefs.createPersistentPreference("polylineWandProxyMaskMaxDim", 4096);

    private static final DoubleProperty proxySimplifyTolerance =
            PathPrefs.createPersistentPreference("polylineWandProxySimplifyTolerance", 1.0);

    private static final ObjectProperty<DisconnectionPolicy> proxyDisconnectionPolicy =
            PathPrefs.createPersistentPreference("polylineWandProxyDisconnectPolicy",
                    DisconnectionPolicy.KEEP_LONGEST, DisconnectionPolicy.class);

    private static final IntegerProperty proxyMidStrokePreviewMs =
            PathPrefs.createPersistentPreference("polylineWandProxyMidStrokePreviewMs", 200);

    private static final BooleanProperty proxyAnchorEndpoints =
            PathPrefs.createPersistentPreference("polylineWandProxyAnchorEndpoints", true);

    private static final BooleanProperty proxyCloseGapsBeforeThinning =
            PathPrefs.createPersistentPreference("polylineWandProxyCloseGapsBeforeThinning", true);

    private static final IntegerProperty proxyOverlayFillAlpha =
            PathPrefs.createPersistentPreference("polylineWandProxyOverlayFillAlpha", 80);

    // ------------------------------------------------------------------
    // Engine C: Arc-length displacement field
    // ------------------------------------------------------------------

    private static final ObjectProperty<KernelType> fieldKernelType =
            PathPrefs.createPersistentPreference("polylineWandFieldKernelType",
                    KernelType.COSINE, KernelType.class);

    private static final DoubleProperty fieldKernelSigmaFraction =
            PathPrefs.createPersistentPreference("polylineWandFieldKernelSigmaFraction", 0.33);

    private static final DoubleProperty fieldDisplacementStrength =
            PathPrefs.createPersistentPreference("polylineWandFieldDisplacementStrength", 1.0);

    private static final DoubleProperty fieldVelocityDampingStrength =
            PathPrefs.createPersistentPreference("polylineWandFieldVelocityDamping", 0.6);

    private static final DoubleProperty fieldVelocityDampingMin =
            PathPrefs.createPersistentPreference("polylineWandFieldVelocityDampingMin", 0.25);

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

    public static ObjectProperty<FalloffProfile> directFalloffProfileProperty() { return directFalloffProfile; }
    public static DoubleProperty directRadialBiasProperty() { return directRadialBias; }
    public static BooleanProperty directDensifyEnabledProperty() { return directDensifyEnabled; }
    public static DoubleProperty directDensifySpacingRatioProperty() { return directDensifySpacingRatio; }
    public static IntegerProperty directMaxInsertionsPerStrokeProperty() { return directMaxInsertionsPerStroke; }
    public static DoubleProperty directVelocityDampingStrengthProperty() { return directVelocityDampingStrength; }

    public static DoubleProperty proxyBufferWidthFractionProperty() { return proxyBufferWidthFraction; }
    public static DoubleProperty proxyBufferMinPxProperty() { return proxyBufferMinPx; }
    public static IntegerProperty proxyMaskMaxDimProperty() { return proxyMaskMaxDim; }
    public static DoubleProperty proxySimplifyToleranceProperty() { return proxySimplifyTolerance; }
    public static ObjectProperty<DisconnectionPolicy> proxyDisconnectionPolicyProperty() { return proxyDisconnectionPolicy; }
    public static IntegerProperty proxyMidStrokePreviewMsProperty() { return proxyMidStrokePreviewMs; }
    public static BooleanProperty proxyAnchorEndpointsProperty() { return proxyAnchorEndpoints; }
    public static BooleanProperty proxyCloseGapsBeforeThinningProperty() { return proxyCloseGapsBeforeThinning; }
    public static IntegerProperty proxyOverlayFillAlphaProperty() { return proxyOverlayFillAlpha; }

    public static ObjectProperty<KernelType> fieldKernelTypeProperty() { return fieldKernelType; }
    public static DoubleProperty fieldKernelSigmaFractionProperty() { return fieldKernelSigmaFraction; }
    public static DoubleProperty fieldDisplacementStrengthProperty() { return fieldDisplacementStrength; }
    public static DoubleProperty fieldVelocityDampingStrengthProperty() { return fieldVelocityDampingStrength; }
    public static DoubleProperty fieldVelocityDampingMinProperty() { return fieldVelocityDampingMin; }
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

    public static FalloffProfile getDirectFalloffProfile() { return directFalloffProfile.get(); }
    public static double getDirectRadialBias() { return directRadialBias.get(); }
    public static boolean getDirectDensifyEnabled() { return directDensifyEnabled.get(); }
    public static double getDirectDensifySpacingRatio() { return directDensifySpacingRatio.get(); }
    public static int getDirectMaxInsertionsPerStroke() { return directMaxInsertionsPerStroke.get(); }
    public static double getDirectVelocityDampingStrength() { return directVelocityDampingStrength.get(); }

    public static double getProxyBufferWidthFraction() { return proxyBufferWidthFraction.get(); }
    public static double getProxyBufferMinPx() { return proxyBufferMinPx.get(); }
    public static int getProxyMaskMaxDim() { return proxyMaskMaxDim.get(); }
    public static double getProxySimplifyTolerance() { return proxySimplifyTolerance.get(); }
    public static DisconnectionPolicy getProxyDisconnectionPolicy() { return proxyDisconnectionPolicy.get(); }
    public static int getProxyMidStrokePreviewMs() { return proxyMidStrokePreviewMs.get(); }
    public static boolean getProxyAnchorEndpoints() { return proxyAnchorEndpoints.get(); }
    public static boolean getProxyCloseGapsBeforeThinning() { return proxyCloseGapsBeforeThinning.get(); }
    public static int getProxyOverlayFillAlpha() { return proxyOverlayFillAlpha.get(); }

    public static KernelType getFieldKernelType() { return fieldKernelType.get(); }
    public static double getFieldKernelSigmaFraction() { return fieldKernelSigmaFraction.get(); }
    public static double getFieldDisplacementStrength() { return fieldDisplacementStrength.get(); }
    public static double getFieldVelocityDampingStrength() { return fieldVelocityDampingStrength.get(); }
    public static double getFieldVelocityDampingMin() { return fieldVelocityDampingMin.get(); }
    public static double getFieldDensifyDivisor() { return fieldDensifyDivisor.get(); }
    public static boolean getFieldUseCatmullRomDensify() { return fieldUseCatmullRomDensify.get(); }
    public static boolean getFieldSelfIntersectionGuard() { return fieldSelfIntersectionGuard.get(); }

    // ------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------

    public static void resetDefaults() {
        engineKind.set(EngineKind.DIRECT_VERTEX);
        brushMode.set(BrushMode.AUTO);
        brushRadius.set(25.0);
        radiusFollowsZoom.set(true);
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

        proxyBufferWidthFraction.set(0.25);
        proxyBufferMinPx.set(0.5);
        proxyMaskMaxDim.set(4096);
        proxySimplifyTolerance.set(1.0);
        proxyDisconnectionPolicy.set(DisconnectionPolicy.KEEP_LONGEST);
        proxyMidStrokePreviewMs.set(200);
        proxyAnchorEndpoints.set(true);
        proxyCloseGapsBeforeThinning.set(true);
        proxyOverlayFillAlpha.set(80);

        fieldKernelType.set(KernelType.COSINE);
        fieldKernelSigmaFraction.set(0.33);
        fieldDisplacementStrength.set(1.0);
        fieldVelocityDampingStrength.set(0.6);
        fieldVelocityDampingMin.set(0.25);
        fieldDensifyDivisor.set(4.0);
        fieldUseCatmullRomDensify.set(false);
        fieldSelfIntersectionGuard.set(true);
    }
}
