package qupath.ext.polylinewand;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;
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

    @Override
    public String getName() {
        return "Polyline Wand and Brush";
    }

    @Override
    public String getDescription() {
        return "Brush/wand-style editor for line and polyline annotations. "
                + "Three engines: direct vertex push, area-proxy skeletonize, "
                + "arc-length displacement field.";
    }

    @Override
    public void installExtension(QuPathGUI qupath) {
        if (isInstalled) {
            PolylineWandLogging.LOG.debug("Polyline Wand extension already installed");
            return;
        }
        isInstalled = true;

        installPreferences(qupath);

        // Tool installation on a daemon thread so any one-time class loads
        // do not stall startup.
        Thread t = new Thread(() -> {
            PolylineWandEventHandler handler = new PolylineWandEventHandler();
            Node icon = createModeAwareIcon(QuPathGUI.TOOLBAR_ICON_SIZE);
            PathTool tool = new PolylineWandPathTool(handler, "Polyline Wand", icon);

            Platform.runLater(() -> {
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
        }, "polyline-wand-init");
        t.setDaemon(true);
        t.start();
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

        Canvas pushedLine = new Canvas(size, size);
        // Repaint when the default-objects color changes so the polyline
        // overlay tracks the QuPath theme.
        PathPrefs.colorDefaultObjectsProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> drawPushedPolyline(pushedLine, size)));
        drawPushedPolyline(pushedLine, size);

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
     * Paint a small polyline being pushed by short rays from the wand tip.
     * The wand tip in FontAwesome's MAGIC glyph sits in the upper-right; we
     * draw three rays heading from there into the lower-left, and a short
     * polyline whose middle vertex is shoved away from the rays so the line
     * visibly bulges where the rays hit it.
     */
    private static void drawPushedPolyline(Canvas canvas, int size) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, size, size);

        Integer rgb = PathPrefs.colorDefaultObjectsProperty().get();
        Color stroke = rgb == null
                ? Color.WHITE
                : Color.rgb(ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb));

        double s = size;
        // Wand tip approx 75% across, 25% down (FontAwesome MAGIC glyph orientation).
        double tipX = s * 0.72;
        double tipY = s * 0.30;

        // Polyline runs lower-left to right edge with a pushed middle vertex.
        double rayLen = s * 0.28;
        double rayDx = -0.78;  // unit vector from tip toward lower-left
        double rayDy = 0.62;
        double pushAmount = s * 0.10;

        double p0x = s * 0.06,         p0y = s * 0.74;
        double p1x = s * 0.32 + rayDx * pushAmount, p1y = s * 0.62 + rayDy * pushAmount;
        double p2x = s * 0.58 + rayDx * pushAmount, p2y = s * 0.78 + rayDy * pushAmount;
        double p3x = s * 0.92,         p3y = s * 0.92;

        g.setStroke(stroke);
        g.setLineCap(StrokeLineCap.ROUND);

        // Rays -- thin strokes from the wand tip
        g.setLineWidth(Math.max(1.0, s * 0.05));
        g.setGlobalAlpha(0.75);
        for (int i = -1; i <= 1; i++) {
            double ang = Math.atan2(rayDy, rayDx) + i * 0.18;
            double ex = tipX + Math.cos(ang) * rayLen;
            double ey = tipY + Math.sin(ang) * rayLen;
            g.strokeLine(tipX, tipY, ex, ey);
        }

        // Polyline being pushed
        g.setGlobalAlpha(1.0);
        g.setLineWidth(Math.max(1.5, s * 0.09));
        g.strokeLine(p0x, p0y, p1x, p1y);
        g.strokeLine(p1x, p1y, p2x, p2y);
        g.strokeLine(p2x, p2y, p3x, p3y);
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
            ContextMenu menu = PolylineWandContextMenu.build();
            button.setContextMenu(menu);
            PolylineWandLogging.LOG.info("Polyline Wand context menu attached to toolbar button");
            return;
        }
        if (attempt < 10) {
            Platform.runLater(() -> tryAttachContextMenu(qupath, attempt + 1));
        } else {
            PolylineWandLogging.LOG.warn("Polyline Wand: could not find toolbar button after {} attempts", attempt);
        }
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
