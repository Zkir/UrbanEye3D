package ru.zkir.urbaneye3d.utils;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OsmDataWasher {
    /** Unlike F4, we inherit only some keys from building to parts, not all */
    final static List<String> inheritableKeys = Arrays.asList("building:colour", "building:material", "roof:colour", "roof:material");

    public  static Double parseDirection(String direction) {
        if (direction == null || direction.isEmpty()) {
            return Double.NaN; // Return NaN if direction is not specified
        }
        try {
            return Double.parseDouble(direction);
        } catch (NumberFormatException e) {
            // Handle cardinal directions (N, S, E, W, etc.)
            switch (direction.toUpperCase()) {
                case "N":   return   0.0;
                case "NNE": return  22.5;
                case "NE":  return  45.0;
                case "ENE": return  67.5;
                case "E":   return  90.0;
                case "ESE": return 112.5;
                case "SE":  return 135.0;
                case "SSE": return 157.5;
                case "S":   return 180.0;
                case "SSW": return 202.5;
                case "SW":  return 225.0;
                case "WSW": return 247.5;
                case "W":   return 270.0;
                case "WNW": return 292.5;
                case "NW":  return 315.0;
                case "NNW": return 337.5;
                default:    return Double.NaN;
            }
        }
    }

    public static Color parseColor(String color, Color default_color){
        Color rgb_color = ColorUtils.parseColor(color);
        if (rgb_color == null) {
            rgb_color = default_color;
        }
        return rgb_color;
    }

    @NotNull
    public static String getTagStr(String key, OsmPrimitive primitive, String defaultValue ){
        return getTagStr(key, primitive.getInterestingTags(), defaultValue);
    }

    @NotNull
    public  static String getTagStr(String key, Map<String, String> primitive, String defaultValue ){
        String value = primitive.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    @NotNull
    public static String getTagStr(String key, Map<String, String> primitive, Map<String, String> parent ){

        String value=primitive.get(key);
        if ((value==null) && parent!=null && inheritableKeys.contains(key)){
            value=parent.get(key);
        }

        if (value==null){
            value="";
        }
        return value;
    }

    @NotNull
    public static Double getTagD(String key, OsmPrimitive primitive, double defaultValue) {
        return getTagD(key, primitive.getInterestingTags(), defaultValue);
    }

    @NotNull
    public static Double getTagD(String key, Map<String, String> primitive, double defaultValue) {
        String value = primitive.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.split(" ")[0]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    //we need to get a floating point value from an osm tag
    // if tag is missing or cannot be parsed, the return value is null,
    // to let it possible to fallback to defaults.
    public static Double getTagD(String key, Map<String, String> primitive, Map<String, String> parent ){
        Double result;
        String tag_str = getTagStr(key, primitive, parent);

        if (tag_str.isEmpty()){
            return null;
        }

        try {
            result = Double.parseDouble(tag_str.split(" ")[0]);
        } catch (NumberFormatException e) {
            result = null;
        }
        return result;

    }
}
