package ru.zkir.urbaneye3d;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JLabel;
import java.awt.GridBagConstraints;
import java.awt.Component;

import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane.PreferencePanel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import javax.swing.ImageIcon;

public class UrbanEye3dPreferences implements TabPreferenceSetting {

    private JCheckBox wireframeCheckBox;
    private JCheckBox fakeAoCheckBox;
    private JCheckBox downloadIncompleteMultipolygonsCheckBox;
    private JCheckBox showStatsCheckBox;
    private JCheckBox useSatelliteCheckBox;
    private JSlider forestDensitySlider;

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        PreferencePanel panel = gui.createPreferenceTab(this, false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1; // Start from row 1, as row 0 is used by the header
        gbc.weightx = 1.0;
        gbc.weighty = 0.0; // Explicitly set weighty to 0 for the checkbox row
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        wireframeCheckBox = new JCheckBox(tr("Enable wireframe rendering mode"));
        wireframeCheckBox.setSelected(Config.getPref().getBoolean("urbaneye3d.wireframe.enabled", false));
        wireframeCheckBox.setToolTipText(tr("If checked, buildings will be rendered as outlines instead of solid polygons."));
        panel.add(wireframeCheckBox, gbc);

        gbc.gridy = 2;
        fakeAoCheckBox = new JCheckBox(tr("Enable Ambient Occlusion Mode"));
        fakeAoCheckBox.setSelected(Config.getPref().getBoolean("urbaneye3d.fakeao.enabled", true));
        fakeAoCheckBox.setToolTipText(tr("If checked, a fake ambient occlusion effect will be applied."));
        panel.add(fakeAoCheckBox, gbc);

        gbc.gridy = 3;
        downloadIncompleteMultipolygonsCheckBox = new JCheckBox(tr("Automatically download incomplete multipolygons"));
        downloadIncompleteMultipolygonsCheckBox.setSelected(Config.getPref().getBoolean("urbaneye3d.download-incomplete.enabled", false));
        downloadIncompleteMultipolygonsCheckBox.setToolTipText(tr("If checked, the plugin will attempt to download missing members of multipolygons."));
        panel.add(downloadIncompleteMultipolygonsCheckBox, gbc);

        gbc.gridy = 4;
        showStatsCheckBox = new JCheckBox(tr("Show scene statistics"));
        showStatsCheckBox.setSelected(Config.getPref().getBoolean("urbaneye3d.stats.enabled", false));
        showStatsCheckBox.setToolTipText(tr("If checked, the number of objects, faces and frame time will be displayed in the top-left corner."));
        panel.add(showStatsCheckBox, gbc);

        gbc.gridy = 5;
        useSatelliteCheckBox = new JCheckBox(tr("Use satellite imagery for ground plane"));
        useSatelliteCheckBox.setSelected(Config.getPref().getBoolean("urbaneye3d.ground-plane.use-satellite", true));
        useSatelliteCheckBox.setToolTipText(tr("If checked, active satellite imagery will be used in 3D ground plane when available. If unchecked, plugin's own 2D style will be used to render flat objects."));
        panel.add(useSatelliteCheckBox, gbc);

        gbc.gridy = 6;
        panel.add(new JLabel(tr("Forest density")), gbc);
        gbc.gridy = 7;
        forestDensitySlider = new JSlider(0, 100);
        forestDensitySlider.setValue(Config.getPref().getInt("urbaneye3d.forest-density", 50));
        forestDensitySlider.setToolTipText(tr("Adjust the number of trees generated in forest polygons. This setting affects performance."));
        panel.add(forestDensitySlider, gbc);
        gbc.gridy = 7; 
        gbc.weighty = 1.0; // This component takes all remaining vertical space
        gbc.fill = GridBagConstraints.BOTH; // Fill both horizontally and vertically
        panel.add(new JPanel(), gbc); // Add an empty JPanel as glue
    }

    @Override
    public boolean ok() {
        if (wireframeCheckBox != null) {
            Config.getPref().putBoolean("urbaneye3d.wireframe.enabled", wireframeCheckBox.isSelected());
        }
        if (fakeAoCheckBox != null) {
            Config.getPref().putBoolean("urbaneye3d.fakeao.enabled", fakeAoCheckBox.isSelected());
        }
        if (downloadIncompleteMultipolygonsCheckBox != null) {
            Config.getPref().putBoolean("urbaneye3d.download-incomplete.enabled", downloadIncompleteMultipolygonsCheckBox.isSelected());
        }
        if (showStatsCheckBox != null) {
            Config.getPref().putBoolean("urbaneye3d.stats.enabled", showStatsCheckBox.isSelected());
		}
        if (useSatelliteCheckBox != null) {
            Config.getPref().putBoolean("urbaneye3d.ground-plane.use-satellite", useSatelliteCheckBox.isSelected());
        }
        if (forestDensitySlider != null) {
            Config.getPref().putInt("urbaneye3d.forest-density", forestDensitySlider.getValue());
        }
        // Force a redraw of the 3D view to apply changes immediately
        DialogWindow3D dialog = UrbanEye3dPlugin.get3DWindow();
        if (dialog != null) {
            dialog.requestSceneUpdate(null, null);
        }
        return false; // No restart required
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    @Override
    public String getIconName() {
        return "urbaneye3d.svg"; //path is not necessary here, JOSM picks it automatically.
    }

    @Override
    public ImageIcon getIcon(ImageProvider.ImageSizes size) {
        return TabPreferenceSetting.super.getIcon(size);
    }

    @Override
    public String getTitle() {
        return tr("Urban Eye 3D");
    }

    @Override
    public String getTooltip() {
        return tr("Settings for the Urban Eye 3D plugin");
    }

    @Override
    public String getDescription() {
        return tr("Configure rendering options for the 3D Viewer.");
    }

    @Override
    public void addSubTab(SubPreferenceSetting sub, String title, Component component) {
        // Not used for this simple setting
    }

    @Override
    public void addSubTab(SubPreferenceSetting sub, String title, Component component, String tip) {
        // Not used for this simple setting
    }

    @Override
    public void registerSubTab(SubPreferenceSetting sub, Component component) {
        // Not used for this simple setting
    }

    @Override
    public Component getSubTab(SubPreferenceSetting sub) {
        return null; // Not used for this simple setting
    }

    @Override
    public Class<? extends SubPreferenceSetting> getSelectedSubTab() {
        return null; // Not used for this simple setting
    }

    @Override
    public boolean selectSubTab(SubPreferenceSetting subPref) {
        return false; // Not used for this simple setting
    }

    @Override
    public String getHelpContext() {
        return null; // No specific help context for now
    }
}
