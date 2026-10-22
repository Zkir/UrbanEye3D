package ru.zkir.urbaneye3d.utils;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.Materials;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagStr;

public class OsmDataWasher {
    /** Unlike F4, we inherit only some keys from building to parts, not all */
    final static List<String> inheritableKeys = Arrays.asList("building:colour", "building:material", "roof:colour", "roof:material");

    final static Pattern pComposite = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:ft|foot|feet|')\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:in|inch|inches|''|\")?\\s*$");
    final static Pattern pSingle = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*([a-zäöü]+|['\"]+|['\"])?\\s*$");

    /**
     * Parses direction tag
     * @param direction direction tag string value
     * @return direction in degrees counterclockwise or null if not set
     */
    public  static Double parseDirection(String direction) {
        if (direction == null || direction.isEmpty()) {
            return null; // Return null if direction is not specified
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
                default:    return null;
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

    public static Double parseDimensionalValue(String key, String tagStr) {
        if (tagStr == null || tagStr.isEmpty()) {
            return null;
        }
        tagStr = tagStr.trim().toLowerCase();
        boolean isDiameter = "diameter".equals(key);

        // Try composite feet & inches: e.g., 10' 5" or 10 ft 5 in
        Matcher mComposite = pComposite.matcher(tagStr);
        if (mComposite.matches()) {
            try {
                double feet = Double.parseDouble(mComposite.group(1));
                double inches = Double.parseDouble(mComposite.group(2));
                return feet * 0.3048 + inches * 0.0254;
            } catch (NumberFormatException e) {
                // fall through
            }
        }

        // Single value with optional unit
        Matcher mSingle = pSingle.matcher(tagStr);
        if (mSingle.matches()) {
            try {
                double val = Double.parseDouble(mSingle.group(1));
                String unit = mSingle.group(2);
                if (unit == null || unit.isEmpty()) {
                    if (isDiameter) {
                        return val / 1000.0; // unitless diameter is in millimeters
                    } else {
                        return val; // unitless height/width/etc. is in meters
                    }
                }
                unit = unit.toLowerCase();
                switch (unit) {
                    case "m":
                    case "meter":
                    case "metres":
                    case "meters":
                        return val;
                    case "cm":
                        return val * 0.01;
                    case "mm":
                        return val * 0.001;
                    case "ft":
                    case "foot":
                    case "feet":
                    case "'":
                        return val * 0.3048;
                    case "in":
                    case "inch":
                    case "inches":
                    case "''":
                    case "\"":
                        return val * 0.0254;
                    default:
                        if (isDiameter) {
                            return val / 1000.0;
                        } else {
                            return val;
                        }
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // Fallback
        try {
            double val = Double.parseDouble(tagStr.split(" ")[0]);
            if (isDiameter) {
                return val / 1000.0;
            } else {
                return val;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NotNull
    public static Double getTagD(String key, OsmPrimitive primitive, double defaultValue) {
        return getTagD(key, primitive.getInterestingTags(), defaultValue);
    }

    @NotNull
    public static Double getTagD(String key, Map<String, String> tags, double defaultValue) {
        String value = tags.get(key);
        if (value == null) {
            return defaultValue;
        }
        Double parsed = parseDimensionalValue(key, value);
        return parsed != null ? parsed : defaultValue;
    }

    //we need to get a floating point value from an osm tag
    // if tag is missing or cannot be parsed, the return value is null,
    // to let it possible to fallback to defaults.
    public static Double getTagD(String key, Map<String, String> primitive, Map<String, String> parent ){
        String tag_str = getTagStr(key, primitive, parent);

        if (tag_str.isEmpty()){
            return null;
        }

        return parseDimensionalValue(key, tag_str);
    }
    @NotNull
    public static Color getTagC(String key, OsmPrimitive primitive, String defaultValue) {
        Color default_color = ColorUtils.parseColor(defaultValue);
        String color_str = getFirstValue(getTagStr(key, primitive, defaultValue));
        Color color = ColorUtils.parseColor(color_str);

        if (color==null){
            color=default_color;
        }

        return color;
    }

    public static String getFirstValue(String tag){
        if (tag.contains(";")) {
            tag = tag.substring(0, tag.indexOf(';')).trim();
        }
        return tag;
    }

    public static Color getColorByColourAndMaterial(OsmPrimitive primitive) {
        Color main_colour = null; // we start from an unspecified color. Note, that there is NO default value.
        String main_color_str = getFirstValue(getTagStr("colour", primitive, ""));
        if (!main_color_str.isBlank()) {
            main_colour = ColorUtils.parseColor(main_color_str);
        }
        if (main_colour == null){
            var material = Materials.fromString(getTagStr("material", primitive, ""));
            // get default value if material is specified
            if (material!=null){
                main_colour = ColorUtils.parseColor(material.getColor());
            }
        }
        return main_colour;

    }

    /**
     * Gets color value value from the "colour" and "material" tags
     * The trick is: first of all explicit color, then color from material, and only then default value.
     */
    public static Color getColorByColourAndMaterial(OsmPrimitive primitive, String defaultColor) {
        var color = getColorByColourAndMaterial(primitive);
        if (color == null) {
            color = ColorUtils.parseColor(defaultColor);
        }
        return color;

    }
}
