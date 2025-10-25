package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import ru.zkir.urbaneye3d.josmactions.OpenF4MapAction;
import ru.zkir.urbaneye3d.validator.SpatialConsistencyChecks;
import ru.zkir.urbaneye3d.validator.TagChecks;

/**
 * This is the main class for the 3D Viewer plugin.
 */
public class UrbanEye3dPlugin extends Plugin {

    private static DialogWindow3D dialog;
    private static boolean f4mapMenuInitialized = false;

    public UrbanEye3dPlugin(PluginInformation info) {
        super(info);
        OsmValidator.addTest(SpatialConsistencyChecks.class);
        OsmValidator.addTest(TagChecks.class);
    }
    
    public static DialogWindow3D get3DWindow() {
        return dialog;
    }

    public static void debugMsg(String s) {
        System.out.println("[" + java.time.Instant.now() + "][UrbanEye3D] "+s);
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

            if (!f4mapMenuInitialized) {
                // Add "Open in F4Map" to the "View" menu
                OpenF4MapAction openF4MapAction = new OpenF4MapAction();
                org.openstreetmap.josm.gui.MainApplication.getMenu().viewMenu.add(openF4MapAction);
                f4mapMenuInitialized = true;
            }
        }
    }
}
