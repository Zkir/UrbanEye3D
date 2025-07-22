package ru.zkir.josm.plugins.z3dviewer;

import javax.swing.JPanel;
import java.awt.BorderLayout;

import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * This is the dialog window for the 3D Viewer plugin.
 * It will contain the 3D canvas.
 */
public class Z3dViewerDialog extends ToggleDialog {

    public Z3dViewerDialog() {
        super(tr("3D Viewer"), "up", tr("Open 3D Viewer"), null, 150); // "threedviewer"
        
        JPanel panel = new JPanel(new BorderLayout());
        // Later, we will add our 3D canvas here.
        
        createLayout(panel, false, null);
    }
}
