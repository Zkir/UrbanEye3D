package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;

/**
 * This is the main class for the 3D Viewer plugin.
 */
public class UrbanEye3dPlugin extends Plugin {

    private DialogWindow3D dialog;

    public UrbanEye3dPlugin(PluginInformation info) {
        super(info);
        
    }

    public static void debugMsg(String s) {
        System.out.println("[UrbanEye3D] "+s);
    }

    @Override
    public PreferenceSetting getPreferenceSetting() {
        return new UrbanEye3dPreferences();
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (newFrame != null) {
            dialog = new DialogWindow3D(this);
            newFrame.addToggleDialog(dialog);
        }
    }
}
