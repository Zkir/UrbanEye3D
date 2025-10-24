package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.tools.Shortcut;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static org.openstreetmap.josm.tools.I18n.tr;

public class ResetCameraAction extends JosmAction {

    private final Renderer3D renderer3D;

    public ResetCameraAction(Renderer3D renderer3D) {
                super(tr("Reset Camera View"), "reset_camera", tr("Reset camera view to default position"),
                        Shortcut.registerShortcut("urbaneye3d:resetcamera", tr("UrbanEye3D: {0}", tr("Reset Camera View")),
                                KeyEvent.VK_N, Shortcut.CTRL_SHIFT), true);
        this.renderer3D = renderer3D;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        renderer3D.resetCameraToNorth();
        renderer3D.repaint();
    }

    @Override
    public void updateEnabledState() {
        setEnabled(true);
    }
}
