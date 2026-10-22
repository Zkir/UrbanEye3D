package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.zkir.urbaneye3d.utils.OsmDataWasher;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

public class OsmDataWasherTest {

    @Test
    public void testParseDimensionalValueMetric() {
        // Meters
        assertEquals(10.0, OsmDataWasher.parseDimensionalValue("height", "10"), 1e-6);
        assertEquals(10.0, OsmDataWasher.parseDimensionalValue("height", "10m"), 1e-6);
        assertEquals(11.5, OsmDataWasher.parseDimensionalValue("height", "11.5 m"), 1e-6);
        assertEquals(10.5, OsmDataWasher.parseDimensionalValue("height", "10.5 meters"), 1e-6);

        // Centimeters
        assertEquals(0.5, OsmDataWasher.parseDimensionalValue("height", "50cm"), 1e-6);
        assertEquals(0.5, OsmDataWasher.parseDimensionalValue("height", "50 cm"), 1e-6);

        // Millimeters
        assertEquals(0.3, OsmDataWasher.parseDimensionalValue("height", "300mm"), 1e-6);
        assertEquals(0.3, OsmDataWasher.parseDimensionalValue("height", "300 mm"), 1e-6);
    }

    @Test
    public void testParseDimensionalValueImperial() {
        // Feet
        assertEquals(3.048, OsmDataWasher.parseDimensionalValue("height", "10ft"), 1e-6);
        assertEquals(3.048, OsmDataWasher.parseDimensionalValue("height", "10'"), 1e-6);
        assertEquals(3.048, OsmDataWasher.parseDimensionalValue("height", "10 '"), 1e-6);
        assertEquals(1.524, OsmDataWasher.parseDimensionalValue("height", "5 feet"), 1e-6);

        // Inches
        assertEquals(0.1524, OsmDataWasher.parseDimensionalValue("height", "6in"), 1e-6);
        assertEquals(0.1524, OsmDataWasher.parseDimensionalValue("height", "6\""), 1e-6);
        assertEquals(0.1524, OsmDataWasher.parseDimensionalValue("height", "6 \""), 1e-6);
        assertEquals(0.1524, OsmDataWasher.parseDimensionalValue("height", "6 inches"), 1e-6);

        // Composite Feet & Inches
        // 10' 5" = 10 * 0.3048 + 5 * 0.0254 = 3.048 + 0.127 = 3.175
        assertEquals(3.175, OsmDataWasher.parseDimensionalValue("height", "10'5\""), 1e-6);
        assertEquals(3.175, OsmDataWasher.parseDimensionalValue("height", "10' 5\""), 1e-6);
        assertEquals(3.175, OsmDataWasher.parseDimensionalValue("height", "10ft 5in"), 1e-6);
    }

    @Test
    public void testParseDimensionalValueDiameter() {
        // Unitless diameter defaults to millimeters (divided by 1000)
        assertEquals(0.3, OsmDataWasher.parseDimensionalValue("diameter", "300"), 1e-6);
        assertEquals(0.3, OsmDataWasher.parseDimensionalValue("diameter", "300mm"), 1e-6);

        // Explicit diameter units convert correctly without extra mm scaling
        assertEquals(2.0, OsmDataWasher.parseDimensionalValue("diameter", "2m"), 1e-6);
        assertEquals(0.3048, OsmDataWasher.parseDimensionalValue("diameter", "1ft"), 1e-6);
    }

    @Test
    public void testGetTagDWithUnits() {
        Map<String, String> tags = new HashMap<>();
        tags.put("height", "12ft");
        tags.put("diameter", "600");

        assertEquals(3.6576, OsmDataWasher.getTagD("height", tags, 0.0), 1e-6);
        assertEquals(0.6, OsmDataWasher.getTagD("diameter", tags, 0.0), 1e-6);
    }
}
