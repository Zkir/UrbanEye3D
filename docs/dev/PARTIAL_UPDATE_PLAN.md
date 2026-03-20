# Plan for Partial Scene Updates

**I. Core Principle: Dirty Bounding Box**

The central idea is to track the geographical area (bounding box) that has been affected by a change. Instead of just knowing *that* something changed, we'll know *where* it changed. This "dirty box" will be used to decide which elements need recalculation.

**II. Changes to Event Handling (`DialogWindow3D.java`)**

1.  **Listen to Granular Events:** Stop using the generic `dataChanged` event as the primary trigger. Instead, enhance the specific listeners (`primitivesAdded`, `primitivesRemoved`, `nodeMoved`, `wayNodesChanged`, `relationMembersChanged`) which provide the set of primitives that were changed.

2.  **Calculate Dirty Bounding Box (REVISED):**
    *   **Initial Set:** Start with the set of primitives directly provided by the JOSM event (e.g., the moved node).
    *   **Recursive Parent Collection:** Create a new helper function. This function will take the initial set of changed primitives and recursively find all parent objects that depend on them.
        *   For each primitive in the set, iterate through its referrers (`primitive.getReferrers()`).
        *   Add each referrer (e.g., a way that uses a changed node, a relation that uses a changed way) to a temporary set to avoid processing them multiple times.
        *   Recursively do this until no new parents are found. This will collect all ways, relations, etc., affected by the initial change.
    *   **Final "Dirty Set":** Combine the initial primitives and all their collected parents into a final "dirty set".
    *   **Calculate Bounding Box:** Iterate through this complete "dirty set" of primitives and calculate a single bounding box that encloses all of them. This is our final `dirtyBounds`.

3.  **Modify `requestSceneUpdate`:** Change the signature of `requestSceneUpdate` to accept an optional "dirty" `Bounds` object. If the `Bounds` object is null, it means a full update is required (e.g., layer change).

4.  **Update Event Listener Calls:** The listener methods will now call `requestSceneUpdate(dirtyBounds)`.


**III. Partial Update for Scene Objects (Buildings, etc.) (`Scene.java`)**

1.  **Modify `calculateUpdate`:** The `calculateUpdate(DataSet dataSet)` method will be changed to `calculateUpdate(DataSet dataSet, Bounds dirtyBounds)`.
2.  **Update Element Selection Logic:**
    *   The method will no longer clear the entire `renderableElements` list on every run.
    *   **Removal:** It will first iterate through the *existing* `renderableElements` and remove any element whose underlying primitive has been deleted or whose bounding box intersects with `dirtyBounds`.
    *   **Addition/Update:** It will then iterate through the primitives in the `dataSet` that intersect with `dirtyBounds`. For each of these, it will generate a new `RenderableElement` and add it to the scene. This re-uses the existing logic but constrains it to the dirty area.
3.  **Modify `applyUpdate`:** The `applyUpdate` method will need to be smarter. Instead of just `clear()` and `addAll()`, it will need to perform a more targeted removal and addition of elements based on the results from `calculateUpdate`. A better approach might be for `calculateUpdate` to return a `SceneUpdate` object that contains two lists: `elementsToRemove` (by ID) and `elementsToAdd`. `applyUpdate` would then execute these changes.

**IV. Partial Update for Ground Tiles (`GroundPlane.java`)**

This will directly address the "redrawn more than necessary" observation.

1.  **Modify `groundPlane.update`:** The method signature will change to `update(..., Bounds dirtyBounds, ...)`. The `forcedUpdate` boolean will be removed in favor of this more granular approach.
2.  **Selective Tile Invalidation:** Inside `update`, when looping through the required tiles:
    *   The call to `tile.loadTextureAsync` will have a new condition.
    *   A tile will be re-rendered (`loadTextureAsync` is called) only if:
        a. It has no image data (`!tile.hasImageData()`).
        b. **OR** its own bounds intersect with the `dirtyBounds` passed into the method.
    *   This ensures that only tiles in the changed area are re-rendered, while others are left untouched.
