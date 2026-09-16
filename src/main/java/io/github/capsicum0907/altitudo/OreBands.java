package io.github.capsicum0907.altitudo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;

/**
 * Carries the ore bands into the space the world gained.
 * <p>
 * Vanilla writes where an ore may appear as a height, and most of those heights
 * are absolute: copper at -16..112, gold at -64..32, diamond relative to a floor
 * that used to be at -64. A world thirty times deeper does not move any of them,
 * so the added space is stone. This is the half of the mod that is not the
 * dimensions.
 *
 * <h2>Additive, because density may not fall</h2>
 * Vanilla's slice from {@code minY} up to the anchor is repeated on the way down.
 * The placement vanilla asked for is emitted unchanged - same height, same x and
 * z - and the repeats are <em>added</em> on top of it. So the vanilla range keeps
 * vanilla's density exactly, and every repeat below it gets at least as much.
 * <p>
 * ⚠ The two designs that came before both failed on this. Stretching one band
 * across the whole depth, and then repeating a band and choosing one repeat at
 * random, are the same mistake twice: they move a fixed number of placements over
 * a much taller world, which divides density by the number of repeats. Measured
 * from a column it looks like a balance question. It is not - it is the whole of
 * vanilla's underground spread over two thousand blocks, which leaves both the
 * vanilla range poorer than vanilla and the deep empty.
 *
 * <h2>Why it can add at all now</h2>
 * The earlier design refused to duplicate because the point it worked at could not
 * tell an ore from a geode or a dungeon, and multiplying those broke world
 * generation. {@link PlacementContext#topFeature()} carries the feature being
 * placed, so the question is answerable here: an ore is a feature configured with
 * {@link OreConfiguration}. That reaches every mod's ores without naming any of
 * them, and leaves everything else exactly as vanilla placed it.
 */
