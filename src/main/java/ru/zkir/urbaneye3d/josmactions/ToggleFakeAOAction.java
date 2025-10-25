package ru.zkir.urbaneye3d.josmactions;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.tools.Shortcut;
import ru.zkir.urbaneye3d.Renderer3D;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import static org.openstreetmap.josm.tools.I18n.tr;

public class ToggleFakeAOAction extends JosmAction {
    private final Renderer3D renderer3D;

    public ToggleFakeAOAction(Renderer3D renderer3D) {
        super(tr("Fake AO"), "wireframe", tr("Toggle Fake AO mode"),
                Shortcut.registerShortcut("urbaneye3d:fakeao", tr("UrbanEye3D: {0}", tr("Toggle Fake AO")),
                        KeyEvent.VK_Z , Shortcut.SHIFT), true);
        this.renderer3D = renderer3D;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        renderer3D.toggleFakeAO();
        renderer3D.repaint();
    }

    @Override
    public void updateEnabledState() {
        setEnabled(true);
    }

}

