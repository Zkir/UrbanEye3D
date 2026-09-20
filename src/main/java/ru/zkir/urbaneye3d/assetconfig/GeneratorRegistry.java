package ru.zkir.urbaneye3d.assetconfig;

import ru.zkir.urbaneye3d.meshers.custom.MesherAdColumn;
import ru.zkir.urbaneye3d.meshers.custom.MesherChimney;
import ru.zkir.urbaneye3d.meshers.custom.MesherFlagpole;
import ru.zkir.urbaneye3d.meshers.custom.MesherStreetCabinet;
import ru.zkir.urbaneye3d.meshers.custom.MesherWindTurbine;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

public class GeneratorRegistry {
    private final Map<String, ProceduralGenerator> registry = new HashMap<>();
    
    private static final GeneratorRegistry instance = new GeneratorRegistry();

    private GeneratorRegistry() {
        register("ad_column", MesherAdColumn::generate );

        register("flagpole",  MesherFlagpole::generate );

        register("chimney",   MesherChimney::generate  );

        register("street_cabinet", MesherStreetCabinet::generate );

        register("wind_turbine",  MesherWindTurbine::generate  );


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
