package ru.zkir.urbaneye3d;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.spi.preferences.Config;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class UrbanEye3dPreferences extends DefaultTabPreferenceSetting {

    private JCheckBox wireframeCheckBox;
    private JComboBox<String> renderingEngineComboBox;
    private JComboBox<String> lodComboBox;
    private final String[] renderingEngines = {tr("Urban Eye"), tr("Osm2World")};
    private final String[] lodLevels = {"1", "2", "3", "4"};
    private final JPanel panel = new JPanel(new GridBagLayout());
    private JCheckBox fakeAoCheckBox;

    public UrbanEye3dPreferences() {
        super("urbaneye3d.svg", tr("Urban Eye 3D"), tr("Settings for the Urban Eye 3D plugin"));
        initComponents();
        loadPreferences();
    }

    private void initComponents() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Row 0: Rendering Engine
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(tr("Rendering Engine:")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        renderingEngineComboBox = new JComboBox<>(renderingEngines);
        renderingEngineComboBox.setToolTipText(tr("Select the engine to generate 3D models."));
        panel.add(renderingEngineComboBox, gbc);

        // Row 1: LOD Level
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(tr("LOD (for Osm2World):")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        lodComboBox = new JComboBox<>(lodLevels);
        lodComboBox.setToolTipText(tr("Select the Level of Detail for Osm2World rendering."));
        panel.add(lodComboBox, gbc);


        // Row 2: Wireframe Checkbox
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        wireframeCheckBox = new JCheckBox(tr("Enable wireframe rendering mode"));
        wireframeCheckBox.setToolTipText(tr("If checked, buildings will be rendered as outlines instead of solid polygons."));
        panel.add(wireframeCheckBox, gbc);
		
		//Row 3: Fake AO Checkbox
		gbc.gridy++;
		gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
		fakeAoCheckBox = new JCheckBox(tr("Enable Ambient Occlusion Mode"));
        fakeAoCheckBox.setToolTipText(tr("If checked, a fake ambient occlusion effect will be applied."));
        panel.add(fakeAoCheckBox, gbc);


        // Add vertical glue to push everything to the top
        gbc.gridy++; // Next row
        gbc.weighty = 1.0; // This component takes all remaining vertical space
        panel.add(Box.createVerticalGlue(), gbc);

    }

    public void loadPreferences() {
        wireframeCheckBox.setSelected(Config.getPref().getBoolean("urbaneye3d.wireframe.enabled", false));
        String currentEngine = Config.getPref().get("urbaneye3d.rendering.engine", "Urban Eye");
        renderingEngineComboBox.setSelectedItem("Osm2World".equals(currentEngine) ? tr("Osm2World") : tr("Urban Eye"));
        lodComboBox.setSelectedItem(Config.getPref().get("urbaneye3d.osm2world.lod", "3"));
		fakeAoCheckBox.setSelected(Config.getPref().getBoolean("urbaneye3d.fakeao.enabled", true));
    }

    public boolean savePreferences() {
        if (wireframeCheckBox != null) {
            Config.getPref().putBoolean("urbaneye3d.wireframe.enabled", wireframeCheckBox.isSelected());
        }
        if (renderingEngineComboBox != null) {
            String selectedValue = (String) renderingEngineComboBox.getSelectedItem();
            String keyToSave = tr("Osm2World").equals(selectedValue) ? "Osm2World" : "Urban Eye";
            Config.getPref().put("urbaneye3d.rendering.engine", keyToSave);
        }
        if (lodComboBox != null) {
            Config.getPref().put("urbaneye3d.osm2world.lod", (String) lodComboBox.getSelectedItem());
        }
		if (fakeAoCheckBox != null) {
            Config.getPref().putBoolean("urbaneye3d.fakeao.enabled", fakeAoCheckBox.isSelected());
        }
        return false; // No restart required
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        createPreferenceTabWithScrollPane(gui, panel);
    }

    @Override
    public boolean ok() {
        savePreferences();
        
        // Force a redraw of the 3D view to apply changes immediately
        DialogWindow3D dialog = UrbanEye3dPlugin.get3DWindow();
        if (dialog != null) {
            dialog.updateData();
        }
        return false; // No restart required
    }

    @Override
    public boolean isExpert() {
        return false;
    }
}
