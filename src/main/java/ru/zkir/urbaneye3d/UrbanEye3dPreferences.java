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
import java.util.Hashtable;

public class UrbanEye3dPreferences implements TabPreferenceSetting {

    private JCheckBox wireframeCheckBox;
    private JCheckBox fakeAoCheckBox;
    private JCheckBox downloadIncompleteMultipolygonsCheckBox;
    private JCheckBox showStatsCheckBox;
    private JCheckBox useSatelliteCheckBox;
    private JCheckBox enableAnimationCheckBox;
    private JSlider forestDensitySlider;
    private JSlider msaaSlider;

    private static final int[] MSAA_VALUES = {0, 2, 4, 8};

    private int getMsaaIndex(int value) {
        for (int i = 0; i < MSAA_VALUES.length; i++) {
            if (MSAA_VALUES[i] == value) return i;
        }
        return 2; // Default to 4 samples (index 2)
    }

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

        gbc.gridy = 8;
        panel.add(new JLabel(tr("Anti-aliasing (MSAA) samples")), gbc);
        gbc.gridy = 9;
        msaaSlider = new JSlider(0, 3);
        int currentSamples = Config.getPref().getInt("urbaneye3d.msaa.samples", 4);
        msaaSlider.setValue(getMsaaIndex(currentSamples));
        msaaSlider.setMajorTickSpacing(1);
        msaaSlider.setPaintTicks(true);
        msaaSlider.setSnapToTicks(true);
        
        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        for (int i = 0; i < MSAA_VALUES.length; i++) {
            labelTable.put(i, new JLabel(String.valueOf(MSAA_VALUES[i])));
        }
        msaaSlider.setLabelTable(labelTable);
        msaaSlider.setPaintLabels(true);
        
        msaaSlider.setToolTipText(tr("Number of MSAA samples for smoother edges. 0 means disabled. Higher values improve quality but may reduce performance. Changes require JOSM restart."));
        panel.add(msaaSlider, gbc);

        gbc.gridy = 10;
        enableAnimationCheckBox = new JCheckBox(tr("Enable animation (experimental)"));
        enableAnimationCheckBox.setSelected(Config.getPref().getBoolean("urbaneye3d.animation.enabled", false));
        enableAnimationCheckBox.setToolTipText(tr("If checked, dynamic objects like smoke and fountains will be animated. This improves realism but increases CPU/GPU usage."));
        panel.add(enableAnimationCheckBox, gbc);

        gbc.gridy = 11; 
        gbc.weighty = 1.0; // This component takes all remaining vertical space
        gbc.fill = GridBagConstraints.BOTH; // Fill both horizontally and vertically
        panel.add(new JPanel(), gbc); // Add an empty JPanel as glue
    }

    @Override
    public boolean ok() {
        boolean restartRequired = false;
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
        if (msaaSlider != null) {
            int oldMsaa = Config.getPref().getInt("urbaneye3d.msaa.samples", 4);
            int newMsaa = MSAA_VALUES[msaaSlider.getValue()];
            if (oldMsaa != newMsaa) {
                Config.getPref().putInt("urbaneye3d.msaa.samples", newMsaa);
                restartRequired = true;
            }
        }
        if (enableAnimationCheckBox != null) {
            Config.getPref().putBoolean("urbaneye3d.animation.enabled", enableAnimationCheckBox.isSelected());
        }
        // Force a redraw of the 3D view to apply changes immediately
        DialogWindow3D dialog = UrbanEye3dPlugin.get3DWindow();
        if (dialog != null) {
            dialog.updateAnimationTimer();
            dialog.requestSceneUpdate(null);
        }
        return restartRequired;
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
