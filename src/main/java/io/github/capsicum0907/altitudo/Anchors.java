package io.github.capsicum0907.altitudo;

import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

public final class Anchors {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int DEEP_FALLBACK_LEVEL = -54;

    private static final AtomicInteger PICKERS = new AtomicInteger();

    private Anchors() {
    }

    public static boolean extendingCaves() {
        // Reached from a constructor that can run before the config is readable.
        try {
            return AltitudoConfig.EXTEND_CAVES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void noteFluidPicker() {
        PICKERS.incrementAndGet();
    }

    public static void report() {
        if (!extendingCaves()) {
            LOGGER.info("Altitudo is not extending caves (extendCaves = false).");
        } else {
            LOGGER.info("Altitudo handed {} dimension(s) back to the aquifer below y={}.",
                    PICKERS.get(), DEEP_FALLBACK_LEVEL);
        }
    }
}
