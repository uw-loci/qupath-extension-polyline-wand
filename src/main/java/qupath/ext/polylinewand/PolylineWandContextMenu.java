package qupath.ext.polylinewand;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Region;
import qupath.ext.polylinewand.engine.direct.FalloffProfile;
import qupath.ext.polylinewand.engine.field.KernelType;
import qupath.ext.polylinewand.engine.proxy.DisconnectionPolicy;

/**
 * Right-click context menu for the Polyline Wand toolbar button.
 * <p>
 * Engine-specific submenu rebuilds itself in {@code setOnShowing} based
 * on the current {@link EngineKind} so users only see relevant controls.
 */
public final class PolylineWandContextMenu {

    private PolylineWandContextMenu() {}

    public static ContextMenu build() {
        ContextMenu menu = new ContextMenu();
        menu.setOnShowing(event -> populate(menu));
        populate(menu);
        return menu;
    }

    private static void populate(ContextMenu menu) {
        menu.getItems().clear();

        // Engine selector
        menu.getItems().add(buildEngineSubmenu());
        // Mode selector
        menu.getItems().add(buildModeSubmenu());
        // Engine-specific settings (rebuilt every time)
        menu.getItems().add(buildEngineSettingsSubmenu());
        menu.getItems().add(new SeparatorMenuItem());

        MenuItem brushRadiusItem = new MenuItem("Set brush radius...");
        brushRadiusItem.setOnAction(e -> promptDouble("Brush radius (px)",
                "Brush radius in image pixels:",
                PolylineWandParameters.getBrushRadius(),
                PolylineWandParameters.brushRadiusProperty()::set));
        menu.getItems().add(brushRadiusItem);

        menu.getItems().add(new SeparatorMenuItem());

        MenuItem resetItem = new MenuItem("Reset Polyline Wand preferences");
        resetItem.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Reset all Polyline Wand preferences (engine choice, brush radius, "
                            + "engine-specific tunings) to their defaults?",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setHeaderText("Reset Polyline Wand preferences");
            confirm.setTitle("Polyline Wand");
            confirm.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            confirm.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    PolylineWandParameters.resetDefaults();
                    PolylineWandLogging.LOG.info("Polyline Wand preferences reset to defaults");
                }
            });
        });
        menu.getItems().add(resetItem);
    }

    private static Menu buildEngineSubmenu() {
        Menu m = new Menu("Engine");
        ToggleGroup group = new ToggleGroup();
        for (EngineKind kind : EngineKind.values()) {
            RadioMenuItem item = new RadioMenuItem(kind.getDisplayName());
            item.setToggleGroup(group);
            item.setSelected(PolylineWandParameters.getEngineKind() == kind);
            item.setOnAction(e -> PolylineWandParameters.engineKindProperty().set(kind));
            m.getItems().add(item);
        }
        return m;
    }

    private static Menu buildModeSubmenu() {
        Menu m = new Menu("Mode");
        ToggleGroup group = new ToggleGroup();
        for (BrushMode mode : BrushMode.values()) {
            RadioMenuItem item = new RadioMenuItem(mode.getDisplayName());
            item.setToggleGroup(group);
            item.setSelected(PolylineWandParameters.getBrushMode() == mode);
            item.setOnAction(e -> PolylineWandParameters.brushModeProperty().set(mode));
            m.getItems().add(item);
        }
        return m;
    }

    private static Menu buildEngineSettingsSubmenu() {
        Menu m = new Menu("Engine settings");
        EngineKind kind = PolylineWandParameters.getEngineKind();
        switch (kind) {
            case DIRECT_VERTEX:
                buildDirectSettings(m);
                break;
            case AREA_PROXY:
                buildProxySettings(m);
                break;
            case DISPLACEMENT_FIELD:
                buildFieldSettings(m);
                break;
        }
        return m;
    }

    private static void buildDirectSettings(Menu m) {
        CheckMenuItem densify = new CheckMenuItem("Densify on push");
        densify.setSelected(PolylineWandParameters.getDirectDensifyEnabled());
        densify.setOnAction(e -> PolylineWandParameters.directDensifyEnabledProperty()
                .set(densify.isSelected()));
        m.getItems().add(densify);

        Menu falloff = new Menu("Falloff");
        ToggleGroup g = new ToggleGroup();
        for (FalloffProfile p : FalloffProfile.values()) {
            RadioMenuItem item = new RadioMenuItem(p.name());
            item.setToggleGroup(g);
            item.setSelected(PolylineWandParameters.getDirectFalloffProfile() == p);
            item.setOnAction(e -> PolylineWandParameters.directFalloffProfileProperty().set(p));
            falloff.getItems().add(item);
        }
        m.getItems().add(falloff);

        MenuItem radial = new MenuItem("Set radial bias...");
        radial.setOnAction(e -> promptDouble("Radial bias",
                "0 = pure motion vector, 1 = pure radial-from-center:",
                PolylineWandParameters.getDirectRadialBias(),
                PolylineWandParameters.directRadialBiasProperty()::set));
        m.getItems().add(radial);

        MenuItem maxIns = new MenuItem("Set max insertions per stroke...");
        maxIns.setOnAction(e -> promptInteger("Max insertions per stroke",
                "Hard cap on densification inserts per stroke:",
                PolylineWandParameters.getDirectMaxInsertionsPerStroke(),
                PolylineWandParameters.directMaxInsertionsPerStrokeProperty()::set));
        m.getItems().add(maxIns);
    }

    private static void buildProxySettings(Menu m) {
        Menu policy = new Menu("Disconnection policy");
        ToggleGroup g = new ToggleGroup();
        for (DisconnectionPolicy p : DisconnectionPolicy.values()) {
            RadioMenuItem item = new RadioMenuItem(p.name());
            item.setToggleGroup(g);
            item.setSelected(PolylineWandParameters.getProxyDisconnectionPolicy() == p);
            item.setOnAction(e -> PolylineWandParameters.proxyDisconnectionPolicyProperty().set(p));
            policy.getItems().add(item);
        }
        m.getItems().add(policy);

        CheckMenuItem anchor = new CheckMenuItem("Anchor original endpoints");
        anchor.setSelected(PolylineWandParameters.getProxyAnchorEndpoints());
        anchor.setOnAction(e -> PolylineWandParameters.proxyAnchorEndpointsProperty()
                .set(anchor.isSelected()));
        m.getItems().add(anchor);

        CheckMenuItem closeGaps = new CheckMenuItem("Close gaps before thinning");
        closeGaps.setSelected(PolylineWandParameters.getProxyCloseGapsBeforeThinning());
        closeGaps.setOnAction(e -> PolylineWandParameters.proxyCloseGapsBeforeThinningProperty()
                .set(closeGaps.isSelected()));
        m.getItems().add(closeGaps);

        MenuItem buf = new MenuItem("Set buffer fraction...");
        buf.setOnAction(e -> promptDouble("Buffer width fraction",
                "Fraction of brush radius used as proxy buffer width:",
                PolylineWandParameters.getProxyBufferWidthFraction(),
                PolylineWandParameters.proxyBufferWidthFractionProperty()::set));
        m.getItems().add(buf);

        MenuItem simp = new MenuItem("Set simplify tolerance...");
        simp.setOnAction(e -> promptDouble("Skeleton simplify tolerance",
                "Visvalingam-Whyatt tolerance:",
                PolylineWandParameters.getProxySimplifyTolerance(),
                PolylineWandParameters.proxySimplifyToleranceProperty()::set));
        m.getItems().add(simp);
    }

    private static void buildFieldSettings(Menu m) {
        Menu kernel = new Menu("Kernel");
        ToggleGroup g = new ToggleGroup();
        for (KernelType k : KernelType.values()) {
            RadioMenuItem item = new RadioMenuItem(k.name());
            item.setToggleGroup(g);
            item.setSelected(PolylineWandParameters.getFieldKernelType() == k);
            item.setOnAction(e -> PolylineWandParameters.fieldKernelTypeProperty().set(k));
            kernel.getItems().add(item);
        }
        m.getItems().add(kernel);

        CheckMenuItem guard = new CheckMenuItem("Self-intersection guard");
        guard.setSelected(PolylineWandParameters.getFieldSelfIntersectionGuard());
        guard.setOnAction(e -> PolylineWandParameters.fieldSelfIntersectionGuardProperty()
                .set(guard.isSelected()));
        m.getItems().add(guard);

        CheckMenuItem catmull = new CheckMenuItem("Catmull-Rom densify");
        catmull.setSelected(PolylineWandParameters.getFieldUseCatmullRomDensify());
        catmull.setOnAction(e -> PolylineWandParameters.fieldUseCatmullRomDensifyProperty()
                .set(catmull.isSelected()));
        m.getItems().add(catmull);

        MenuItem damp = new MenuItem("Set velocity damping...");
        damp.setOnAction(e -> promptDouble("Velocity damping",
                "0 = none, 1 = full perpendicular damping:",
                PolylineWandParameters.getFieldVelocityDampingStrength(),
                PolylineWandParameters.fieldVelocityDampingStrengthProperty()::set));
        m.getItems().add(damp);
    }

    private static void promptDouble(String title, String header, double current,
                                     java.util.function.DoubleConsumer setter) {
        TextInputDialog dlg = new TextInputDialog(String.valueOf(current));
        dlg.setTitle("Polyline Wand");
        dlg.setHeaderText(title);
        dlg.setContentText(header);
        dlg.showAndWait().ifPresent(s -> {
            try {
                setter.accept(Double.parseDouble(s.trim()));
            } catch (NumberFormatException ex) {
                PolylineWandLogging.LOG.debug("Invalid number entered: {}", s);
            }
        });
    }

    private static void promptInteger(String title, String header, int current,
                                      java.util.function.IntConsumer setter) {
        TextInputDialog dlg = new TextInputDialog(String.valueOf(current));
        dlg.setTitle("Polyline Wand");
        dlg.setHeaderText(title);
        dlg.setContentText(header);
        dlg.showAndWait().ifPresent(s -> {
            try {
                setter.accept(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ex) {
                PolylineWandLogging.LOG.debug("Invalid integer entered: {}", s);
            }
        });
    }
}
