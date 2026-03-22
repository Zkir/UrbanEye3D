package ru.zkir.urbaneye3d.utils;

import com.jogamp.opengl.util.packrect.Rect;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * A utility to visualize the result of a UvPacker operation as a PNG image.
 */
public class PackingVisualizer {

    /**
     * Renders the packed rectangles to a PNG file.
     *
     * @param packer The successfully run UvPacker instance.
     * @param filePath The path to save the output PNG file.
     * @throws IOException If an error occurs during file writing.
     */
    public static void saveAsPng(UvPacker packer, String filePath) throws IOException {
        int size = packer.getOptimalSize();
        Map<Object, Rect> packedRects = packer.getPackedRects();

        // Use a small size for empty packers to avoid creating a 0x0 image
        int imageSize = Math.max(size, 1);
        BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            // 1. Fill background with white
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, imageSize, imageSize);

            // 2. Draw each rectangle and its label
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));

            for (Rect r : packedRects.values()) {
                // Fill the rectangle with light gray
                g.setColor(Color.LIGHT_GRAY);
                g.fillRect(r.x(), r.y(), r.w(), r.h());

                // Set color to black for the border and text
                g.setColor(Color.BLACK);

                // Draw the rectangle outline
                g.drawRect(r.x(), r.y(), r.w(), r.h());

                // Prepare and draw the label
                String label = String.format("%s (%dx%d)", r.getUserData(), r.w(), r.h());
                // Position label inside the rectangle
                g.drawString(label, r.x() + 5, r.y() + 15);
            }
        } finally {
            // 3. Dispose of the graphics context
            g.dispose();
        }

        // 4. Write the image to a file
        File outputFile = new File(filePath);
        // Ensure the parent directory exists
        outputFile.getParentFile().mkdirs();
        ImageIO.write(image, "png", outputFile);
        System.out.println("Packing visualization saved to: " + outputFile.getAbsolutePath());
    }
}
