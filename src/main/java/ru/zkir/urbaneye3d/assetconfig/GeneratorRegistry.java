package ru.zkir.urbaneye3d.assetconfig;

import ru.zkir.urbaneye3d.RenderableElement;

import java.util.HashMap;
import java.util.Map;

public class GeneratorRegistry {
    private final Map<String, ProceduralGenerator> registry = new HashMap<>();
    
    private static final GeneratorRegistry instance = new GeneratorRegistry();

    private GeneratorRegistry() {
        register("ad_column",
                (primitive, origin, rule, random) -> RenderableElement.createAdColumn(primitive, origin, primitive.getInterestingTags(), random)
        );

        register("flagpole",
                (primitive, origin, rule, random) -> RenderableElement.createFlagpole(primitive, origin, primitive.getInterestingTags(), random)
        );

        register("chimney",
                (primitive, origin, rule, random) -> RenderableElement.createChimney(primitive, origin, random)
        );

        register("street_cabinet",
                (primitive, origin, rule, random) -> RenderableElement.createStreetCabinet(primitive, origin)
        );
    }

    public static GeneratorRegistry getInstance() {
        return instance;
    }

    public void register(String name, ProceduralGenerator generator) {
        registry.put(name, generator);
    }

    public ProceduralGenerator get(String name) {
        return registry.get(name);
    }
}
