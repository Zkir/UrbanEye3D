package ru.zkir.urbaneye3d;

import com.jogamp.opengl.util.packrect.Rect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.zkir.urbaneye3d.utils.PackingVisualizer;
import ru.zkir.urbaneye3d.utils.UvPacker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UvPackerTest {

    private List<Rect> rects;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(Paths.get("target/test-output/uv-packing"));
        // These are the same rectangles from the original example code.
        rects = new ArrayList<>();
        rects.add(new Rect(0, 0, 200, 150, "A"));
        rects.add(new Rect(0, 0, 100, 100, "B"));
        rects.add(new Rect(0, 0, 80, 200, "C"));
        rects.add(new Rect(0, 0, 300, 50, "D"));
        rects.add(new Rect(0, 0, 120, 120, "E"));
        rects.add(new Rect(0, 0, 80, 80, "F"));
    }

    @Test
    void testUvPacker() throws IOException {
        UvPacker packer = new UvPacker(rects);
        Map<Object, Rect> packedRects = packer.getPackedRects();
        //Save as PNG
        PackingVisualizer.saveAsPng(packer, "target/test-output/uv-packing/testUVPacker.png");

        // This is a "golden master" value derived from running the original implementation.
        // It confirms the binary search logic produces the expected result for this specific input.
        assertEquals(370, packer.getOptimalSize(), "Should find the minimal square size.");

        // Check that all rectangles are packed
        assertEquals(rects.size(), packedRects.size(), "All input rectangles should be present in the output.");
        for (Rect r : rects) {
            assertTrue(packedRects.containsKey(r.getUserData()), "Packed rects should contain user data key: " + r.getUserData());
        }

        //Check that all rectangles are within the target square.
        int size = packer.getOptimalSize();
        for (Rect r : packedRects.values()) {
            assertTrue(r.x() >= 0, "X coordinate should be non-negative for " + r.getUserData());
            assertTrue(r.y() >= 0, "Y coordinate should be non-negative for " + r.getUserData());
            assertTrue(r.x() + r.w() <= size, "Right edge should be within bounds for " + r.getUserData());
            assertTrue(r.y() + r.h() <= size, "Bottom edge should be within bounds for " + r.getUserData());
        }

        //Checked that rectangles do not overlap
        List<Rect> packed = new ArrayList<>(packedRects.values());
        for (int i = 0; i < packed.size(); i++) {
            for (int j = i + 1; j < packed.size(); j++) {
                Rect r1 = packed.get(i);
                Rect r2 = packed.get(j);
                assertFalse(intersects(r1, r2),
                        "Rectangles should not overlap: " + r1.getUserData() + " and " + r2.getUserData());
            }
        }
    }

    @Test
    void testWithEmptyList() {
        UvPacker packer = new UvPacker(new ArrayList<>());
        assertEquals(0, packer.getOptimalSize(), "Optimal size for empty list should be 0.");
        assertTrue(packer.getPackedRects().isEmpty(), "Packed rects for empty list should be empty.");
    }


    /**
     * Custom intersection check, as Rect.intersects() isn't a public method.
     * This checks if two rectangles overlap in 2D space.
     */
    private boolean intersects(Rect r1, Rect r2) {
        return r1.x() < r2.x() + r2.w() &&
               r1.x() + r1.w() > r2.x() &&
               r1.y() < r2.y() + r2.h() &&
               r1.y() + r1.h() > r2.y();
    }

}
