package ru.zkir.josm.plugins.z3dviewer;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.tools.Shortcut;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static org.openstreetmap.josm.tools.I18n.tr;

public class ToggleWireframeAction extends JosmAction {

    private final Renderer3D renderer3D;

    public ToggleWireframeAction(Renderer3D renderer3D) {
        super(tr("Wireframe"), "wireframe", tr("Toggle wireframe mode"),
                Shortcut.registerShortcut("view:wireframe", tr("View: {0}", tr("Toggle Wireframe")),
                        KeyEvent.VK_Z, Shortcut.DIRECT), true);
        this.renderer3D = renderer3D;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        renderer3D.toggleWireframeMode();
        renderer3D.repaint();
    }

    @Override
    public void updateEnabledState() {
        setEnabled(true);
    }
}