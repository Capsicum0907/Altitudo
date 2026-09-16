package io.github.capsicum0907.altitudo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Rewrites the numbers inside vanilla's own worldgen files rather than shipping
 * copies of them.
 * <p>
 * A copy of {@code noise_settings/overworld.json} is 38 KB of terrain shaping that
 * has nothing to do with this mod's subject. Carrying it means inheriting every
 * future change to it as a silent divergence, and overwriting whatever another
 * data pack did to it. Reading what is already there and changing a few numbers
 * does not.
 * <p>
 * Every rewrite is counted. A transform that matches nothing still produces valid
 * JSON and a world that generates, so "found no match" has to be an error rather
 * than a quiet pass-through - otherwise the failure shows up hours later as a
 * world with no caves.
 * <p>
 * ⚠ The count is not a number typed here. The overworld carries the slide pair
 * twice and the nether once, so a literal would have to be different per file and
 * would be exactly the kind of value this class exists to keep out of the code.
 * What is required is structural: at least one floor slide and at least one
 * ceiling slide, matched by the values vanilla computed for that dimension.
 */
public final class Rewrite {
    private Rewrite() {
    }

    /** {@code dimension_type}: the box the world is allowed to fill. */
    public static JsonObject dimensionType(JsonObject source, Dimensions vanilla, Dimensions target) {
        JsonObject out = source.deepCopy();
        replaceInt(out, "min_y", target.minY());
        replaceInt(out, "height", target.boxHeight());
        replaceInt(out, "logical_height", target.logicalHeight());
        return out;
    }

    /**
     * {@code noise_settings}: the range terrain is generated into, the fluid line,
     * and the two slides that decide where rock stops.
     * <p>
     * The sea level is only written when it actually differs, because the nether's
     * is not ours to move: twelve surface rules place the lava shore at absolute
     * heights between 30 and 35, and they would stay behind.
     */
    public static JsonObject noiseSettings(JsonObject source, Dimensions vanilla, Dimensions target) {
        JsonObject out = source.deepCopy();

        JsonObject noise = out.getAsJsonObject("noise");
        if (noise == null) {
            throw new IllegalStateException("noise_settings has no \"noise\" block");
        }
        replaceInt(noise, "min_y", target.minY());
        replaceInt(noise, "height", target.height());
        if (target.seaLevel() != vanilla.seaLevel()) {
            replaceInt(out, "sea_level", target.seaLevel());
        }

        Slides found = retargetSlides(out, vanilla, target);
        if (found.floor() == 0 || found.ceiling() == 0) {
            throw new IllegalStateException(
                    "expected to find both slides to retarget, found " + found.floor()
                            + " floor and " + found.ceiling() + " ceiling. The terrain shaping is"
                            + " not the one this was written against, and leaving it alone would"
                            + " produce a taller world of solid rock with no caves.");
        }
        return out;
    }

    /** How many of each kind were changed, so a missing kind can be told from a missing file. */
    private record Slides(int floor, int ceiling) {
        Slides plus(Slides other) {
            return new Slides(this.floor + other.floor, this.ceiling + other.ceiling);
        }

        static final Slides NONE = new Slides(0, 0);
    }

    /**
     * Moves the floor and ceiling slides to the new extent.
     * <p>
     * Vanilla writes them as literals - the overworld's {@code (-64, -40)} and
     * {@code (240, 256)}, the nether's {@code (-8, 24)} and {@code (104, 128)} -
     * but each is its dimension's own {@code minY} and {@code minY + height} with
     * fixed offsets. Matching on the pair vanilla would have computed is what makes
     * the requirement above meaningful: anything else in the file that happens to
     * be a gradient is left alone.
     */
    private static Slides retargetSlides(JsonElement element, Dimensions vanilla, Dimensions target) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            Slides here = Slides.NONE;
            if (isSlide(object, vanilla.floorSlideFrom(), vanilla.floorSlideTo())) {
                set(object, target.floorSlideFrom(), target.floorSlideTo());
                here = new Slides(1, 0);
            } else if (isSlide(object, vanilla.ceilingSlideFrom(), vanilla.ceilingSlideTo())) {
                set(object, target.ceilingSlideFrom(), target.ceilingSlideTo());
                here = new Slides(0, 1);
            }
            for (var entry : object.entrySet()) {
                here = here.plus(retargetSlides(entry.getValue(), vanilla, target));
            }
            return here;
        }
        if (element.isJsonArray()) {
            Slides here = Slides.NONE;
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                here = here.plus(retargetSlides(child, vanilla, target));
            }
            return here;
        }
        return Slides.NONE;
    }

    private static boolean isSlide(JsonObject object, int fromY, int toY) {
        if (!object.has("type") || !object.has("from_y") || !object.has("to_y")) {
            return false;
        }
        return "minecraft:y_clamped_gradient".equals(object.get("type").getAsString())
                && object.get("from_y").getAsInt() == fromY
                && object.get("to_y").getAsInt() == toY;
    }

    private static void set(JsonObject object, int fromY, int toY) {
        object.addProperty("from_y", fromY);
        object.addProperty("to_y", toY);
    }

    private static void replaceInt(JsonObject object, String field, int value) {
        if (!object.has(field)) {
            throw new IllegalStateException("expected field \"" + field + "\" to replace");
        }
        object.addProperty(field, value);
    }
}
