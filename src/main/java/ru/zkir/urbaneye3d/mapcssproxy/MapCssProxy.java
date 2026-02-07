package ru.zkir.urbaneye3d.mapcssproxy;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.mappaint.RenderingHelper;
import org.openstreetmap.josm.io.IllegalDataException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public class MapCssProxy
{

    public BufferedImage render(DataSet dataSet, Bounds bounds, double scale, List<RenderingHelper.StyleData> argStyles) throws IOException, IllegalDataException {
        RenderingHelper rh = new RenderingHelper(dataSet, bounds, scale, argStyles);
        BufferedImage image = rh.render();
        return  image;

    }
}
