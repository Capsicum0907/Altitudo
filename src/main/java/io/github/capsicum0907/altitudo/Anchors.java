package io.github.capsicum0907.altitudo;

import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * Moves the y bounds that decide where caves may exist.
 * <p>
 * These live in density functions that vanilla builds in code and never writes to
 * disk, so there is no file to read and rewrite. They are reached the same way the
 * ore bands are: not by naming the data, but by catching the value on its way
 * through - the mod already answers "unreadable" with a mapping rather than a copy.
 * <p>
 * ⚠ The key is the whole tuple, not the pair of y values. {@code (-64, -40)} alone
 * also appears in the amplified and large-biomes presets, which have their own
 * slides and must not be moved from here. Checked against every shipped
 * {@code noise_settings}: the tuples below appear in none of them, and the
 * functions that hold them are referenced only by the three overworld presets.
 * The Nether and the End carry {@code depth: 0.0} and reference no cave function,
 * so they cannot be reached by this at all.
 */
public final class Anchors {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** {@code overworld/caves/spaghetti_2d}: how far this height is from the band caves live in. */
    private static final int SPAGHETTI_FROM_Y = -64;
    private static final int SPAGHETTI_TO_Y = 320;
    private static final double SPAGHETTI_FROM = 8.0;
    private static final double SPAGHETTI_TO = -40.0;

    /** {@code overworld/caves/noodle}: outside this the noodle carver is switched off. */
    private static final double NOODLE_MIN = -60.0;
    private static final double NOODLE_MAX = 321.0;

    private static final AtomicInteger MOVED = new AtomicInteger();

    private Anchors() {
    }

    private static boolean enabled() {
        // Reached from a constructor that can run before the config is readable.
        try {
            return AltitudoConfig.EXTEND_CAVES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int floor() {
        return Dimensions.fromConfig().minY();
    }

    /**
     * @return the y this gradient should start from, or {@code fromY} unchanged
     */
    public static int gradientFromY(int fromY, int toY, double fromValue, double toValue) {
        if (!matches(fromY, toY, fromValue, toValue) || !enabled()) {
            return fromY;
        }
        return floor();
    }

    /**
     * The gradient reads "how many eights of a block above the floor", so carrying it
     * down means keeping its slope and asking what the value would have been there.
     * Anything else changes how thick the band is, not where it reaches.
     */
    public static double gradientFromValue(int fromY, int toY, double fromValue, double toValue) {
        if (!matches(fromY, toY, fromValue, toValue) || !enabled()) {
            return fromValue;
        }
        double slope = (toValue - fromValue) / (toY - fromY);
        double moved = toValue + slope * (floor() - toY);
        LOGGER.info("Altitudo carried the cave band from y={} down to y={} (value {} -> {}).",
                fromY, floor(), fromValue, moved);
        MOVED.incrementAndGet();
        return moved;
    }

    private static boolean matches(int fromY, int toY, double fromValue, double toValue) {
        return fromY == SPAGHETTI_FROM_Y && toY == SPAGHETTI_TO_Y
                && fromValue == SPAGHETTI_FROM && toValue == SPAGHETTI_TO;
    }

    /** @return the lowest y the noodle carver is allowed to run at */
    public static double rangeMin(double minInclusive, double maxExclusive) {
        if (minInclusive != NOODLE_MIN || maxExclusive != NOODLE_MAX || !enabled()) {
            return minInclusive;
        }
        LOGGER.info("Altitudo opened the noodle carver from y={} down to y={}.",
                minInclusive, floor());
        MOVED.incrementAndGet();
        return floor();
    }

    /**
     * Two moves are expected: the spaghetti band and the noodle range. Zero means the
     * shapes this keys on are not the ones running, and the space below the vanilla
     * floor would be left to 3D noise alone - large shapeless voids that still look
     * like the mod worked.
     */
    public static void report() {
        int moved = MOVED.get();
        if (!enabled()) {
            LOGGER.info("Altitudo is not extending caves (extendCaves = false).");
        } else if (moved == 0) {
            LOGGER.warn("Altitudo moved no cave anchors. The density functions are not the"
                    + " ones this was written against; below y={} there will be voids but no"
                    + " cave systems.", Dimensions.VANILLA.minY());
        } else {
            LOGGER.info("Altitudo moved {} cave anchors.", moved);
        }
    }
}
