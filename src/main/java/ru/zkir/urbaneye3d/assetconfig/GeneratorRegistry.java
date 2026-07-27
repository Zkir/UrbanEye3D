package ru.zkir.urbaneye3d.assetconfig;

import java.util.HashMap;
import java.util.Map;

public class GeneratorRegistry {
    private final Map<String, ProceduralGenerator> registry = new HashMap<>();
    
    private static final GeneratorRegistry instance = new GeneratorRegistry();

    private GeneratorRegistry() {
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
