# Plan for Forest Rendering (`natural=wood`, `landuse=forest`)

**High-Level Goal:** Render polygonal areas tagged as `natural=wood` or `landuse=forest` by populating them with 3D tree objects, while ensuring no trees are placed on intersecting roads.

This is a multi-part plan:
1.  **Clearing Road Corridors:** We will programmatically "cut out" the areas covered by roads from the forest polygon.
2.  **Tree Population:** We will fill the resulting cleared area with randomly placed tree models.

---

## Part 0: Check for Existing Mapped Trees (Deprecated / Future Work)

**Objective:** In the future, we might want to consider manually placed `natural=tree` nodes.

*   **Note:** The initial idea to skip forest generation completely if manually placed trees exist proved to be flawed (a single unnoticeable tree could prevent an entire forest from rendering). Therefore, this check is currently disabled.
*   **Future possibilities:** We may revisit this to use manual trees to influence the calculated tree density or as part of a more advanced tree placement algorithm.

---

## Part 1: Clearing Road Corridors with JTS

**Objective:** Before generating any trees, subtract the area covered by any intersecting roads from the forest polygon. We will use the **JTS (Java Topology Suite)** library, which is already a project dependency.

### Step 1.1: Convert OSM Geometry to JTS Geometry
*   **Context:** `Scene.updateData()` method, after a forest polygon is identified.
*   **Action:**
    1.  Create a single `GeometryFactory` instance for reuse.
    2.  Convert the forest's `Contour` object into a JTS `Polygon`.
    3.  Create a list of all `highway=*` ways from the dataset and convert them into JTS `LineString` objects.

### Step 1.2: Identify and Buffer Intersecting Roads
*   **Action:**
    1.  Create an empty list for `roadPolygons`.
    2.  Iterate through all road `LineString`s. For each one that `intersects()` the `forestPolygon`:
        a. Determine the road's width by checking for a `width` tag, falling back to a default based on the `highway` tag's value (e.g., `primary` = 7m, `residential` = 5m).
        b. Buffer the line into a polygon: `roadPolygon = roadLineString.buffer(width / 2.0)`.
        c. Add the resulting `roadPolygon` to the list.

### Step 1.3: Perform Geometric Difference
*   **Action:**
    1.  If the `roadPolygons` list is not empty, merge them into a single `Geometry` object using a cascading `union()` operation.
    2.  Subtract the combined road geometry from the forest polygon: `Geometry finalForestArea = forestPolygon.difference(allRoadsGeometry)`.
    3.  The `finalForestArea` is the geometry we will use for tree placement. It may be a `Polygon` with holes or a `MultiPolygon`.

---

## Part 2: Populating the Cleared Area with Trees

**Objective:** Fill the `finalForestArea` geometry from Part 1 with randomly placed tree objects.

### Step 2.1: Triangulate the Area
*   **Challenge:** The `finalForestArea` can be a complex shape.
*   **Solution:** Decompose the area into simple triangles.
*   **Action:**
    1.  For each polygon within the `finalForestArea` geometry:
    2.  Feed its exterior and interior rings into the existing **JOSM tessellator utility** (the same one used for flat roofs). This will produce a list of triangles that perfectly tile the area.

### Step 2.2: Generate Random Tree Locations
*   **Challenge:** Place trees naturally, not on a grid.
*   **Solution:** Generate a random point inside each triangle.
*   **Action:**
    1.  Create a helper method `generateRandomPointsInTriangle(Triangle t, int count)`.
    2.  Implement a simple barycentric coordinate algorithm to generate random points that are guaranteed to be inside the triangle.

### Step 2.3: Control Tree Density
*   **Challenge:** Avoid overwhelming the renderer with too many objects.
*   **Solution:** Base the number of trees on the area and a user setting.
*   **Action:**
    1.  Add a `forestDensity` setting to `UrbanEye3dPreferences.java` (e.g., a slider from 0.1 to 1.0).
    2.  Calculate the total area of the `finalForestArea`.
    3.  Determine the total number of trees to create: `treeCount = area * DENSITY_CONSTANT * settings.getForestDensity()`.
    4.  Distribute `treeCount` across the triangles, proportional to each triangle's area.

### Step 2.4: Create and Render Tree Objects
*   **Action:** For each randomly generated point:
    1.  Convert the point's local 2D coordinates back to a world `LatLon` to serve as the `origin` for the new `RenderableElement`.
    2.  Create a `Mesh` for the tree using `MesherTree.generate(width, height)`. Width and height can be slightly randomized for variety.
    3.  Select a random tree texture from the `TextureManager` (e.g., via a new `getRandomTreeTextureName()` method).
    4.  Instantiate the tree object: `new RenderableElement(forestPrimitive, treeMesh, textureName)`.
    5.  Add the new element to the main `scene.renderableElements` list. The existing rendering loop will handle it automatically.
