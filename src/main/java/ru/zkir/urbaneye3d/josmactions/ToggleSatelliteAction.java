package ru.zkir.urbaneye3d.josmactions;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.tools.Shortcut;
import ru.zkir.urbaneye3d.DialogWindow3D;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static org.openstreetmap.josm.tools.I18n.tr;

public class ToggleSatelliteAction extends JosmAction {

    private final DialogWindow3D dialogWindow3D;

    public ToggleSatelliteAction(DialogWindow3D dialogWindow3D) {
        super(tr("Satellite imagery"), "satellite", tr("Toggle satellite imagery on ground plane"),
                Shortcut.registerShortcut("urbaneye3d:satellite", tr("UrbanEye3D: {0}", tr("Toggle Satellite Imagery")),
                        KeyEvent.VK_E, Shortcut.SHIFT), true);
        this.dialogWindow3D = dialogWindow3D;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        dialogWindow3D.toggleSatelliteImagery();
    }

    @Override
    public void updateEnabledState() {
        setEnabled(true);
    }
}
