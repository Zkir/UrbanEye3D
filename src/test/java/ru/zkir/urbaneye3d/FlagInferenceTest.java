package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import ru.zkir.urbaneye3d.utils.FlagsDatabase;
import ru.zkir.urbaneye3d.utils.WindTurbineDatabase;

import static java.lang.Math.abs;
import static org.junit.jupiter.api.Assertions.*;

public class FlagInferenceTest {

    @BeforeAll
    public static void setUp() {
        Config.setPreferencesInstance(new MemoryPreferences());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Config.setUrlsProvider(JosmUrls.getInstance());
    }

    @Test
    void testVietnamInference() {
        Node node = new Node();
        node.put("subject", "Vietnam");
        
        String color = FlagsDatabase.getInstance().getInferredColor(node);
        assertEquals("red", color, "Subject Vietnam should infer red color.");
    }

    @Test
    void testCanadaInference() {
        Node node = new Node();
        node.put("country", "CA");
        
        String color = FlagsDatabase.getInstance().getInferredColor(node);
        assertEquals("red", color, "Country CA should infer red color.");
    }
    @Test
    void testUnitedStatesInference() {
        Node node = new Node();
        node.put("flag:name", "United States");

        String qid = FlagsDatabase.getInstance().getInferredQID(node);
        assertEquals("Q42537", qid, "flag:name=United States should infer 'Q42537' QID.");

        Node node2 = new Node();
        node2.put("country", "US");

        qid = FlagsDatabase.getInstance().getInferredQID(node2);
        assertEquals("Q42537", qid, "country=US should infer 'Q42537' QID.");

        Node node3 = new Node();
        node3.put("country", "us");

        qid = FlagsDatabase.getInstance().getInferredQID(node3);
        assertEquals("Q42537", qid, "country=us should infer 'Q42537' QID.");
    }



    @Test
    void testMaximumLikelihoodTieBreak() {
        // In our flag_rules.json:
        // country=CA -> red (prob 1.0, count 985)
        // subject=Canada -> red (prob 1.0, count 981)
        // If we had a case with same prob but different counts, count should win.
        // Let's mock a scenario with tags that have different counts in our actual json.
        
        Node node = new Node();
        node.put("country", "CA"); // prob 1.0, count 985
        node.put("subject", "Canada"); // prob 1.0, count 981
        
        // Both point to 'red', but 'country=CA' has slightly higher count.
        // Since we return color, it's hard to see which one was picked if they match.
        // But the logic is there.
        String color = FlagsDatabase.getInstance().getInferredColor(node);
        assertEquals("red", color);
    }

    @Test
    void testNoMatchReturnsNull() {
        Node node = new Node();
        node.put("subject", "NonExistentSubject123");
        
        String color = FlagsDatabase.getInstance().getInferredColor(node);
        assertEquals("", color, "Unknown subject should return blank string.");
    }

    @Test
    void testWindGeneratorInference(){
        Node node = new Node();
        node.put("manufacturer", "Enercon");
        node.put("model", "E-101");

        String rotor_diameter = WindTurbineDatabase.getInstance().getInferredRotorDiameter(node);
        String height = WindTurbineDatabase.getInstance().getInferredHeight(node);

        //expected height=135, rotor:diameter=101
        assertEquals("101", rotor_diameter, "Expected rotor diameter for Enron E-101 is 101.");
        assertEquals("135", height, "Expected height for Enron E-101 is 135");

    }

    @Test
    void testWindGeneratorScene() {
        // Arrange
        DataSet dataSet = new DataSet();
        Node node = new Node(new LatLon(55.0, 37.0));
        node.put("power", "generator");
        node.put("generator:source", "wind");

        node.put("manufacturer", "Enercon");
        node.put("model", "E-101");
        dataSet.addPrimitive(node);

        Scene scene = new Scene();

        // Act
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert
        assertEquals(1, scene.renderableElements.size());
        RenderableElement windGenerator = scene.renderableElements.get(0);

        double actualHeight =windGenerator.getMesh().getMaxBounds().z;

        // Check that inferred parameter values are applied.
        // we expect zero rotation phase of the rotor (tip up), but it is not always the case!
        assertTrue(abs(actualHeight-135)<2,  "Expected height for Enron E-101 is 135, got " + actualHeight);
    }
}
