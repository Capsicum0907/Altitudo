package io.github.capsicum0907.altitudo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class Rewrite {
    private Rewrite() {
    }

    public static JsonObject dimensionType(JsonObject source, Dimensions vanilla, Dimensions target) {
        JsonObject out = source.deepCopy();
        replaceInt(out, "min_y", target.minY());
        replaceInt(out, "height", target.boxHeight());
        replaceInt(out, "logical_height", target.logicalHeight());
        return out;
    }

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

    private record Slides(int floor, int ceiling) {
        Slides plus(Slides other) {
            return new Slides(this.floor + other.floor, this.ceiling + other.ceiling);
        }

        static final Slides NONE = new Slides(0, 0);
    }

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
