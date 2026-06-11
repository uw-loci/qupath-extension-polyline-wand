package qupath.ext.polylinewand;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;
import org.controlsfx.control.decoration.Decorator;
import org.controlsfx.control.decoration.GraphicDecoration;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.controlsfx.control.action.Action;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.tools.PathTool;

/**
 * Entry point for the Polyline Wand and Brush extension.
 * <p>
 * Registers the tool with the QuPath toolbar (default chord: Shift+P),
 * installs preferences in the preferences pane, and attaches a right-click
 * context menu to the toolbar button.
 */
public final class PolylineWandExtension implements QuPathExtension {

    private boolean isInstalled = false;

    // The mode-aware icon node. QuPath binds a tool's iconProperty to the tool
    // action's graphic, but in practice the toolbar button is built as a text
    // button with a null graphic before that binding takes effect, so the icon
    // never appears. We therefore set this node on the button directly once it
    // exists (see applyIconToToolbarButton).
    private Node iconNode;

    @Override
    public String getName() {
        return "Polyline Wand and Brush";
    }

    @Override
    public String getDescription() {
        return "Brush/wand-style editor for line and polyline annotations. "
                + "Two engines: direct vertex push, arc-length displacement field.";
    }

    @Override
    public void installExtension(QuPathGUI qupath) {
        if (isInstalled) {
            PolylineWandLogging.LOG.debug("Polyline Wand extension already installed");
            return;
        }
        isInstalled = true;

        installPreferences(qupath);

        // The icon Node (Glyph + Line shapes in a StackPane) is constructed on
        // the FX thread and later set on the toolbar button directly -- see
        // applyIconToToolbarButton for why the tool's iconProperty alone is not
        // enough to make the icon appear.
        PolylineWandEventHandler handler = new PolylineWandEventHandler();
        Platform.runLater(() -> {
            Node icon = createModeAwareIcon(QuPathGUI.TOOLBAR_ICON_SIZE);
            iconNode = icon;
            PathTool tool = new PolylineWandPathTool(handler, "Polyline Wand", icon);

            KeyCodeCombination chord = new KeyCodeCombination(KeyCode.P, KeyCombination.SHIFT_DOWN);
            qupath.getToolManager().installTool(tool, chord);
            Action action = qupath.getToolManager().getToolAction(tool);
            Runnable updateLongText = () -> {
                BrushMode mode = PolylineWandParameters.getBrushMode();
                if (mode == BrushMode.CUT_AT_POINT) {
                    action.setLongText(String.format(
                            "(%s) Polyline Wand: Scissors mode.%n"
                                    + "Click on a selected polyline to cut it at the point "
                                    + "nearest to the click. The polyline is split into two "
                                    + "annotations.%n"
                                    + "Right-click toolbar button to switch back to brush mode.",
                            chord.getDisplayText()));
                } else {
                    action.setLongText(String.format(
                            "(%s) Polyline Wand: brush mode.%n"
                                    + "Click and drag to edit a selected line or polyline.%n"
                                    + "Hold Shift to override auto erase-at-endpoint.%n"
                                    + "Right-click toolbar button for engine choice, scissors mode, "
                                    + "brush radius, and other options.",
                            chord.getDisplayText()));
                }
            };
            updateLongText.run();
            PolylineWandParameters.brushModeProperty().addListener(
                    (obs, oldMode, newMode) -> Platform.runLater(updateLongText));
            attachContextMenuToToolbarButton(qupath);
        });
    }

