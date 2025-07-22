package ru.zkir.josm.plugins.z3dviewer;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.Shortcut;

import java.awt.event.ActionEvent;
import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Action to toggle the 3D Viewer dialog.
 */
public class Z3dViewerAction extends JosmAction {

    public Z3dViewerAction() {
        super(tr("3D Viewer"), "eye", tr("Open 3D Viewer"),
              Shortcut.registerShortcut("threedviewer:toggle", tr("Toggle: {0}", tr("3D Viewer")),
              java.awt.event.KeyEvent.VK_T, Shortcut.ALT_SHIFT), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Find the dialog and toggle it
        Z3dViewerDialog dialog = MainApplication.getMap().getToggleDialog(Z3dViewerDialog.class);
        if (dialog != null) {
            dialog.showDialog();
        }
    }
}
