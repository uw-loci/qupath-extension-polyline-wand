package qupath.ext.polylinewand;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.tools.PathTool;

/**
 * Thin {@link PathTool} wrapper that registers the Polyline Wand event
 * handler (and its overlay) on the viewer.
 */
public final class PolylineWandPathTool implements PathTool {

    private final PolylineWandEventHandler handler;
    private final StringProperty name;
    private final ObjectProperty<Node> icon;
    private QuPathViewer viewer;

    public PolylineWandPathTool(PolylineWandEventHandler handler, String name, Node icon) {
        this.handler = handler;
        this.name = new SimpleStringProperty(name);
        this.icon = new SimpleObjectProperty<>(icon);
    }

    @Override
    public void registerTool(QuPathViewer viewer) {
        if (this.viewer != null) {
            deregisterTool(this.viewer);
        }
        this.viewer = viewer;
        if (viewer == null) {
            return;
        }
        Parent canvas = viewer.getView();
        canvas.addEventHandler(MouseEvent.ANY, handler);
        handler.attach(viewer);
    }

    @Override
    public void deregisterTool(QuPathViewer viewer) {
        if (this.viewer != viewer) {
            return;
        }
        Parent canvas = viewer.getView();
        canvas.removeEventHandler(MouseEvent.ANY, handler);
        handler.detach(viewer);
        this.viewer = null;
    }

    @Override
    public ReadOnlyStringProperty nameProperty() {
        return name;
    }

    @Override
    public ReadOnlyObjectProperty<Node> iconProperty() {
        return icon;
    }
}
