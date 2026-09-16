package io.github.capsicum0907.altitudo;

/**
 * The configured extent of one dimension, checked once and then answered from.
 * <p>
 * Every number the generated files need is derived here, so the places that have
 * to agree cannot drift apart.
 *
 * <h2>⭐ Vanilla's two dimensions are one formula</h2>
 * Reading the two shipped files side by side:
 *
 * <pre>
 *              min_y   generated   box    roof gap   logical_height
 *  overworld     -64         384    384          0              384
 *  nether          0         128    256        128              128
 * </pre>
 *
 * Both are {@code box = generated + roof gap} and {@code logical_height =
 * generated}. The Nether is not a different shape; it is the same shape with a
 * gap above the roof, which is the empty space players build in.
 * <p>
 * ⚠ That second identity is load-bearing rather than decorative.
 * {@code logical_height} caps where a portal may be placed, and
 * {@code PortalForcer} reads it as {@code min(maxBuild, minY + logicalHeight) -
 * 1}. Vanilla's 128 is not the number 128; it is "the roof". Keeping the literal
 * while lowering the floor puts the cap at {@code y = -1}, inside the lava sea,
 * and the fallback branch then refuses to build a portal at all.
 */
public record Dimensions(int minY, int height, int seaLevel, int roofGap, SlideShape slides) {

    /**
     * How far the two density gradients sit from each end of the generated range.
     * <p>
     * Vanilla writes them into the JSON as literals, but {@code NoiseRouterData}
     * builds them from the range: the floor pair is offset from {@code minY} and
     * the ceiling pair from {@code minY + height}. Holding the offsets rather than
     * the results is what lets one rewrite serve both dimensions, and what makes
     * "which pair is this" answerable when matching the file.
     */
    public record SlideShape(int floorFrom, int floorTo, int ceilingFrom, int ceilingTo) {
        /** {@code slideOverworld}: floor at minY and minY+24, ceiling 80 and 64 below the top. */
        public static final SlideShape OVERWORLD = new SlideShape(0, 24, -80, -64);

        /** {@code slideNetherLike}: floor 8 below minY and 24 above, ceiling 24 below the top and at it. */
        public static final SlideShape NETHER = new SlideShape(-8, 24, -24, 0);
    }

    /** Vanilla's overworld: the pair every overworld rewrite reads from. */
    public static final Dimensions VANILLA_OVERWORLD =
            new Dimensions(-64, 384, 63, 0, SlideShape.OVERWORLD);

    /** Vanilla's nether. The 128 of roof gap is the space above the bedrock. */
    public static final Dimensions VANILLA_NETHER =
            new Dimensions(0, 128, 32, 128, SlideShape.NETHER);

    /**
     * The box the world is allowed to hold, which is taller than what is generated
     * whenever there is a roof to build on top of.
     */
    public int boxHeight() {
        return this.height + this.roofGap;
    }

    /**
     * Portal and chorus fruit destinations only - not the world's height and not
     * the build limit. Equal to the generated height, so the cap lands on the roof
     * rather than in the empty space above it.
     */
    public int logicalHeight() {
        return this.height;
    }

    /**
     * Where the ground stops being generated and becomes solid rock. Without moving
     * this, a taller world is a taller block of stone: below the low anchor the
     * gradient pins density to a constant positive value, so nothing is carved.
     */
    public int floorSlideFrom() {
        return this.minY + this.slides.floorFrom();
    }

    public int floorSlideTo() {
        return this.minY + this.slides.floorTo();
    }

    /** Where the ground is pulled to air near the top. */
    public int ceilingSlideFrom() {
        return this.minY + this.height + this.slides.ceilingFrom();
    }

    public int ceilingSlideTo() {
        return this.minY + this.height + this.slides.ceilingTo();
    }

    /** The highest block the box can hold, roof gap included. */
    public int topY() {
        return this.minY + this.boxHeight() - 1;
    }

    /**
     * Reads the overworld's config and refuses anything the game would reject
     * later, where the failure would surface as a crash inside world creation
     * instead of a sentence.
     *
     * @throws IllegalStateException with what was wrong and what the limit is
     */
    public static Dimensions fromConfig() {
        Dimensions target = new Dimensions(AltitudoConfig.MIN_Y.get(), AltitudoConfig.HEIGHT.get(),
                AltitudoConfig.SEA_LEVEL.get(), 0, SlideShape.OVERWORLD);
        target.validate("overworld");
        require(target.seaLevel() > target.minY()
                && target.seaLevel() < target.minY() + target.height(),
                "seaLevel " + target.seaLevel() + " is outside the world "
                        + target.minY() + ".." + (target.minY() + target.height() - 1));
        return target;
    }

    /**
     * Reads the nether's config. Its sea level stays vanilla's 32 and is not a
     * setting: the surface rules place the lava shore with twelve absolute anchors
     * between 30 and 35, so moving it would leave them behind. The lava sea gets
     * deeper by lowering the floor, which is what was asked for anyway.
     */
    public static Dimensions netherFromConfig() {
        Dimensions target = new Dimensions(AltitudoConfig.NETHER_MIN_Y.get(),
                AltitudoConfig.NETHER_HEIGHT.get(), VANILLA_NETHER.seaLevel(),
                AltitudoConfig.NETHER_ROOF_GAP.get(), SlideShape.NETHER);
        target.validate("nether");
        require(target.minY() < target.seaLevel(),
                "netherMinY " + target.minY() + " is at or above the lava sea at "
                        + target.seaLevel() + ", which would leave no sea at all");
        return target;
    }

    private void validate(String which) {
        require(this.minY % AltitudoConfig.SECTION == 0,
                which + " minY must be a multiple of " + AltitudoConfig.SECTION
                        + ", got " + this.minY);
        require(this.height % AltitudoConfig.SECTION == 0,
                which + " height must be a multiple of " + AltitudoConfig.SECTION
                        + ", got " + this.height);
        require(this.roofGap % AltitudoConfig.SECTION == 0,
                which + " roof gap must be a multiple of " + AltitudoConfig.SECTION
                        + ", got " + this.roofGap);
        require(this.minY + this.boxHeight() <= AltitudoConfig.LIMIT_MAX_Y + 1,
                which + " minY + height + roof gap may not exceed "
                        + (AltitudoConfig.LIMIT_MAX_Y + 1) + ", got "
                        + (this.minY + this.boxHeight()));
        require(this.minY >= AltitudoConfig.LIMIT_MIN_Y,
                which + " minY may not be below " + AltitudoConfig.LIMIT_MIN_Y
                        + ", got " + this.minY);
        require(this.height >= 128,
                which + " height must leave room for the slides, got " + this.height);
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }
}
