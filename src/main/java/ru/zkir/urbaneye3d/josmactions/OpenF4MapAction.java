package ru.zkir.urbaneye3d.josmactions;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.tools.OpenBrowser;
import org.openstreetmap.josm.tools.Shortcut;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

import java.awt.event.KeyEvent;

public class OpenF4MapAction extends JosmAction {


    public OpenF4MapAction() {
        super("View in F4Map (web)", "f4_icon.png", "Open current view in F4 Map",
                Shortcut.registerShortcut("urbaneye3d:openf4map", "UrbanEye3D: Open in F4 Map", KeyEvent.VK_F, Shortcut.SHIFT),
                true);
    }

    @Override
    public void actionPerformed(java.awt.event.ActionEvent e) {
        var renderer = UrbanEye3dPlugin.get3DWindow().getRenderer3D();
        if (renderer == null || MainApplication.getMap() == null) {
            return;
        }

        MapView mapView = MainApplication.getMap().mapView;
        if (mapView == null) {
            return;
        }

        EastNorth centerEN = mapView.getCenter();
        LatLon center = mapView.getProjection().eastNorth2latlon(centerEN);

        // Calculate zoom based on the 3D camera distance, not the 2D map scale.
        // This is an empirical formula to map camera distance to a reasonable F4Map zoom level.
        double cam_dist = renderer.getCam_dist();
        double zoom = 25.24 - 1.32 * Math.log(cam_dist);
        // Clamp the zoom to a reasonable range for F4Map.
        zoom = Math.max(16, Math.min(21, zoom));

        // F4Map's theta is the polar angle from the zenith (0=top, 90=horizon).
        // Our camX_angle is the elevation from the horizon (0=horizon, 90=top).
        double theta = 90.0 - renderer.getCamX_angle();
        theta = Math.max(1.0, Math.min(80.0, theta)); // Clamp theta for F4Map

        // F4Map's phi is the azimuth (0=N, 90=E, 180=S, 270=W).
        // Our camY_angle seems to be azimuth with a -90 degree offset (0=E, 90=S, etc.).
        double phi = (renderer.getCamY_angle() + 90.0) % 360.0;
        if (phi < 0) {
            phi += 360.0;
        }

        // Convert from [0, 360] to [-180, 180] for F4Map
        if (phi > 180) {
            phi -= 360;
        }

        String url = String.format(java.util.Locale.US, "https://demo.f4map.com/#lat=%.7f&lon=%.7f&zoom=%.0f&camera.theta=%.3f&camera.phi=%.3f",
                center.lat(),
                center.lon(),
                zoom,
                theta,
                phi);

        OpenBrowser.displayUrl(url);
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(!getLayerManager().getLayers().isEmpty());
    }
}
