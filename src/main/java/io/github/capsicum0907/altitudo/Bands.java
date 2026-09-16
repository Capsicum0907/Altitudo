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
 * Carries vanilla's bands into the space the world gained.
 * <p>
 * Vanilla writes where something may appear as a height, and those heights are
 * written against the world vanilla has. A world eight or thirty times taller does
 * not move them: an absolute band stays where it was and leaves the rest empty, a
 * relative one stretches across the whole new range and thins by the same factor.
 * Either way the added space is worth less than the space that was already there,
 * which is the half of this mod that is not the dimensions.
 *
 * <h2>Two steps, and the order matters</h2>
 * <ol>
 * <li>Resolve the band against <em>vanilla's</em> extent, so it sits where vanilla
 * drew it rather than where the floor moved to.</li>
 * <li>Emit that placement unchanged, then <em>add</em> copies one period apart
 * through the range to be filled.</li>
 * </ol>
 * Because the original is emitted untouched, the density vanilla had cannot fall.
 * That is the whole invariant; the rest is which copies to make.
 *
 * <h2>Why copies at all</h2>
 * Vanilla already scales some of its own content with height: everything placed by
 * {@code count_on_every_layer} finds more layers in a taller world and places more.
 * In the Nether that is the vegetation, the fungi and the basalt columns - eight
 * times as much, without anyone asking. Tiling the {@code height_range} features
 * makes the other twenty-seven behave the way those ten already do. The rule is
 * vanilla's, not this mod's.
 */
