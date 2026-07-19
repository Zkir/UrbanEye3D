# Objective
Create a universal, extensible configuration system for mapping OSM features to 3D assets (billboards, OBJ models, procedural generators) using a MapCSS-like syntax with support for Level of Detail (LOD).

# Key Files & Context
- `d:/UrbanEye3D/src/main/resources/textures/textures.cfg` (to be replaced/migrated to something like `assets.mapcss`)
- `d:/UrbanEye3D/src/main/java/ru/zkir/urbaneye3d/Scene.java` (needs to use the new config instead of hardcoded if-statements)
- New files will be created in a new package, e.g., `ru.zkir.urbaneye3d.assetconfig`.

# Background & Motivation
Currently, asset assignment (like trees, benches, street lamps) is hardcoded in `Scene.java` using manual `if` checks. The tree texture assignment uses a simple custom `textures.cfg`. As we add more asset types and plan to support LODs, this approach becomes unmaintainable. We need a unified, data-driven approach that allows adding new assets and LODs without recompiling Java code.

# Scope & Impact
- Replaces the existing `textures.cfg` parser.
- Removes hardcoded object assignments from `Scene.calculateUpdate`.
- Introduces a robust specificity scoring system to handle OSM's implicit taxonomy (e.g., `species` > `leaf_type`).
- Lays the groundwork for distance-based LOD rendering in `Renderer3D`.

# Proposed Solution

## 1. Syntax (MapCSS-like with LOD extensions)

We will use a syntax similar to MapCSS. To support LODs, we will adopt the zoom-level syntax `|z` but use `|l` (level) or `|d` (distance). Given that LOD is often distance-based, distance ranges might be more intuitive, but abstract LOD levels (0, 1, 2) are easier to configure. Let's use `|l` for abstract LOD levels (0 = closest).

```css
/* Base tree: LOD 0 (close) is a model, LOD 1 (far) is a billboard */
node|l0[natural=tree] {
    generator: model;
    model: "models/tree_base_high.obj";
}
node|l1-[natural=tree] {
    generator: billboard;
    texture: "trees/tree_base_distant.png";
}

/* Specific tree overrides */
node|l0[natural=tree][species="Betula pendula"] {
    generator: model;
    model: "models/birch_high.obj";
}
node|l1-[natural=tree][species="Betula pendula"] {
    generator: billboard;
    texture: "trees/birch_distant.png";
}

/* Bench: No LOD specified means it applies to all LODs (or we define a default fallback behavior) */
node[amenity=bench] {
    generator: model;
    model: "models/bench.obj";
    rotatable: true;
}

/* Procedural */
node[advertising=column] {
    generator: procedural;
    procedure: "ad_column";
}
```

*Syntax Rules:*
- `node`, `way`, `area` prefixes.
- `|l0` (exactly level 0), `|l1-` (level 1 and higher/further), `|l0-1` (levels 0 to 1).
- `[key=value]` attribute selectors.
- Property blocks enclosed in `{ ... }`.

## 2. Specificity Scoring

To resolve conflicts (e.g., `[natural=tree]` vs `[natural=tree][species="..."]`), we need a scoring system. Since we can't rely on simple counts, we assign predefined weights to known keys:
- Base tag match (`[key=value]`): +10
- Taxonomy weights (added to base score):
  - `species`: +500
  - `genus`: +100
  - `leaf_type`: +50
  - `leaf_cycle`: +20
  - Default for unknown keys: +0

*Example:* `[natural=tree]` = 10. `[natural=tree][species="X"]` = 10 + (10+500) = 520.

## 3. Architecture

1.  **`AssetRuleParser`**: Reads the `.mapcss` file and produces `AssetRule` objects. It will handle the regex/string parsing of selectors and properties.
2.  **`AssetRule`**: Represents a single parsed block.
    - `Target`: Node, Way, Area
    - `LodRange`: Min LOD, Max LOD
    - `Selector`: List of Key-Value constraints.
    - `Properties`: Map of properties (e.g., `generator: "model"`).
    - `Specificity`: Calculated score.
3.  **`AssetConfig`**: Holds all `AssetRule`s. Provides a method `findBestRules(OsmPrimitive, int currentLod)` returning the properties of the highest-scoring matching rule.
4.  **`GeneratorRegistry`**: A simple map linking string aliases (e.g., `"ad_column"`) to functional interfaces or Factory classes for procedural generation, eliminating reflection.

## 4. Implementation Steps

1.  **Phase 1: Parser & Data Structures**
    - Create the `AssetRule`, `LodRange`, `Selector`, and `AssetConfig` classes.
    - Implement the `AssetRuleParser` using regex or manual tokenization.
    - Implement the specificity scoring logic.
    - *Testing:* Unit tests for parser (syntax errors, multi-line blocks), specificity calculation, and rule matching.

2.  **Phase 2: Registry & Integration**
    - Create `GeneratorRegistry`.
    - Create a test `.mapcss` file migrating the current `textures.cfg`, bench, lamp, and ad column logic.
    - *Testing:* Verify `AssetConfig` correctly selects rules based on primitive tags.

3.  **Phase 3: Scene Wiring**
    - Modify `Scene.calculateUpdate` to iterate over primitives, query `AssetConfig` for LOD 0 (initially, until renderer supports full LOD swapping), and use the registry/model loader/texture manager based on the `generator` property to create `RenderableElement`s.
    - *Testing:* Integration tests ensuring the scene correctly populates with the new system.

4.  **Phase 4: Cleanup**
    - Remove old hardcoded blocks from `Scene.java`.
    - Remove old `textures.cfg` parsing logic if fully superseded.

# Verification & Testing
- Unit tests for the parser to ensure it handles the `node|l0-2[key=val]` syntax correctly.
- Unit tests for specificity to ensure `species` always beats `leaf_type`.
- Integration test in `SceneTest` to ensure a bench is still rendered when defined via the new config.

# Alternatives Considered
- **Standard JOSM MapCSS Engine:** Too complex to extract, heavily tied to 2D rendering, and hard to extend with custom properties cleanly without modifying JOSM core.
- **INI/TOML Format:** Simpler to parse, but verbose when dealing with multiple LODs and less familiar to OSM developers used to MapCSS.