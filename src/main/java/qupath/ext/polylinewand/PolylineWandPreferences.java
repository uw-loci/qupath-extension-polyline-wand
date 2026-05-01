package qupath.ext.polylinewand;

import javafx.collections.ObservableList;
import org.controlsfx.control.PropertySheet;
import qupath.ext.polylinewand.engine.direct.FalloffProfile;
import qupath.ext.polylinewand.engine.field.KernelType;
import qupath.ext.polylinewand.engine.proxy.DisconnectionPolicy;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.gui.QuPathGUI;

/**
 * Registers Polyline Wand preferences in QuPath's preference pane.
 * Engine-specific entries are grouped under sub-categories so the
 * dialog stays scannable.
 */
public final class PolylineWandPreferences {

    private static final String CATEGORY = "Polyline Wand";
    private static final String CATEGORY_DIRECT = "Polyline Wand: Direct vertex push";
    private static final String CATEGORY_PROXY = "Polyline Wand: Area proxy";
    private static final String CATEGORY_FIELD = "Polyline Wand: Displacement field";

    private PolylineWandPreferences() {}

    public static void installPreferences(QuPathGUI qupath) {
        if (qupath == null) {
            return;
        }
        PolylineWandLogging.LOG.info("Installing Polyline Wand preferences");

        ObservableList<PropertySheet.Item> items =
                qupath.getPreferencePane().getPropertySheet().getItems();

        // ---- Shared ----

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.engineKindProperty(), EngineKind.class)
                .name("Engine")
                .category(CATEGORY)
                .description("Which brush engine the tool uses.\n\n"
                        + "Direct vertex push (default): per-frame brush displaces vertices in radius. "
                        + "Most reactive feel; the brush can start anywhere and pull the line toward it.\n\n"
                        + "Area proxy + skeletonize: buffers the polyline to a thin area, edits as area, "
                        + "skeletonizes back on release. Best for bold reshapes.\n\n"
                        + "Displacement field: locks an active arc-length window at press; per-vertex "
                        + "cosine kernel + velocity damping for the most tactile feel.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.brushModeProperty(), BrushMode.class)
                .name("Mode")
                .category(CATEGORY)
                .description("Default brush behavior. AUTO pushes, but switches to ERASE_FROM_END "
                        + "automatically when the stroke begins near a polyline endpoint.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.brushRadiusProperty(), Double.class)
                .name("Brush radius (screen px)")
                .category(CATEGORY)
                .description("Brush radius in on-screen pixels. The brush stays a constant "
                        + "size on screen and automatically covers a larger region of the image "
                        + "when zoomed out. Adjustable live with the mouse wheel.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.commitThrottleMsProperty(), Integer.class)
                .name("Commit throttle (ms)")
                .category(CATEGORY)
                .description("Minimum interval between mid-drag setROI commits. 33 ms = ~30 Hz.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.eraseAtEndpointsProperty(), Boolean.class)
                .name("Erase at endpoints")
                .category(CATEGORY)
                .description("When the stroke begins near a polyline endpoint, switch to "
                        + "ERASE_FROM_END mode automatically. Hold Shift at press to override.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.endpointEraseProximityProperty(), Double.class)
                .name("Endpoint erase proximity")
                .category(CATEGORY)
                .description("Multiplier of brush radius. Cursor-down within this distance of an "
                        + "endpoint enters ERASE_FROM_END mode.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.lineConversionVertexCountProperty(), Integer.class)
                .name("Line-to-polyline vertex count")
                .category(CATEGORY)
                .description("When a LineROI is edited for the first time, it is densified into a "
                        + "PolylineROI with this many vertices.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.endStrokeSimplifyToleranceProperty(), Double.class)
                .name("End-stroke simplify tolerance")
                .category(CATEGORY)
                .description("Visvalingam-Whyatt tolerance applied to the touched range on mouse "
                        + "release. 0 disables simplification.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.cursorOutlineColorProperty(), CursorOutlineColor.class)
                .name("Cursor outline color")
                .category(CATEGORY)
                .description("Color of the brush-radius circle drawn over the viewer. THEME tracks "
                        + "the QuPath default-objects color preference.")
                .build());

        // ---- Engine A: Direct vertex push ----

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.directFalloffProfileProperty(), FalloffProfile.class)
                .name("Falloff profile")
                .category(CATEGORY_DIRECT)
                .description("Per-vertex weight curve. LINEAR is sharp, COSINE is smooth (default), "
                        + "GAUSSIAN has a softer edge.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.directRadialBiasProperty(), Double.class)
                .name("Radial bias")
                .category(CATEGORY_DIRECT)
                .description("Push direction blend. 0 = pure brush motion vector, 1 = pure radial-from-center.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.directDensifyEnabledProperty(), Boolean.class)
                .name("Densify on push")
                .category(CATEGORY_DIRECT)
                .description("Insert intermediate vertices into segments crossed by the brush so the "
                        + "push has something to act on.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.directDensifySpacingRatioProperty(), Double.class)
                .name("Densify spacing ratio")
                .category(CATEGORY_DIRECT)
                .description("Target spacing as a fraction of brush radius (lower values = more "
                        + "vertices). 0.25 places ~4 vertices across the brush diameter.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.directMaxInsertionsPerStrokeProperty(), Integer.class)
                .name("Max insertions per stroke")
                .category(CATEGORY_DIRECT)
                .description("Hard cap on densification inserts in a single stroke. Prevents "
                        + "dwell-on-spot vertex blowup.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.directVelocityDampingStrengthProperty(), Double.class)
                .name("Velocity damping")
                .category(CATEGORY_DIRECT)
                .description("Optional perpendicular-motion damping. 0 = off (default, original feel); "
                        + "higher values reduce displacement when the brush moves perpendicular to the "
                        + "local segment.")
                .build());

        // ---- Engine B: Area proxy ----

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.proxyBufferWidthFractionProperty(), Double.class)
                .name("Buffer width fraction")
                .category(CATEGORY_PROXY)
                .description("Buffer distance for the proxy area = max(buffer min, fraction * brush radius).")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.proxyBufferMinPxProperty(), Double.class)
                .name("Buffer minimum (px)")
                .category(CATEGORY_PROXY)
                .description("Hard floor on the proxy buffer distance, in image pixels.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.proxyMaskMaxDimProperty(), Integer.class)
                .name("Skeleton mask max dimension")
                .category(CATEGORY_PROXY)
                .description("Maximum width/height of the raster mask used by Zhang-Suen thinning. "
                        + "Caps skeletonization cost on huge polylines.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.proxySimplifyToleranceProperty(), Double.class)
                .name("Skeleton simplify tolerance")
                .category(CATEGORY_PROXY)
                .description("Visvalingam-Whyatt tolerance applied to the traced skeleton.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.proxyDisconnectionPolicyProperty(), DisconnectionPolicy.class)
                .name("Disconnection policy")
                .category(CATEGORY_PROXY)
                .description("What to do when the brush splits the proxy area into multiple components.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.proxyMidStrokePreviewMsProperty(), Integer.class)
                .name("Mid-stroke preview (ms)")
                .category(CATEGORY_PROXY)
                .description("How often to run a low-priority skeletonization during the stroke for "
                        + "visual confirmation. 0 disables.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.proxyAnchorEndpointsProperty(), Boolean.class)
                .name("Anchor original endpoints")
                .category(CATEGORY_PROXY)
                .description("Splice the original polyline's endpoint coordinates back onto the "
                        + "skeleton. Compensates for Zhang-Suen's 1-2 pixel endpoint erosion.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.proxyCloseGapsBeforeThinningProperty(), Boolean.class)
                .name("Close gaps before thinning")
                .category(CATEGORY_PROXY)
                .description("Run a 3x3 morphological close on the raster mask before Zhang-Suen "
                        + "thinning. Reduces skeleton spurs from raster jaggies.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.proxyOverlayFillAlphaProperty(), Integer.class)
                .name("Proxy overlay alpha")
                .category(CATEGORY_PROXY)
                .description("Translucency (0-255) of the working-area fill drawn during the stroke.")
                .build());

        // ---- Engine C: Displacement field ----

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.fieldKernelTypeProperty(), KernelType.class)
                .name("Kernel")
                .category(CATEGORY_FIELD)
                .description("Falloff shape: COSINE (tactile), GAUSSIAN (soft), LINEAR (proportional).")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.fieldKernelSigmaFractionProperty(), Double.class)
                .name("Gaussian sigma fraction")
                .category(CATEGORY_FIELD)
                .description("Gaussian sigma as a fraction of brush radius (Gaussian kernel only).")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.fieldDisplacementStrengthProperty(), Double.class)
                .name("Displacement strength")
                .category(CATEGORY_FIELD)
                .description("Global multiplier on per-frame displacement.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.fieldVelocityDampingStrengthProperty(), Double.class)
                .name("Velocity damping")
                .category(CATEGORY_FIELD)
                .description("How much fast perpendicular motion reduces displacement (0 = none, 1 = full).")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.fieldVelocityDampingMinProperty(), Double.class)
                .name("Velocity damping floor")
                .category(CATEGORY_FIELD)
                .description("Minimum damping floor: perpendicular motion never reduces displacement "
                        + "below this multiplier.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.fieldDensifyDivisorProperty(), Double.class)
                .name("Densify divisor")
                .category(CATEGORY_FIELD)
                .description("Insert vertices when an active segment is longer than (brush radius / divisor).")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.fieldUseCatmullRomDensifyProperty(), Boolean.class)
                .name("Catmull-Rom densify")
                .category(CATEGORY_FIELD)
                .description("Smoother midpoint insertion vs simple linear. Slightly more expensive.")
                .build());

        items.add(new PropertyItemBuilder<>(PolylineWandParameters.fieldSelfIntersectionGuardProperty(), Boolean.class)
                .name("Self-intersection guard")
                .category(CATEGORY_FIELD)
                .description("Reject per-frame vertex moves that would create a self-intersection "
                        + "within the active range.")
                .build());

        PolylineWandLogging.LOG.info("Polyline Wand preferences installed");
    }
}
