package io.github.capsicum0907.altitudo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;

public final class Bands {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final TagKey<PlacedFeature> KEEP = TagKey.create(Registries.PLACED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(Altitudo.MODID, "keep"));

    private static final AtomicLong OVERWORLD_SEEN = new AtomicLong();
    private static final AtomicLong OVERWORLD_ADDED = new AtomicLong();
    private static final AtomicLong NETHER_SEEN = new AtomicLong();
    private static final AtomicLong NETHER_ADDED = new AtomicLong();

    private Bands() {
    }

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

    public static Plan planFor(PlacementContext context) {
        Plan plan = dimensionPlan(context);
        if (plan == null || optedOut(context)) {
            return null;
        }
        (plan.nether() ? NETHER_SEEN : OVERWORLD_SEEN).incrementAndGet();
        return plan;
    }

    private static Plan dimensionPlan(PlacementContext context) {
        Dimensions overworld;
        try {
            overworld = Dimensions.fromConfig();
        } catch (RuntimeException e) {
            return null;
        }
        if (matches(context, overworld)) {
            int anchor = AltitudoConfig.ORE_ANCHOR.get();
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
            Dimensions vanilla = Dimensions.VANILLA_NETHER;
            return new Plan(vanilla, vanilla.minY(), vanilla.minY() + vanilla.height(),
                    nether.minY(), nether.minY() + nether.height(), false, 1.0, true);
        }
        return null;
    }

    private static boolean matches(PlacementContext context, Dimensions configured) {
        return context.getMinGenY() == configured.minY()
                && context.getGenDepth() == configured.height();
    }

    private static boolean optedOut(PlacementContext context) {
        Optional<PlacedFeature> top = context.topFeature();
        return top.isPresent() && context.getLevel().registryAccess()
                .registryOrThrow(Registries.PLACED_FEATURE).wrapAsHolder(top.get()).is(KEEP);
    }

    public static boolean isOre(PlacementContext context) {
        Optional<PlacedFeature> top = context.topFeature();
        return top.isPresent() && top.get().feature().value().config() instanceof OreConfiguration;
    }

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

    public static List<BlockPos> positions(Plan plan, BlockPos pos, int sampled, RandomSource random) {
        List<BlockPos> out = new ArrayList<>();
        out.add(pos.atY(sampled));

        if (sampled < plan.sliceMin() || sampled >= plan.sliceMax()) {
            return out;
        }
        int period = plan.period();
        if (period <= 0) {
            return out;
        }
        // The low bound rounds towards zero, the high bound away from it.
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
                out.add(new BlockPos(originX + random.nextInt(16), y, originZ + random.nextInt(16)));
                (plan.nether() ? NETHER_ADDED : OVERWORLD_ADDED).incrementAndGet();
            }
        }
        return out;
    }

    private static int multiplicity(int k, int lowest, double bonus, RandomSource random) {
        if (bonus <= 1.0 || lowest >= -1) {
            return 1;
        }
        double value = 1.0 + (bonus - 1.0) * (k + 1) / (double) (lowest + 1);
        int whole = (int) value;
        return random.nextDouble() < value - whole ? whole + 1 : whole;
    }

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
