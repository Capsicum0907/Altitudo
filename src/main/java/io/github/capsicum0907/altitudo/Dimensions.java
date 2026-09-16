package io.github.capsicum0907.altitudo;

public record Dimensions(int minY, int height, int seaLevel, int roofGap, SlideShape slides) {

    public record SlideShape(int floorFrom, int floorTo, int ceilingFrom, int ceilingTo) {
        public static final SlideShape OVERWORLD = new SlideShape(0, 24, -80, -64);
        public static final SlideShape NETHER = new SlideShape(-8, 24, -24, 0);
    }

    public static final Dimensions VANILLA_OVERWORLD =
            new Dimensions(-64, 384, 63, 0, SlideShape.OVERWORLD);

    public static final Dimensions VANILLA_NETHER =
            new Dimensions(0, 128, 32, 128, SlideShape.NETHER);

    public int boxHeight() {
        return this.height + this.roofGap;
    }

    public int logicalHeight() {
        return this.height;
    }

    public int floorSlideFrom() {
        return this.minY + this.slides.floorFrom();
    }

    public int floorSlideTo() {
        return this.minY + this.slides.floorTo();
    }

    public int ceilingSlideFrom() {
        return this.minY + this.height + this.slides.ceilingFrom();
    }

    public int ceilingSlideTo() {
        return this.minY + this.height + this.slides.ceilingTo();
    }

    public int topY() {
        return this.minY + this.boxHeight() - 1;
    }

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
