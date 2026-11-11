package ru.zkir.urbaneye3d.utils;

import com.jogamp.opengl.util.packrect.BackingStoreManager;
import com.jogamp.opengl.util.packrect.Rect;
import com.jogamp.opengl.util.packrect.RectanglePacker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finds the minimal square to pack a list of rectangles and calculates their final positions and UV coordinates.
 */
public class UvPacker {

    /** A dummy BackingStoreManager that does nothing, as we don't need actual texture allocation. */
    static class DummyManager implements BackingStoreManager {
        @Override public Object allocateBackingStore(int w, int h) { return new Object(); }
        @Override public void deleteBackingStore(Object backingStore) {}
        @Override public boolean canCompact() { return true; }

        @Override
        public boolean preExpand(Rect cause, int attemptNumber) {
            // Return false because we want to pack into a fixed-size square.
            // We do not allow the packer to expand the backing store.
            return false;
        }

        @Override
        public boolean additionFailed(Rect cause, int attemptNumber) {
            // IMPORTANT: Return false.
            // Returning true would cause the RectanglePacker to try adding the
            // rectangle again. Since our manager doesn't change the packing area,
            // this would lead to an infinite loop (a hang). By returning false, we
            // tell the packer the failure is final, causing it to throw a
            // RuntimeException, which is caught in canPack().
            throw new RuntimeException("Unable to pack rectangle" + cause);
            //return false;
        }

        @Override public void beginMovement(Object oldBackingStore, Object newBackingStore) {}
        @Override public void move(Object oldBackingStore, Rect oldLocation, Object newBackingStore, Rect newLocation) {}
        @Override public void endMovement(Object oldBackingStore, Object newBackingStore) {}
    }

    private final List<Rect> inputRects;
    private final int optimalSize;
    private final Map<Object, Rect> packedRects;

    /**
     * Initializes the packer and runs the packing algorithm.
     * @param rects The list of rectangles to pack. Their initial x and y are ignored.
     */
    public UvPacker(List<Rect> rects) {
        this.inputRects = new ArrayList<>(rects);
        // Sort by decreasing height - this is a common heuristic that improves packing efficiency.
        this.inputRects.sort((r1, r2) -> Integer.compare(r2.h(), r1.h()));

        this.optimalSize = findOptimalPackingSize(this.inputRects);
        this.packedRects = packIntoSquare(this.inputRects, this.optimalSize);
    }

    /**
     * @return The side length of the optimal square found.
     */
    public int getOptimalSize() {
        return optimalSize;
    }

    /**
     * @return A map where keys are the user data from the original Rects and values are the new Rects with final positions.
     */
    public Map<Object, Rect> getPackedRects() {
        return packedRects;
    }

    /**
     * Checks if a list of rectangles can be packed into a square of a given size.
     * @param rects The rectangles to pack.
     * @param S The side length of the square.
     * @return true if packing is successful, false otherwise.
     */
    private static boolean canPack(List<Rect> rects, int S) {
        DummyManager manager = new DummyManager();
        RectanglePacker packer = new RectanglePacker(manager, S, S);
        packer.setMaxSize(S, S); // Prevent expansion

        for (Rect rect : rects) {
            try {
                packer.add(rect);
            } catch (RuntimeException e) {
                // Thrown by RectanglePacker when a rectangle doesn't fit.
                packer.dispose();
                return false;
            }
        }
        packer.dispose();
        return true;
    }

    /**
     * Uses a binary search to find the smallest possible square side length for packing.
     * @param rects The rectangles to pack.
     * @return The optimal side length.
     */
    private static int findOptimalPackingSize(List<Rect> rects) {
        if (rects.isEmpty()) {
            return 0;
        }

        int totalArea = rects.stream().mapToInt(r -> r.w() * r.h()).sum();
        int low = (int) Math.ceil(Math.sqrt(totalArea)); // Lower bound: sqrt of total area

        int maxW = rects.stream().mapToInt(Rect::w).max().orElse(0);
        int maxH = rects.stream().mapToInt(Rect::h).max().orElse(0);
        // A loose but safe upper bound. Could be sum of all widths or heights.
        int high = Math.max(maxW, maxH) * rects.size();

        int bestS = high;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (canPack(rects, mid)) {
                bestS = mid;
                high = mid - 1; // Try to find an even smaller square
            } else {
                low = mid + 1;
            }
        }
        return bestS;
    }

    /**
     * Performs the final packing into a square of a given size.
     * @param rects The rectangles to pack.
     * @param size The side length of the square.
     * @return A map of the packed rectangles.
     */
    private static Map<Object, Rect> packIntoSquare(List<Rect> rects, int size) {
        DummyManager manager = new DummyManager();
        RectanglePacker packer = new RectanglePacker(manager, size, size);
        packer.setMaxSize(size, size);

        for (Rect rect : rects) {
            packer.add(rect);
        }

        List<Rect> results = new ArrayList<>();
        packer.visit(results::add);
        packer.dispose();

        return results.stream().collect(Collectors.toMap(Rect::getUserData, r -> r));
    }
}
