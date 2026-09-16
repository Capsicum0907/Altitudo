package io.github.capsicum0907.altitudo;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AltitudoConfig {
    public static final int LIMIT_MIN_Y = -2032;
    public static final int LIMIT_MAX_Y = 2031;
    public static final int LIMIT_HEIGHT = 4064;
    public static final int SECTION = 16;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MIN_Y = BUILDER
            .comment("Lowest block of the overworld, a multiple of 16. Vanilla is -64.")
            .defineInRange("minY", LIMIT_MIN_Y, LIMIT_MIN_Y, LIMIT_MAX_Y);

    public static final ModConfigSpec.IntValue HEIGHT = BUILDER
            .comment("Overworld height, a multiple of 16. Vanilla is 384.")
            .defineInRange("height", LIMIT_HEIGHT, SECTION, LIMIT_HEIGHT);

    public static final ModConfigSpec.IntValue SEA_LEVEL = BUILDER
            .comment("Y of the ocean surface. Vanilla is 63. Moves the water, not the land.")
            .defineInRange("seaLevel", 63, LIMIT_MIN_Y, LIMIT_MAX_Y);

    public static final ModConfigSpec.BooleanValue EXTEND_CAVES = BUILDER
            .comment("Carry the bounds that decide where caves may exist down to minY.")
            .define("extendCaves", true);

    public static final ModConfigSpec.BooleanValue FOLLOW_ORES = BUILDER
            .comment("Repeat vanilla's bands into the space the world gained.")
            .define("followOres", true);

    public static final ModConfigSpec.IntValue ORE_ANCHOR = BUILDER
            .comment("Nothing is added at or above this height. Keep it at or below seaLevel.")
            .defineInRange("oreAnchor", 0, LIMIT_MIN_Y, LIMIT_MAX_Y);

    public static final ModConfigSpec.DoubleValue DEEP_ORE_BONUS = BUILDER
            .comment("Ore density at minY, as a multiple of vanilla. 1.0 repeats it unchanged.")
            .defineInRange("deepOreBonus", 4.5, 1.0, 16.0);

    public static final ModConfigSpec.BooleanValue EXTEND_NETHER = BUILDER
            .comment("Extend the Nether as well.")
            .define("extendNether", true);

    public static final ModConfigSpec.IntValue NETHER_MIN_Y = BUILDER
            .comment("Lowest block of the Nether. Vanilla is 0. Also sets the lava sea's depth.")
            .defineInRange("netherMinY", -128, LIMIT_MIN_Y, LIMIT_MAX_Y);

    public static final ModConfigSpec.IntValue NETHER_HEIGHT = BUILDER
            .comment("How much of the Nether is generated. Vanilla is 128. The roof sits on top.")
            .defineInRange("netherHeight", 1024, SECTION, LIMIT_HEIGHT);

    public static final ModConfigSpec.IntValue NETHER_ROOF_GAP = BUILDER
            .comment("Empty space kept above the Nether's roof, to build in. Vanilla is 128.")
            .defineInRange("netherRoofGap", 128, 0, LIMIT_HEIGHT);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AltitudoConfig() {
    }
}
