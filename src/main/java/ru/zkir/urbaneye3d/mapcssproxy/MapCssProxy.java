package ru.zkir.urbaneye3d.mapcssproxy;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.mappaint.RenderingHelper;
import org.openstreetmap.josm.io.IllegalDataException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Since we do not have our own MapCSS engine, and are not likely to have one in a near future,
 *  we have to use one, built in JOSM. It's buggy in unusual ways, but better than nothing.  */
public class MapCssProxy
{

    public BufferedImage render(DataSet dataSet, Bounds bounds, double scale, ArrayList<String> styleUrls) throws IOException, IllegalDataException {

        // 1. Invoke JOSM MapCSS rendering engine with our own style
        List<RenderingHelper.StyleData> argStyles = new ArrayList<>();

        for (var styleUrl:styleUrls) {
            var argCurrentStyle = new RenderingHelper.StyleData();
            argCurrentStyle.styleUrl = styleUrl;
            argStyles.add(argCurrentStyle);
        }

        RenderingHelper rh = new RenderingHelper(dataSet, bounds, scale, argStyles);
        BufferedImage image = rh.render();

        // 2. We have some own logic, which cannot be handled by MapCSS currently.
        //    so we have to do that ourselves.
        if (image != null) {
            GroundDecorations.drawGroundDecorations(image, dataSet, bounds, scale);
        }

        return  image;

    }
}
