package ru.zkir.urbaneye3d;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
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

        // Add vertical glue to push content to the top
        gbc.gridy = 2; // Next row
        gbc.weighty = 1.0; // This component takes all remaining vertical space
        gbc.fill = GridBagConstraints.BOTH; // Fill both horizontally and vertically
        panel.add(new JPanel(), gbc); // Add an empty JPanel as glue
    }

    @Override
    public boolean ok() {
        if (wireframeCheckBox != null) {
            Config.getPref().putBoolean("urbaneye3d.wireframe.enabled", wireframeCheckBox.isSelected());
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
