package ru.zkir.urbaneye3d.assetconfig;

import ru.zkir.urbaneye3d.RenderableElement;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

public class GeneratorRegistry {
    private final Map<String, ProceduralGenerator> registry = new HashMap<>();
    
    private static final GeneratorRegistry instance = new GeneratorRegistry();

    private GeneratorRegistry() {
        register("ad_column",
                (primitive, origin, rule) -> RenderableElement.createAdColumn(primitive, origin, primitive.getInterestingTags())
        );

        register("flagpole",
                (primitive, origin, rule) -> RenderableElement.createFlagpole(primitive, origin, primitive.getInterestingTags(), new SplittableRandom(primitive.getId()))
        );

        register("chimney",
                (primitive, origin, rule) -> RenderableElement.createChimney(primitive, origin)
        );

        register("street_cabinet",
                (primitive, origin, rule) -> RenderableElement.createStreetCabinet(primitive, origin)
        );

        register("wind_turbine",
                (primitive, origin, rule) -> RenderableElement.createWindTurbine(primitive, origin, new SplittableRandom(primitive.getId()))
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
