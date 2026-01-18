package ru.zkir.urbaneye3d.josmactions;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Shortcut;
import ru.zkir.urbaneye3d.DialogWindow3D;
import ru.zkir.urbaneye3d.Renderer3D;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static org.openstreetmap.josm.tools.I18n.tr;

public class SwitchRenderEngineAction extends JosmAction {

    private final DialogWindow3D dialogWindow3D;

    public SwitchRenderEngineAction(DialogWindow3D dialogWindow3D) {
        super(tr("Switch Render Engine"), "urbaneye3d", tr("Switches between Urban Eye and Osm2World render engines"),
              Shortcut.registerShortcut("view:switchrenderengine", tr("View: Switch Render Engine"),
                                       KeyEvent.VK_F2, Shortcut.DIRECT), true);
        this.dialogWindow3D = dialogWindow3D;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String currentEngine = Config.getPref().get("urbaneye3d.rendering.engine", "Urban Eye");
        String newEngine;

        if ("Urban Eye".equals(currentEngine)) {
            newEngine = "Osm2World";
        } else {
            newEngine = "Urban Eye";
        }

        Config.getPref().put("urbaneye3d.rendering.engine", newEngine);

        // Force a redraw of the 3D view to apply changes immediately
        if (dialogWindow3D != null) {
            dialogWindow3D.updateData();
        }
    }
}
