package ru.zkir.urbaneye3d.utils;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class FlagsDatabase {
    private final String FLAG_RULES_FILE_NAME = "/data/flag_rules.json";
    private static FlagsDatabase instance;
    private final TagInference flagRules;

    private FlagsDatabase() {
        flagRules = new TagInference(FLAG_RULES_FILE_NAME);
    }

    public static synchronized FlagsDatabase getInstance() {
        if (instance == null) {
            instance = new FlagsDatabase();
        }
        return instance;
    }



    public String getInferredColor(OsmPrimitive primitive) {
        return flagRules.getInferredValue(primitive.getInterestingTags(), "flag:colour");
    }
    public String getInferredQID(OsmPrimitive primitive) {
        return flagRules.getInferredValue(primitive.getInterestingTags(), "flag:wikidata");
    }


    public boolean checkQID(String flagQID){
        String resourcePath = "/textures/flags/" + flagQID + ".png";
        URL svgUrl = FlagsDatabase.class.getResource(resourcePath);
        if (svgUrl == null) {
            return false;
        }
        return true;
    }

    public String getFlagTextureName(String qid) {
        //unlike checkQID, we just need relative path for resources/textures. TextureManager will handle that
        String resourcePath = "flags/" + qid + ".png";

        return resourcePath;
    }

    public double getAspectRatio(String flagQID) {
        //TODO: we should rather extract it from wikidata itself, property P2061 "aspect ratio (W:H)"
        //  those cases should be moved to autotests.
        if (flagQID.equals("Q159741")){ // flag of Nepal is quite unique
            return 1/1.25;
        }
        if (flagQID.equals("Q124020") || //Swiss flag is actually square, not rectangle.
                flagQID.equals("Q79198")) { // flag of the Holy See is also rectangular
            return 1;
        }
        return 1.5; // most flags are either 3:2 or 2:1
    }
}
