package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import ru.zkir.urbaneye3d.utils.TreeSpeciesDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreeSpeciesNormalizationTest {

    @Test
    void testNormalization() {
        Assertions.assertEquals("tilia cordata", TreeSpeciesDatabase.normalizeSpecies("Tilia cordata green spire"));
        assertEquals("platanus × acerifolia", TreeSpeciesDatabase.normalizeSpecies("Platanus x acerifolia"));
        assertEquals("platanus × hispanica", TreeSpeciesDatabase.normalizeSpecies("Platanus ×hispanica"));
        assertEquals("tilia euchlora", TreeSpeciesDatabase.normalizeSpecies("Tilia euchlora"));
        assertEquals("× crataegomespilus dardarii", TreeSpeciesDatabase.normalizeSpecies("× Crataegomespilus dardarii Bronvaux"));
        assertEquals("taxus baccata", TreeSpeciesDatabase.normalizeSpecies("Taxus baccata"));
    }

    @Test
    void testFormatting() {
        assertEquals("Tilia cordata", TreeSpeciesDatabase.formatSpecies("Tilia cordata green spire"));
        assertEquals("Platanus × acerifolia", TreeSpeciesDatabase.formatSpecies("Platanus x acerifolia"));
        assertEquals("× Crataegomespilus dardarii", TreeSpeciesDatabase.formatSpecies("× crataegomespilus dardarii"));
        assertEquals("Tilia × europaea", TreeSpeciesDatabase.formatSpecies("Tilia × europaea 'Pallida'"));
        assertEquals("Aesculus × carnea", TreeSpeciesDatabase.formatSpecies("Aesculus × carnea var. Briotii"));
        assertEquals("Acer griseum × pseudoplatanus", TreeSpeciesDatabase.formatSpecies("Acer griseum × pseudoplatanus"));
        assertEquals("Acer griseum × pseudoplatanus", TreeSpeciesDatabase.formatSpecies("Acer griseum × pseudoplatanus var. Bogus"));

        assertEquals("Ulmus sp.",TreeSpeciesDatabase.formatSpecies("Ulmus 'Sapporo Autumn Gold'"));
        assertEquals("Malus sp.",TreeSpeciesDatabase.formatSpecies("Malus 'Evereste'"));

    }

    @Test
    void testEnrichTagsModifiesSpecies() {
        Map<String, String> tags = new HashMap<>();
        tags.put("species", "Tilia cordata green spire");
        TreeSpeciesDatabase.getInstance().enrichTags(tags, new LatLon(55.0, 35.0), new Random(1));
        assertEquals("Tilia cordata", tags.get("species"));
        assertEquals("broadleaved", tags.get("leaf_type"));
        assertEquals("deciduous", tags.get("leaf_cycle"));
    }

    @Test
    void testSpatialInference() {
        // Test a square where we know palms don't grow (e.g., Moscow region)
        Map<String, String> tags = new HashMap<>();
        tags.put("natural", "tree");
        // No species, no leaf_type
        TreeSpeciesDatabase.getInstance().enrichTags(tags, new LatLon(55.75, 37.61), new Random(42));

        // In Moscow region (+55+035), probability of broadleaved/needleleaved is high, palm is 0
        String leafType = tags.get("leaf_type");
        Assertions.assertTrue("broadleaved".equals(leafType) || "needleleaved".equals(leafType), "Unexpected leaf type for location: " + leafType);
        Assertions.assertNotEquals("palm", leafType);
    }
}
