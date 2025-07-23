package ru.zkir.josm.plugins.z3dviewer;

import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;

/**
 * This is the main class for the 3D Viewer plugin.
 */
public class Z3dViewerPlugin extends Plugin {

    private Z3dViewerDialog dialog;

    public Z3dViewerPlugin(PluginInformation info) {
        super(info);
        
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (newFrame != null) {
            dialog = new Z3dViewerDialog(this);
            newFrame.addToggleDialog(dialog);
        }
    }
}
