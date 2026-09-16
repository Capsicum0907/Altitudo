package io.github.capsicum0907.altitudo;

import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;

public final class Preset {
    public static final String ID = Altitudo.MODID + ":" + Altitudo.MODID;

    private Preset() {
    }

    public static byte[] json(boolean nether) {
        JsonObject dimensions = new JsonObject();
        dimensions.add("minecraft:overworld", dimension(Altitudo.MODID + ":overworld",
                multiNoise("minecraft:overworld"), Altitudo.MODID + ":overworld"));
        dimensions.add("minecraft:the_nether",
                dimension(nether ? Altitudo.MODID + ":the_nether" : "minecraft:the_nether",
                        multiNoise("minecraft:nether"),
                        nether ? Altitudo.MODID + ":nether" : "minecraft:nether"));
        JsonObject end = new JsonObject();
        end.addProperty("type", "minecraft:the_end");
        dimensions.add("minecraft:the_end",
                dimension("minecraft:the_end", end, "minecraft:end"));

        JsonObject out = new JsonObject();
        out.add("dimensions", dimensions);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject multiNoise(String preset) {
        JsonObject source = new JsonObject();
        source.addProperty("type", "minecraft:multi_noise");
        source.addProperty("preset", preset);
        return source;
    }

    private static JsonObject dimension(String type, JsonObject biomeSource, String settings) {
        JsonObject generator = new JsonObject();
        generator.addProperty("type", "minecraft:noise");
        generator.add("biome_source", biomeSource);
        generator.addProperty("settings", settings);

        JsonObject dimension = new JsonObject();
        dimension.addProperty("type", type);
        dimension.add("generator", generator);
        return dimension;
    }
}
