package io.github.capsicum0907.altitudo;

import java.util.concurrent.atomic.AtomicLong;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.util.RandomSource;

/**
 * Carries the ore bands into the space the world gained.
 * <p>
 * Vanilla writes where an ore may appear as a height, and most of those heights
 * are absolute: copper at -16..112, gold at -64..32, diamond relative to a floor
 * that used to be at -64. A world thirty times deeper does not move any of them,
 * so the added space is stone. This is the half of the mod that is not the
 * dimensions.
 * <p>
 * It works on the height a placement resolved to, not on the data that named it,
 * because the data cannot be enumerated: every mod that adds an ore has its own,
 * under ids this cannot know. Catching the value on the way through reaches all of
 * them without naming any.
 *
 * <h2>⚠ Repeated, not stretched</h2>
 * The first attempt spread vanilla's band across the whole depth and then placed
 * as many more as it had lengthened, to keep the ore from thinning. Both halves
 * were wrong. Thirty-one copies of one position all landed in the same column,
 * because only the height was resampled - visibly a vertical stripe of ore two
 * thousand blocks tall. And this point cannot tell an ore from a geode or a
 * dungeon, so everything that uses {@code height_range} was multiplied too, until
 * one of them reached into a chunk that did not exist yet and the server stopped.
 * <p>
 * So: a placement is moved, never duplicated. One position in, one position out.
 * Vanilla's band is repeated down the world instead of being stretched across it,
 * which keeps the ore as dense as vanilla made it without placing anything extra,
 * and leaves depth to be expressed as which repeat gets chosen.
 */
public final class OreBands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicLong MOVED = new AtomicLong();
    private static final AtomicLong KEPT = new AtomicLong();

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
     * Moves a height into one of the repeats of vanilla's band below it.
     * <p>
     * At and above the anchor nothing moves, so the surface is untouched. Below it
     * the height keeps its position within vanilla's band and is dropped by a whole
     * number of band lengths - so an ore that belonged near the bottom of its band
     * still does, in whichever repeat it landed in.
     *
     * @return where this placement should go
     */
    public static int place(int y, RandomSource random) {
        int anchor = AltitudoConfig.ORE_ANCHOR.get();
        if (y >= anchor) {
            KEPT.incrementAndGet();
            return y;
        }
        int floor = Dimensions.fromConfig().minY();
        int band = anchor - Dimensions.VANILLA.minY();
        if (band <= 0 || floor >= Dimensions.VANILLA.minY()) {
            KEPT.incrementAndGet();
            return y;
        }
        int repeats = (anchor - floor) / band;
        if (repeats <= 1) {
            KEPT.incrementAndGet();
            return y;
        }
        int chosen = chooseRepeat(repeats, random);
        int moved = y - chosen * band;
        if (moved < floor) {
            KEPT.incrementAndGet();
            return y;
        }
        if (chosen > 0) {
            MOVED.incrementAndGet();
        } else {
            KEPT.incrementAndGet();
        }
        return moved;
    }

    /**
     * Which repeat this one goes to. Even at a bonus of 1; with a higher bonus the
     * deeper repeats are drawn more often, which is the whole of "deeper is richer"
     * - no extra ore is placed, it is only placed further down.
     */
    private static int chooseRepeat(int repeats, RandomSource random) {
        double bonus = AltitudoConfig.DEEP_ORE_BONUS.get();
        if (bonus <= 1.0) {
            return random.nextInt(repeats);
        }
        // Weight rises linearly from 1 at the top repeat to bonus at the bottom one.
        // Drawn by inverting the cumulative weight rather than by building a table,
        // so the number of repeats costs nothing.
        double total = repeats * (1.0 + bonus) / 2.0;
        double pick = random.nextDouble() * total;
        double step = (bonus - 1.0) / Math.max(repeats - 1, 1);
        double weight = 1.0;
        double sum = 0.0;
        for (int i = 0; i < repeats; i++) {
            sum += weight;
            if (pick < sum) {
                return i;
            }
            weight += step;
        }
        return repeats - 1;
    }

    /**
     * Zero moved means every ore is still where vanilla put it and the added depth
     * is bare stone - a world that generates perfectly well and has nothing in it.
     */
    public static void report() {
        if (!enabled()) {
            LOGGER.info("Altitudo is not following ore bands (followOres = false).");
            return;
        }
        long moved = MOVED.get();
        if (moved == 0) {
            LOGGER.warn("Altitudo moved no ore placement. Everything below y={} is bare stone.",
                    Dimensions.VANILLA.minY());
        } else {
            LOGGER.info("Altitudo moved {} of {} placements into deeper repeats of their band.",
                    moved, moved + KEPT.get());
        }
    }
}