public final class OreBands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicLong ORES = new AtomicLong();
    private static final AtomicLong ADDED = new AtomicLong();

    private OreBands() {
    }

    public static boolean enabled() {
        try {
            return AltitudoConfig.FOLLOW_ORES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Whether this placement is an ore vein, and so may be repeated downwards.
     * <p>
     * Asked of the configuration rather than of the feature's id, so that a mod's
     * ore is recognised on the same terms as vanilla's.
     */
    public static boolean isOre(PlacementContext context) {
        Optional<PlacedFeature> top = context.topFeature();
        if (top.isEmpty()) {
            return false;
        }
        return top.get().feature().value().config() instanceof OreConfiguration;
    }

    /**
     * The same context, answering with vanilla's extent instead of this world's.
     * <p>
     * ⚠ Without this the vanilla range ends up poorer than vanilla, and the mod's
     * own ore work cannot see it. An ore band written as {@code above_bottom}
     * resolves against whatever floor the world has, so extending the world drags
     * diamond and redstone from -64..16 down to the new bedrock - measured at half
     * of vanilla's density in -64..-1 with the ore work switched off entirely.
     * <p>
     * Resolving an ore's band as though the world were vanilla puts it back where
     * vanilla drew it; the repeats below are then this mod's business rather than
     * an accident of where the floor moved to. Absolute anchors are unaffected,
     * since they ignore the context.
     * <p>
     * {@link WorldGenerationContext} keeps two integers and no reference to the
     * level or the generator, so one of these costs nothing and holds nothing.
     */
    private static final class VanillaExtent extends WorldGenerationContext {
        VanillaExtent(PlacementContext context) {
            super(context.generator(), context.getLevel());
        }

        @Override
        public int getMinGenY() {
            return Dimensions.VANILLA.minY();
        }

        @Override
        public int getGenDepth() {
            return Dimensions.VANILLA.height();
        }
    }

    /** Where vanilla would have put this band, whatever floor the world has now. */
    public static WorldGenerationContext vanillaExtent(PlacementContext context) {
        return new VanillaExtent(context);
    }

    /**
     * Whether this is a dimension Altitudo resized.
     * <p>
     * ⚠ Asked before touching anything, because the Nether decorates through the
     * same code and most of its features are anchored relatively too. Pinning those
     * to the overworld's vanilla extent would move every one of them, and dropping
     * a repeat of an ore band would put it below the Nether's floor.
     * <p>
     * The test is the extent itself rather than the dimension's name, so it answers
     * for a modded dimension on the same terms.
     */
    public static boolean resized(PlacementContext context) {
        Dimensions configured = Dimensions.fromConfig();
        return context.getMinGenY() == configured.minY()
                && context.getGenDepth() == configured.height();
    }

    /**
     * Every position this placement should produce: vanilla's, then one set per
     * repeat below it.
     *
     * @param pos     where vanilla's placement landed, x and z already chosen
     * @param sampled the height vanilla's height provider gave
     */
    public static List<BlockPos> positions(BlockPos pos, int sampled, RandomSource random) {
        List<BlockPos> out = new ArrayList<>();
        out.add(pos.atY(sampled));
        ORES.incrementAndGet();

        int anchor = AltitudoConfig.ORE_ANCHOR.get();
        if (sampled >= anchor) {
            return out;
        }
        int band = anchor - Dimensions.VANILLA.minY();
        int floor = Dimensions.fromConfig().minY();
        if (band <= 0 || floor >= Dimensions.VANILLA.minY()) {
            return out;
        }
        int repeats = (anchor - floor) / band;
        if (repeats <= 1) {
            return out;
        }

        double bonus = AltitudoConfig.DEEP_ORE_BONUS.get();
        int originX = (pos.getX() >> 4) << 4;
        int originZ = (pos.getZ() >> 4) << 4;
        for (int i = 1; i < repeats; i++) {
            int y = sampled - i * band;
            if (y < floor) {
                break;
            }
            int copies = multiplicity(i, repeats, bonus, random);
            for (int c = 0; c < copies; c++) {
                // Fresh x and z, or every copy lands in one column and the ore reads
                // as a vertical stripe two thousand blocks tall. Drawn from the chunk
                // origin, which is the same guarantee in_square gives, so no copy can
                // reach a chunk that does not exist yet.
                out.add(new BlockPos(originX + random.nextInt(16), y, originZ + random.nextInt(16)));
                ADDED.incrementAndGet();
            }
        }
        return out;
    }

    /**
     * How many veins this repeat gets for each one vanilla placed.
     * <p>
     * One at the first repeat below the vanilla range, rising linearly to
     * {@code deepOreBonus} at the floor. The value is generally not a whole number,
     * so the fraction is taken as a chance rather than rounded - over a chunk's
     * worth of placements the average is the value asked for.
     */
    private static int multiplicity(int repeat, int repeats, double bonus, RandomSource random) {
        double value = 1.0;
        if (bonus > 1.0 && repeats > 2) {
            value = 1.0 + (bonus - 1.0) * (repeat - 1) / (double) (repeats - 2);
        }
        int whole = (int) value;
        return random.nextDouble() < value - whole ? whole + 1 : whole;
    }

    /**
     * Zero ores recognised means the test above never matched and the added depth
     * is bare stone - a world that generates perfectly well and has nothing in it.
     * That was the shipped failure of the mod this replaces, and the quiet kind:
     * nothing reports it but the digging.
     */
    public static void report() {
        if (!enabled()) {
            LOGGER.info("Altitudo is not following ore bands (followOres = false).");
            return;
        }
        long ores = ORES.get();
        if (ores == 0) {
            LOGGER.warn("Altitudo recognised no ore placement. Everything below y={} is bare stone.",
                    Dimensions.VANILLA.minY());
        } else if (ADDED.get() == 0) {
            LOGGER.warn("Altitudo recognised {} ore placements but added none below y={}.",
                    ores, AltitudoConfig.ORE_ANCHOR.get());
        } else {
            LOGGER.info("Altitudo added {} ore veins below y={} to the {} vanilla asked for.",
                    ADDED.get(), AltitudoConfig.ORE_ANCHOR.get(), ores);
        }
    }
}
