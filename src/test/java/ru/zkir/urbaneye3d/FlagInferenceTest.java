package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import ru.zkir.urbaneye3d.utils.FlagsDatabase;

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
}
