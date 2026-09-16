package io.github.capsicum0907.altitudo;

import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

public final class Anchors {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SPAGHETTI_FROM_Y = -64;
    private static final int SPAGHETTI_TO_Y = 320;
    private static final double SPAGHETTI_FROM = 8.0;
    private static final double SPAGHETTI_TO = -40.0;

    private static final double NOODLE_MIN = -60.0;
    private static final double NOODLE_MAX = 321.0;

    public static final int DEEP_FALLBACK_LEVEL = -54;

    private static final AtomicInteger MOVED = new AtomicInteger();
    private static final AtomicInteger PICKERS = new AtomicInteger();

    private Anchors() {
    }

    public static boolean extendingCaves() {
        return enabled();
    }

    public static void noteFluidPicker() {
        PICKERS.incrementAndGet();
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

    public static int gradientFromY(int fromY, int toY, double fromValue, double toValue) {
        if (!matches(fromY, toY, fromValue, toValue) || !enabled()) {
            return fromY;
        }
        return floor();
    }

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

    public static double rangeMin(double minInclusive, double maxExclusive) {
        if (minInclusive != NOODLE_MIN || maxExclusive != NOODLE_MAX || !enabled()) {
            return minInclusive;
        }
        LOGGER.info("Altitudo opened the noodle carver from y={} down to y={}.",
                minInclusive, floor());
        MOVED.incrementAndGet();
        return floor();
    }

    public static void report() {
        int moved = MOVED.get();
        if (!enabled()) {
            LOGGER.info("Altitudo is not extending caves (extendCaves = false).");
        } else if (moved == 0) {
            LOGGER.warn("Altitudo moved no cave anchors. The density functions are not the"
                    + " ones this was written against; below y={} there will be voids but no"
                    + " cave systems.", Dimensions.VANILLA_OVERWORLD.minY());
        } else {
            LOGGER.info("Altitudo moved {} cave anchors and handed {} dimension(s)"
                    + " back to the aquifer below y={}.",
                    moved, PICKERS.get(), DEEP_FALLBACK_LEVEL);
        }
    }
}