    private void installPreferences(QuPathGUI qupath) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> installPreferences(qupath));
            return;
        }
        PolylineWandPreferences.installPreferences(qupath);
    }

    /**
     * Build an icon Node that swaps between a wand+pushed-polyline composite
     * and a CUT (scissors) glyph based on the current {@link BrushMode}.
     * The composite layers a MAGIC wand glyph under a small Canvas that
     * draws a polyline being pushed by short rays from the wand tip, so
     * the icon visually communicates "wand pushing on a line".
     */
    private static Node createModeAwareIcon(int size) {
        StackPane root = new StackPane();
        root.setPrefSize(size, size);
        root.setMinSize(size, size);
        root.setMaxSize(size, size);

        Glyph wandGlyph = makeStyledGlyph(FontAwesome.Glyph.MAGIC, size);
        Glyph scissorsGlyph = makeStyledGlyph(FontAwesome.Glyph.CUT, size);

        // Retained-mode shape overlay of a "wand pushing a line". A Canvas is
        // NOT rendered when it sits inside the graphic of a toolbar button --
        // only shape/Glyph nodes are (the same reason the corner triangle, a
        // Path, shows). The pushed polyline is therefore drawn with Line nodes
        // rather than a Canvas; its stroke tracks the default-objects color.
        Pane pushedLine = buildPushedPolylineOverlay(size);

        Runnable updateMode = () -> {
            BrushMode mode = PolylineWandParameters.getBrushMode();
            root.getChildren().clear();
            if (mode == BrushMode.CUT_AT_POINT) {
                root.getChildren().add(scissorsGlyph);
            } else {
                root.getChildren().addAll(wandGlyph, pushedLine);
            }
        };
        updateMode.run();
        PolylineWandParameters.brushModeProperty().addListener(
                (obs, oldMode, newMode) -> Platform.runLater(updateMode));
        return root;
    }

    private static Glyph makeStyledGlyph(FontAwesome.Glyph icon, int size) {
        var fontAwesome = GlyphFontRegistry.font("FontAwesome");
        Glyph glyph = fontAwesome.create(icon).size(size);
        glyph.setAlignment(Pos.CENTER);
        glyph.setContentDisplay(ContentDisplay.CENTER);
        glyph.setTextAlignment(TextAlignment.CENTER);
        glyph.getStyleClass().add("qupath-icon");
        glyph.textFillProperty().bind(Bindings.createObjectBinding(
                () -> ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get()),
                PathPrefs.colorDefaultObjectsProperty()));
        return glyph;
    }

    /**
     * Build a small "wand pushing a line" overlay out of {@link Line} shape
     * nodes. The wand tip in FontAwesome's MAGIC glyph sits in the upper-right;
     * three rays head from there into the lower-left, and a short polyline whose
     * middle vertices are shoved away from the rays so the line visibly bulges
     * where the rays hit it. Strokes bind to the default-objects color so the
     * icon tracks the QuPath theme.
     *
     * <p>Uses retained-mode shapes (not a Canvas) because a Canvas does not
     * render inside a toolbar button's graphic.</p>
     */
    private static Pane buildPushedPolylineOverlay(double s) {
        Pane pane = new Pane();
        pane.setPrefSize(s, s);
        pane.setMinSize(s, s);
        pane.setMaxSize(s, s);
        pane.setMouseTransparent(true);

        var strokeColor = Bindings.createObjectBinding(
                () -> {
                    Integer rgb = PathPrefs.colorDefaultObjectsProperty().get();
                    return rgb == null
                            ? Color.WHITE
                            : Color.rgb(ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb));
                },
                PathPrefs.colorDefaultObjectsProperty());

        // Wand tip approx 72% across, 30% down (FontAwesome MAGIC glyph orientation).
        double tipX = s * 0.72;
        double tipY = s * 0.30;
        double rayLen = s * 0.28;
        double rayDx = -0.78; // unit vector from tip toward lower-left
        double rayDy = 0.62;
        double pushAmount = s * 0.10;
        double rayWidth = Math.max(1.0, s * 0.05);
        double lineWidth = Math.max(1.5, s * 0.09);

        // Rays -- thin strokes fanning out from the wand tip.
        double baseAng = Math.atan2(rayDy, rayDx);
        for (int i = -1; i <= 1; i++) {
            double ang = baseAng + i * 0.18;
            double ex = tipX + Math.cos(ang) * rayLen;
            double ey = tipY + Math.sin(ang) * rayLen;
            Line ray = new Line(tipX, tipY, ex, ey);
            ray.strokeProperty().bind(strokeColor);
            ray.setStrokeWidth(rayWidth);
            ray.setStrokeLineCap(StrokeLineCap.ROUND);
            ray.setOpacity(0.75);
            pane.getChildren().add(ray);
        }

        // Polyline running lower-left to right edge with a pushed middle.
        double p0x = s * 0.06, p0y = s * 0.74;
        double p1x = s * 0.32 + rayDx * pushAmount, p1y = s * 0.62 + rayDy * pushAmount;
        double p2x = s * 0.58 + rayDx * pushAmount, p2y = s * 0.78 + rayDy * pushAmount;
        double p3x = s * 0.92, p3y = s * 0.92;
        double[][] segments = {
            {p0x, p0y, p1x, p1y},
            {p1x, p1y, p2x, p2y},
            {p2x, p2y, p3x, p3y},
        };
        for (double[] seg : segments) {
            Line line = new Line(seg[0], seg[1], seg[2], seg[3]);
            line.strokeProperty().bind(strokeColor);
            line.setStrokeWidth(lineWidth);
            line.setStrokeLineCap(StrokeLineCap.ROUND);
            pane.getChildren().add(line);
        }
        return pane;
    }

    /**
     * Put the mode-aware icon directly on the toolbar button. QuPath is meant to
     * derive the button graphic from the tool's {@code iconProperty}, but the
     * button is created as a text button with a {@code null} graphic before that
     * binding lands, leaving the body of the icon blank (only the separately
     * added corner triangle shows). Setting the graphic on the button here --
     * after unbinding any null-valued binding QuPath installed -- guarantees the
     * icon appears. The node keeps its own brush/scissors mode listener, so
     * mode swaps continue to work after this hand-off.
     */
    private void applyIconToToolbarButton(ButtonBase button) {
        if (iconNode == null) {
            return;
        }
        if (button.graphicProperty().isBound()) {
            button.graphicProperty().unbind();
        }
        button.setGraphic(iconNode);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void attachContextMenuToToolbarButton(QuPathGUI qupath) {
        Platform.runLater(() -> Platform.runLater(() -> tryAttachContextMenu(qupath, 0)));
    }

    private void tryAttachContextMenu(QuPathGUI qupath, int attempt) {
        ToolBar toolBar = qupath.getToolBar();
        if (toolBar == null) {
            PolylineWandLogging.LOG.warn("Polyline Wand: could not get toolbar for context menu");
            return;
        }
        ButtonBase button = findToolbarButton(toolBar);
        if (button != null) {
            applyIconToToolbarButton(button);
            ContextMenu menu = PolylineWandContextMenu.build();
            button.setContextMenu(menu);
            addContextMenuDecoration(button, menu);
            PolylineWandLogging.LOG.info("Polyline Wand context menu attached to toolbar button");
            return;
        }
        if (attempt < 10) {
            Platform.runLater(() -> tryAttachContextMenu(qupath, attempt + 1));
        } else {
            PolylineWandLogging.LOG.warn("Polyline Wand: could not find toolbar button after {} attempts", attempt);
        }
    }

    /**
     * Add a small triangle to the bottom-right corner of the toolbar button so
     * the right-click submenu is discoverable. Mirrors QuPath's own
     * {@code ToolBarComponent.addContextMenuDecoration} (geometry, rotation and
     * opacity copied verbatim) so this triangle reads as a peer of the ones on
     * the built-in Line / Polyline tool buttons.
     *
     * <p>ControlsFX decorations require the node to be in a scene and are
     * dropped when the graphic changes (the icon swaps between brush and
     * scissors modes here), so the decoration is also re-applied via
     * {@code sceneProperty} and {@code graphicProperty} listeners.</p>
     */
    private static void addContextMenuDecoration(ButtonBase button, ContextMenu menu) {
        double width = 6;
        Path triangle = new Path(
                new MoveTo(0, 0),
                new LineTo(width, 0),
                new LineTo(width / 2.0, Math.sqrt(width * width / 2.0)),
                new ClosePath());
        triangle.setTranslateX(-width);
        triangle.setTranslateY(-width);
        triangle.setRotate(-90);
        triangle.setStroke(null);
        triangle.setOpacity(0.5);
        triangle.fillProperty().bind(button.textFillProperty());
        triangle.setOnMouseClicked(e -> {
            menu.show(button, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        GraphicDecoration decoration = new GraphicDecoration(triangle, Pos.BOTTOM_RIGHT);
        button.sceneProperty().addListener((obs, oldScene, newScene) -> Platform.runLater(() -> {
            if (newScene != null) {
                Decorator.addDecoration(button, decoration);
            } else {
                Decorator.removeDecoration(button, decoration);
            }
        }));
        button.graphicProperty().addListener((obs, oldGraphic, newGraphic) -> {
            Decorator.removeAllDecorations(button);
            Platform.runLater(() -> Decorator.addDecoration(button, decoration));
        });
        Platform.runLater(() -> Decorator.addDecoration(button, decoration));
    }

    private static ButtonBase findToolbarButton(ToolBar toolBar) {
        for (Node item : toolBar.getItems()) {
            ButtonBase b = findButton(item);
            if (b == null) continue;
            var tooltip = b.getTooltip();
            if (tooltip != null && tooltip.getText() != null
                    && tooltip.getText().contains("Polyline Wand")) {
                return b;
            }
            if ("Polyline Wand".equals(b.getText())) {
                return b;
            }
            Object action = b.getProperties().get("controlsfx.actions.action");
            if (action instanceof org.controlsfx.control.action.Action a
                    && "Polyline Wand".equals(a.getText())) {
                return b;
            }
        }
        return null;
    }

    private static ButtonBase findButton(Node node) {
        if (node instanceof ButtonBase b) return b;
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                ButtonBase found = findButton(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
