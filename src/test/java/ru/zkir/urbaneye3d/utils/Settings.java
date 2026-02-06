package ru.zkir.urbaneye3d.utils;

import org.apache.commons.io.FileUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class Settings{
    public static boolean SAVE_TEST_RESULTS_TO_FILE = false;

    public static String prepareTestOutputFolder(String testName) throws IOException {
        String outputFolder = "tests/output/" + testName;
        File folder = new File(outputFolder);
        FileUtils.forceMkdir(folder);
        FileUtils.cleanDirectory(folder);
        return outputFolder;
    }

    public static int countUniqueColors(BufferedImage image) {
        if (image == null) return 0;
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                colors.add(image.getRGB(x, y));
            }
        }
        return colors.size();
    }

}
