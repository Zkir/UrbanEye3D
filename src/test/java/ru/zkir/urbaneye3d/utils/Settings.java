package ru.zkir.urbaneye3d.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class Settings{
    public static boolean SAVE_TEST_RESULTS_TO_FILE = false;

    public static String prepareTestOutputFolder(String testName) throws IOException {
        String outputFolder = "tests/output/" + testName;
        File folder = new File(outputFolder);
        FileUtils.forceMkdir(folder);
        FileUtils.cleanDirectory(folder);
        return outputFolder;
    }

}
