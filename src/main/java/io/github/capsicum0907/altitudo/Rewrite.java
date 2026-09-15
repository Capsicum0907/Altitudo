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
 * data pack did to it. Reading what is already there and changing five numbers
 * does not.
 * <p>
 * Every rewrite is counted. A transform that matches nothing still produces valid
 * JSON and a world that generates, so "found no match" has to be an error rather
 * than a quiet pass-through - otherwise the failure shows up hours later as a
 * world with no caves.
 */
public final class Rewrite {
    /** What vanilla writes, and therefore what this expects to find. */
    private static final int VANILLA_SLIDES = 4;

    private Rewrite() {
    }

    /** {@code dimension_type/overworld.json}: the box the world is allowed to fill. */
    public static JsonObject dimensionType(JsonObject source, Dimensions target) {
        JsonObject out = source.deepCopy();
        replaceInt(out, "min_y", target.minY());
        replaceInt(out, "height", target.height());
        replaceInt(out, "logical_height", target.logicalHeight());
        return out;
    }

    /**
     * {@code noise_settings/overworld.json}: the range terrain is generated into,
     * the water line, and the two slides that decide where rock stops.
     */
    public static JsonObject noiseSettings(JsonObject source, Dimensions target) {
        JsonObject out = source.deepCopy();

        JsonObject noise = out.getAsJsonObject("noise");
        if (noise == null) {
            throw new IllegalStateException("noise_settings has no \"noise\" block");
        }
        replaceInt(noise, "min_y", target.minY());
        replaceInt(noise, "height", target.height());
        replaceInt(out, "sea_level", target.seaLevel());

        int slides = retargetSlides(out, target);
        if (slides != VANILLA_SLIDES) {
            throw new IllegalStateException(
                    "expected " + VANILLA_SLIDES + " y_clamped_gradient slides to retarget, found "
                            + slides + ". The terrain shaping is not the one this was written"
                            + " against, and leaving it alone would produce a taller world of"
                            + " solid rock with no caves.");
        }
        return out;
    }

    /**
     * Moves the floor and ceiling slides to the new extent.
     * <p>
     * Vanilla writes them as literals - {@code (-64, -40)} and {@code (240, 256)} -
     * but they are {@code minY} and {@code minY + height} with fixed offsets, taken
     * from {@code NoiseRouterData.slideOverworld}. Matching on the literal pair is
     * what makes the count above meaningful: anything else in the file that happens
     * to be a gradient is left alone.
     *
     * @return how many were changed
     */
    private static int retargetSlides(JsonElement element, Dimensions target) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            int changed = isSlide(object, Dimensions.VANILLA.minY(), Dimensions.VANILLA.minY() + 24)
                    ? set(object, target.floorSlideFrom(), target.floorSlideTo())
                    : isSlide(object,
                            Dimensions.VANILLA.minY() + Dimensions.VANILLA.height() - 80,
                            Dimensions.VANILLA.minY() + Dimensions.VANILLA.height() - 64)
                                    ? set(object, target.ceilingSlideFrom(), target.ceilingSlideTo())
                                    : 0;
            for (var entry : object.entrySet()) {
                changed += retargetSlides(entry.getValue(), target);
            }
            return changed;
        }
        if (element.isJsonArray()) {
            int changed = 0;
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                changed += retargetSlides(child, target);
            }
            return changed;
        }
        return 0;
    }

    private static boolean isSlide(JsonObject object, int fromY, int toY) {
        if (!object.has("type") || !object.has("from_y") || !object.has("to_y")) {
            return false;
        }
        return "minecraft:y_clamped_gradient".equals(object.get("type").getAsString())
                && object.get("from_y").getAsInt() == fromY
                && object.get("to_y").getAsInt() == toY;
    }

    private static int set(JsonObject object, int fromY, int toY) {
        object.addProperty("from_y", fromY);
        object.addProperty("to_y", toY);
        return 1;
    }

    private static void replaceInt(JsonObject object, String field, int value) {
        if (!object.has(field)) {
            throw new IllegalStateException("expected field \"" + field + "\" to replace");
        }
        object.addProperty(field, value);
    }
}
