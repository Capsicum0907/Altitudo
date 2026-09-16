package io.github.capsicum0907.altitudo;

import java.util.concurrent.atomic.AtomicLong;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Carries the ore bands into the space the world gained.
 * <p>
 * Vanilla writes where an ore may appear as a height, and most of those heights
 * are absolute: copper at -16..112, gold at -64..32, diamond relative to a floor
 * that used to be at -64. A world that is thirty times deeper does not move any of
 * them, so the added space is stone. This is the half of the mod that is not the
 * dimensions.
 * <p>
 * It is applied to the height a placement resolved to rather than to the data that
 * named it, because the data cannot be enumerated: every mod that adds an ore has
 * its own, under ids this cannot know. Catching the value on the way through
 * reaches all of them without naming any.
 * <p>
 * <b>Two levers, not one.</b> Stretching a band without changing how many are
 * placed spreads the same veins over more rock, which is thinner ore, not deeper
 * ore. So the same place that moves a position also decides how many to emit -
 * one factor to undo the thinning, and one to make depth actually pay.
 */
public final class OreBands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicLong STRETCHED = new AtomicLong();
    private static final AtomicLong EMITTED = new AtomicLong();

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
     * Where a height written for vanilla's world lands in this one.
     * <p>
     * Identity at and above the anchor, so the surface and everything a player sees
     * without digging is untouched; below it, vanilla's range down to its old floor
     * is spread across the range down to this one. Piecewise linear and monotone:
     * an ore that was below another still is.
     */
    public static int stretch(int y) {
        int anchor = AltitudoConfig.ORE_ANCHOR.get();
        if (y >= anchor) {
            return y;
        }
        int floor = Dimensions.fromConfig().minY();
        int vanillaFloor = Dimensions.VANILLA.minY();
        if (floor >= vanillaFloor || anchor <= vanillaFloor) {
            return y;
        }
        double t = (double) (anchor - y) / (anchor - vanillaFloor);
        return (int) Math.round(anchor + t * (floor - anchor));
    }

    /** How much longer the band became, and therefore how much thinner it would be. */
    public static double thinning() {
        int anchor = AltitudoConfig.ORE_ANCHOR.get();
        int floor = Dimensions.fromConfig().minY();
        int vanillaFloor = Dimensions.VANILLA.minY();
        if (floor >= vanillaFloor || anchor <= vanillaFloor) {
            return 1.0;
        }
        return (double) (anchor - floor) / (anchor - vanillaFloor);
    }

    /**
     * How many to place where one was placed before.
     * <p>
     * The first factor is arithmetic: the band is this much longer, so this many are
     * needed to leave the ore as dense as it was. The second is a choice - how much
     * better the bottom is than the top - and it is the reason to dig rather than to
     * stay where the ore already was.
     *
     * @param y the stretched height, so the reward follows where the ore ended up
     */
    public static int copies(int y, RandomSource random) {
        double count = 1.0;
        if (AltitudoConfig.KEEP_ORE_DENSITY.get()) {
            count = thinning();
        }
        count *= reward(y);
        int whole = (int) count;
        // The fractional part is a probability, not a rounding error: a factor of 1.4
        // has to mean "one, and sometimes a second", or every band lands on the same
        // integer and the curve disappears.
        if (random.nextDouble() < count - whole) {
            whole++;
        }
        return Math.max(whole, 0);
    }

    /** 1 at the anchor, rising to the configured multiple at the floor. */
    private static double reward(int y) {
        double atFloor = AltitudoConfig.DEEP_ORE_BONUS.get();
        if (atFloor <= 1.0) {
            return 1.0;
        }
        int anchor = AltitudoConfig.ORE_ANCHOR.get();
        int floor = Dimensions.fromConfig().minY();
        if (y >= anchor || floor >= anchor) {
            return 1.0;
        }
        double t = Mth.clamp((double) (anchor - y) / (anchor - floor), 0.0, 1.0);
        return 1.0 + t * (atFloor - 1.0);
    }

    public static void note(int before, int after, int copies) {
        if (before != after) {
            STRETCHED.incrementAndGet();
        }
        EMITTED.addAndGet(copies);
    }

    /**
     * Zero stretched means every ore is still sitting where vanilla put it and the
     * added depth is bare stone - a world that generates perfectly well and has
     * nothing in it.
     */
    public static void report() {
        if (!enabled()) {
            LOGGER.info("Altitudo is not following ore bands (followOres = false).");
            return;
        }
        long stretched = STRETCHED.get();
        if (stretched == 0) {
            LOGGER.warn("Altitudo stretched no ore band. Everything below y={} is bare stone.",
                    Dimensions.VANILLA.minY());
        } else {
            LOGGER.info("Altitudo stretched {} ore placements into {} positions"
                    + " (band {}x longer, bottom {}x richer).",
                    stretched, EMITTED.get(), String.format("%.1f", thinning()),
                    AltitudoConfig.DEEP_ORE_BONUS.get());
        }
    }
}
