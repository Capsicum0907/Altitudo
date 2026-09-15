package io.github.capsicum0907.altitudo;

/**
 * The configured extent, checked once and then answered from.
 * <p>
 * Every number the generated files need is derived here, so the five places that
 * have to agree cannot drift apart. The slide anchors are the interesting ones:
 * vanilla writes them as literals, but they are {@code minY} and
 * {@code minY + height} with fixed offsets, so they follow a taller world for
 * free once they are expressed that way.
 */
public record Dimensions(int minY, int height, int seaLevel) {
    /** Vanilla's, used when the config is unusable and as the pair to rewrite from. */
    public static final Dimensions VANILLA = new Dimensions(-64, 384, 63);

    /**
     * Where the ground stops being generated and becomes solid rock. Without moving
     * this, a taller world is a taller block of stone: below the low anchor the
     * gradient pins density to a constant positive value, so nothing is carved.
     */
    public int floorSlideFrom() {
        return this.minY;
    }

    public int floorSlideTo() {
        return this.minY + 24;
    }

    /** Where the ground is pulled to air near the top. */
    public int ceilingSlideFrom() {
        return this.minY + this.height - 80;
    }

    public int ceilingSlideTo() {
        return this.minY + this.height - 64;
    }

    public int topY() {
        return this.minY + this.height - 1;
    }

    /**
     * Portal and chorus fruit destinations only - not the world's height and not the
     * build limit. Matching {@code height} means neither is capped below the top.
     */
    public int logicalHeight() {
        return this.height;
    }

    /**
     * Reads the config and refuses anything the game would reject later, where the
     * failure would surface as a crash inside world creation instead of a sentence.
     *
     * @throws IllegalStateException with what was wrong and what the limit is
     */
    public static Dimensions fromConfig() {
        int minY = AltitudoConfig.MIN_Y.get();
        int height = AltitudoConfig.HEIGHT.get();
        int seaLevel = AltitudoConfig.SEA_LEVEL.get();

        require(minY % AltitudoConfig.SECTION == 0,
                "minY must be a multiple of " + AltitudoConfig.SECTION + ", got " + minY);
        require(height % AltitudoConfig.SECTION == 0,
                "height must be a multiple of " + AltitudoConfig.SECTION + ", got " + height);
        require(minY + height <= AltitudoConfig.LIMIT_MAX_Y + 1,
                "minY + height may not exceed " + (AltitudoConfig.LIMIT_MAX_Y + 1)
                        + ", got " + (minY + height));
        require(seaLevel > minY && seaLevel < minY + height,
                "seaLevel " + seaLevel + " is outside the world "
                        + minY + ".." + (minY + height - 1));
        require(height >= 128,
                "height must leave room for the slides, got " + height);

        return new Dimensions(minY, height, seaLevel);
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }
}
