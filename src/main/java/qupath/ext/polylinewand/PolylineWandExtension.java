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
import org.controlsfx.glyphfont.GlyphFontRegistry;
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
            Node icon = createIcon(QuPathGUI.TOOLBAR_ICON_SIZE);
            PathTool tool = new PolylineWandPathTool(handler, "Polyline Wand", icon);

            Platform.runLater(() -> {
                KeyCodeCombination chord = new KeyCodeCombination(KeyCode.P, KeyCombination.SHIFT_DOWN);
                qupath.getToolManager().installTool(tool, chord);
                qupath.getToolManager().getToolAction(tool).setLongText(String.format(
                        "(%s) Click and drag to edit a selected line or polyline annotation.%n"
                                + "Hold Shift to override auto erase-at-endpoint.%n"
                                + "Mouse-wheel adjusts brush radius.%n"
                                + "Right-click toolbar button for engine choice and options.",
                        chord.getDisplayText()));
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

    private static Node createIcon(int size) {
        var fontAwesome = GlyphFontRegistry.font("FontAwesome");
        var glyph = fontAwesome.create(FontAwesome.Glyph.PAINT_BRUSH).size(size);
        glyph.setAlignment(Pos.CENTER);
        glyph.setContentDisplay(ContentDisplay.CENTER);
        glyph.setTextAlignment(TextAlignment.CENTER);
        glyph.getStyleClass().add("qupath-icon");
        glyph.textFillProperty().bind(Bindings.createObjectBinding(
                () -> ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get()),
                PathPrefs.colorDefaultObjectsProperty()));
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