public final class Bands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicLong OVERWORLD_SEEN = new AtomicLong();
    private static final AtomicLong OVERWORLD_ADDED = new AtomicLong();
    private static final AtomicLong NETHER_SEEN = new AtomicLong();
    private static final AtomicLong NETHER_ADDED = new AtomicLong();

    private Bands() {
    }

    /**
     * What to do in one dimension.
     *
     * @param vanilla   the extent to resolve bands against
     * @param sliceMin  first height of the band that gets repeated
     * @param sliceMax  one past its last height; the difference is the period
     * @param fillMin   lowest height a copy may occupy
     * @param fillMax   one past the highest
     * @param oresOnly  whether only ore veins may be copied
     * @param farBonus  density at the far end of the fill, as a multiple of vanilla
     */
    public record Plan(Dimensions vanilla, int sliceMin, int sliceMax, int fillMin, int fillMax,
            boolean oresOnly, double farBonus, boolean nether) {
        int period() {
            return this.sliceMax - this.sliceMin;
        }
    }

    public static boolean enabled() {
        try {
            return AltitudoConfig.FOLLOW_ORES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Which dimension this is, by its extent rather than its name, so a modded
     * dimension answers on the same terms.
     * <p>
     * ⚠ A dimension this mod did not resize gets no plan at all. The Nether
     * decorates through the same code and most of its features are anchored
     * relatively too: resolving those against the overworld's extent would move
     * every one of them.
     */
    public static Plan planFor(PlacementContext context) {
        Dimensions overworld;
        try {
            overworld = Dimensions.fromConfig();
        } catch (RuntimeException e) {
            return null;
        }
        if (matches(context, overworld)) {
            OVERWORLD_SEEN.incrementAndGet();
            int anchor = AltitudoConfig.ORE_ANCHOR.get();
            // Only the underground is repeated. Above the anchor is surface, which
            // vanilla already fills and which has nowhere new to go.
            return new Plan(Dimensions.VANILLA_OVERWORLD, Dimensions.VANILLA_OVERWORLD.minY(),
                    anchor, overworld.minY(), anchor, true,
                    AltitudoConfig.DEEP_ORE_BONUS.get(), false);
        }
        if (!AltitudoConfig.EXTEND_NETHER.get()) {
            return null;
        }
        Dimensions nether;
        try {
            nether = Dimensions.netherFromConfig();
        } catch (RuntimeException e) {
            return null;
        }
        if (matches(context, nether)) {
            NETHER_SEEN.incrementAndGet();
            // No surface to protect, so the whole of vanilla's range is the slice and
            // the copies go both ways. No bonus either: making the deep richer is
            // adding rather than not thinning, and this mod does not add.
            return new Plan(Dimensions.VANILLA_NETHER, Dimensions.VANILLA_NETHER.minY(),
                    Dimensions.VANILLA_NETHER.minY() + Dimensions.VANILLA_NETHER.height(),
                    nether.minY(), nether.minY() + nether.height(), false, 1.0, true);
        }
        return null;
    }

    private static boolean matches(PlacementContext context, Dimensions configured) {
        return context.getMinGenY() == configured.minY()
                && context.getGenDepth() == configured.height();
    }

    /** Whether this placement is an ore vein, asked of its configuration so a mod's counts too. */
    public static boolean isOre(PlacementContext context) {
        Optional<PlacedFeature> top = context.topFeature();
        return top.isPresent() && top.get().feature().value().config() instanceof OreConfiguration;
    }

    /**
     * The same context, answering with vanilla's extent instead of this world's.
     * <p>
     * ⚠ Load-bearing rather than tidy. {@code ore_soul_sand} is anchored
     * {@code above_bottom 0} at one end and {@code absolute 31} at the other: with
     * the Nether's floor at -128 that band becomes -128..31 instead of 0..31, and
     * the soul sand ends up smeared through a hundred and sixty blocks of lava sea.
     * The overworld's diamond and redstone move the same way.
     * <p>
     * {@link WorldGenerationContext} keeps two integers and no reference to the
     * level or the generator, so one of these costs nothing and holds nothing.
     */
    public static WorldGenerationContext vanillaExtent(PlacementContext context, Plan plan) {
        return new VanillaExtent(context, plan.vanilla());
    }

    private static final class VanillaExtent extends WorldGenerationContext {
        private final Dimensions vanilla;

        VanillaExtent(PlacementContext context, Dimensions vanilla) {
            super(context.generator(), context.getLevel());
            this.vanilla = vanilla;
        }

        @Override
        public int getMinGenY() {
            return this.vanilla.minY();
        }

        @Override
        public int getGenDepth() {
            return this.vanilla.height();
        }
    }

    /**
     * Every position this placement should produce: vanilla's, then the copies.
     *
     * @param pos     where vanilla's placement landed, x and z already chosen
     * @param sampled the height vanilla's height provider gave
     */
    public static List<BlockPos> positions(Plan plan, BlockPos pos, int sampled, RandomSource random) {
        List<BlockPos> out = new ArrayList<>();
        out.add(pos.atY(sampled));

        // Outside the slice there is nothing to repeat. This is also what keeps the
        // tails of the wide overworld bands - redstone to -96, diamond to -144 -
        // from being stacked into every copy of the slice above them.
        if (sampled < plan.sliceMin() || sampled >= plan.sliceMax()) {
            return out;
        }
        int period = plan.period();
        if (period <= 0) {
            return out;
        }
        // ⚠ The low bound rounds towards zero and the high bound away from it, and
        // they are not the same expression. Taking the floor for both puts one copy
        // per placement below the world's floor, and since the low bound is also the
        // denominator of the bonus curve, it tilts the whole curve with it - about
        // five percent at the bottom, which is small enough to read as noise and
        // systematic enough not to be.
        int lowest = -Math.floorDiv(sampled - plan.fillMin(), period);
        int highest = Math.floorDiv(plan.fillMax() - 1 - sampled, period);

        int originX = (pos.getX() >> 4) << 4;
        int originZ = (pos.getZ() >> 4) << 4;
        for (int k = lowest; k <= highest; k++) {
            if (k == 0) {
                continue;
            }
            int y = sampled + k * period;
            for (int c = multiplicity(k, lowest, plan.farBonus(), random); c > 0; c--) {
                // Fresh x and z, or every copy lands in one column and reads as a
                // vertical stripe. Drawn from the chunk origin, which is the same
                // guarantee in_square gives, so no copy can reach a chunk that does
                // not exist yet.
                out.add(new BlockPos(originX + random.nextInt(16), y, originZ + random.nextInt(16)));
                count(plan, 1);
            }
        }
        return out;
    }

    /**
     * How many copies this repeat gets for each one vanilla placed.
     * <p>
     * One everywhere at a bonus of 1, which is the whole of the Nether. Below the
     * overworld's anchor it rises linearly to the bonus at the floor - the only
     * place this mod makes something denser than vanilla, and the reason to dig.
     * The value is generally not whole, so the fraction is taken as a chance rather
     * than rounded; over a chunk the average is the value asked for.
     */
    private static int multiplicity(int k, int lowest, double bonus, RandomSource random) {
        if (bonus <= 1.0 || lowest >= -1) {
            return 1;
        }
        double value = 1.0 + (bonus - 1.0) * (k + 1) / (double) (lowest + 1);
        int whole = (int) value;
        return random.nextDouble() < value - whole ? whole + 1 : whole;
    }

    private static void count(Plan plan, long n) {
        (plan.nether() ? NETHER_ADDED : OVERWORLD_ADDED).addAndGet(n);
    }

    /**
     * Nothing added means every band is still where vanilla put it and the space the
     * world gained is worth less than the space it had - a world that generates
     * perfectly well and has nothing in it. That was the shipped failure of the mod
     * this replaces, and the quiet kind: nothing reports it but the digging.
     */
    public static void report() {
        if (!enabled()) {
            LOGGER.info("Altitudo is not following bands (followOres = false).");
            return;
        }
        say("overworld", OVERWORLD_SEEN.get(), OVERWORLD_ADDED.get());
        if (AltitudoConfig.EXTEND_NETHER.get()) {
            say("nether", NETHER_SEEN.get(), NETHER_ADDED.get());
        }
    }

    private static void say(String where, long seen, long added) {
        if (seen == 0) {
            LOGGER.info("Altitudo has not decorated the {} yet.", where);
        } else if (added == 0) {
            LOGGER.warn("Altitudo saw {} {} placements and added none. The space that dimension"
                    + " gained is empty.", seen, where);
        } else {
            LOGGER.info("Altitudo added {} placements to the {}, on top of the {} vanilla asked for.",
                    added, where, seen);
        }
    }
}
