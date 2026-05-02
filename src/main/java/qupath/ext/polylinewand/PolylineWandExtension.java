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
import javafx.scene.text.TextAlignment;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.controlsfx.control.action.Action;
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
            Glyph icon = createModeAwareIcon(QuPathGUI.TOOLBAR_ICON_SIZE);
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
     * Build a Glyph icon that swaps between PAINT_BRUSH and CUT (scissors)
     * based on the current {@link BrushMode}. The toolbar listens to icon
     * changes via {@code iconProperty()} so we just mutate this single Node.
     */
    private static Glyph createModeAwareIcon(int size) {
        var fontAwesome = GlyphFontRegistry.font("FontAwesome");
        Glyph glyph = fontAwesome.create(FontAwesome.Glyph.PAINT_BRUSH).size(size);
        glyph.setAlignment(Pos.CENTER);
        glyph.setContentDisplay(ContentDisplay.CENTER);
        glyph.setTextAlignment(TextAlignment.CENTER);
        glyph.getStyleClass().add("qupath-icon");
        glyph.textFillProperty().bind(Bindings.createObjectBinding(
                () -> ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get()),
                PathPrefs.colorDefaultObjectsProperty()));
        Runnable updateGlyph = () -> {
            BrushMode mode = PolylineWandParameters.getBrushMode();
            glyph.setIcon(mode == BrushMode.CUT_AT_POINT
                    ? FontAwesome.Glyph.CUT
                    : FontAwesome.Glyph.PAINT_BRUSH);
        };
        updateGlyph.run();
        PolylineWandParameters.brushModeProperty().addListener(
                (obs, oldMode, newMode) -> Platform.runLater(updateGlyph));
        return glyph;
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
