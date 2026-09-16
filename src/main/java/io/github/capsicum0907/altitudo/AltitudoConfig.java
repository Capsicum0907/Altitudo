package io.github.capsicum0907.altitudo;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The world's vertical extent, in one place. Nothing else in the mod may hold one
 * of these numbers.
 * <p>
 * The same three values are needed in five places across three generated files,
 * so they are read from here and written out rather than typed five times. That
 * is the whole reason this mod exists rather than a data pack: a data pack has to
 * say 4064 in every file that wants it.
 * <p>
 * STARTUP, because the pack is registered before any world exists and the values
 * have to be readable by then. The cost is that changing them needs a restart,
 * which is free here: a world's height is fixed when it is created and never
 * changes afterwards, so there is nothing to change mid-session.
 */
public final class AltitudoConfig {
    /** {@code BlockPos} packs Y into 12 bits, which is where 4064 comes from. */
    public static final int LIMIT_MIN_Y = -2032;
    public static final int LIMIT_MAX_Y = 2031;
    public static final int LIMIT_HEIGHT = 4064;
    public static final int SECTION = 16;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MIN_Y = BUILDER
            .comment("Lowest block the world can hold. Must be a multiple of 16.",
                    "Vanilla is -64.")
            .defineInRange("minY", LIMIT_MIN_Y, LIMIT_MIN_Y, LIMIT_MAX_Y);

    public static final ModConfigSpec.IntValue HEIGHT = BUILDER
            .comment("How many blocks tall the world is. Must be a multiple of 16.",
                    "minY + height may not exceed 2032. Vanilla is 384.")
            .defineInRange("height", LIMIT_HEIGHT, SECTION, LIMIT_HEIGHT);

    public static final ModConfigSpec.IntValue SEA_LEVEL = BUILDER
            .comment("Y of the ocean surface. Any value inside the world is legal;",
                    "the game does not require a multiple of anything here.",
                    "",
                    "This moves the water, not the land. The ground's shape comes from",
                    "density functions that are anchored to vanilla's -64..320, so",
                    "lowering this on its own drains the oceans and leaves the terrain",
                    "standing above dry seabed. It becomes useful once something else",
                    "supplies the ground's shape. Until then, leave it at 63.")
            .defineInRange("seaLevel", 63, LIMIT_MIN_Y, LIMIT_MAX_Y);

    public static final ModConfigSpec.BooleanValue EXTEND_CAVES = BUILDER
            .comment("Carry the bounds that decide where caves may exist down to the new",
                    "floor. Vanilla anchors them near -64, so without this the space below",
                    "is shaped by 3D noise alone: large shapeless voids rather than cave",
                    "systems, and every one of them flooded with lava.",
                    "",
                    "Turn this off when another mod supplies the terrain's shape.")
            .define("extendCaves", true);

    public static final ModConfigSpec.BooleanValue FOLLOW_ORES = BUILDER
            .comment("Carry the ore bands into the space the world gained.",
                    "Vanilla writes most of them at absolute heights, so without this the",
                    "added depth is bare stone - the failure the mod this replaces shipped",
                    "with, and the quiet kind: nothing reports it but the digging.",
                    "",
                    "Other mods' ores come along, because an ore is recognised by being",
                    "configured as one, not by a list of ids this would have to know.")
            .define("followOres", true);

    public static final ModConfigSpec.IntValue ORE_ANCHOR = BUILDER
            .comment("At and above this height nothing is added. Below it, vanilla's slice",
                    "from -64 up to here is repeated on the way down to minY.",
                    "Keep this at or below sea level, or bands that belong to the surface",
                    "will be copied underground.",
                    "",
                    "The deep is a copy of that slice, so what vanilla does not put below",
                    "this height is not down there either: at 0 that means no coal at all,",
                    "and copper only in a short stripe once per repeat.")
            .defineInRange("oreAnchor", 0, LIMIT_MIN_Y, LIMIT_MAX_Y);

    public static final ModConfigSpec.DoubleValue DEEP_ORE_BONUS = BUILDER
            .comment("How dense ore is at the floor, as a multiple of vanilla. 1.0 repeats",
                    "vanilla's density unchanged all the way down; the rise from the top of",
                    "the deep to the floor is linear.",
                    "",
                    "Nothing is taken from the vanilla range to pay for it - every vein down",
                    "here is an addition. So this is also the bill: filling the world at",
                    "vanilla density already costs about one vein per repeat, and this",
                    "multiplies that. Raise it and watch chunk generation time.")
            .defineInRange("deepOreBonus", 3.0, 1.0, 16.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AltitudoConfig() {
    }
}
