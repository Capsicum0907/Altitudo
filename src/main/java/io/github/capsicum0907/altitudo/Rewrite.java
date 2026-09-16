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

    public static final String VANILLA_CAVES = "minecraft:overworld/caves/";
    public static final String ALTITUDO_CAVES = Altitudo.MODID + ":overworld/caves/";
    private static final String[] CAVE_FUNCTIONS = { "spaghetti_2d", "noodle" };

    private static final int SPAGHETTI_FROM_Y = -64;
    private static final int SPAGHETTI_TO_Y = 320;
    private static final double SPAGHETTI_FROM = 8.0;
    private static final double SPAGHETTI_TO = -40.0;
    private static final double NOODLE_MIN = -60.0;
    private static final double NOODLE_MAX = 321.0;
    private static final int NOODLE_RANGES = 4;

    public static JsonObject caveSpaghetti(JsonObject source, Dimensions vanilla, Dimensions target) {
        JsonObject out = source.deepCopy();
        double slope = (SPAGHETTI_TO - SPAGHETTI_FROM) / (SPAGHETTI_TO_Y - SPAGHETTI_FROM_Y);
        double moved = SPAGHETTI_TO + slope * (target.minY() - SPAGHETTI_TO_Y);
        int found = carryGradient(out, target.minY(), moved);
        if (found != 1) {
            throw new IllegalStateException("expected one cave gradient to carry down, found " + found);
        }
        return out;
    }

    public static JsonObject caveNoodle(JsonObject source, Dimensions vanilla, Dimensions target) {
        JsonObject out = source.deepCopy();
        int found = openRange(out, target.minY());
        if (found != NOODLE_RANGES) {
            throw new IllegalStateException("expected " + NOODLE_RANGES
                    + " noodle ranges to open, found " + found);
        }
        return out;
    }

    private static int carryGradient(JsonElement element, int minY, double fromValue) {
        return walk(element, object -> {
            if (!isSlide(object, SPAGHETTI_FROM_Y, SPAGHETTI_TO_Y)
                    || object.get("from_value").getAsDouble() != SPAGHETTI_FROM
                    || object.get("to_value").getAsDouble() != SPAGHETTI_TO) {
                return 0;
            }
            object.addProperty("from_y", minY);
            object.addProperty("from_value", fromValue);
            return 1;
        }, e -> carryGradient(e, minY, fromValue));
    }

    private static int openRange(JsonElement element, int minY) {
        return walk(element, object -> {
            if (!"minecraft:range_choice".equals(typeOf(object))
                    || !object.has("min_inclusive") || !object.has("max_exclusive")
                    || object.get("min_inclusive").getAsDouble() != NOODLE_MIN
                    || object.get("max_exclusive").getAsDouble() != NOODLE_MAX) {
                return 0;
            }
            object.addProperty("min_inclusive", (double) minY);
            return 1;
        }, e -> openRange(e, minY));
    }

    private static int walk(JsonElement element, java.util.function.ToIntFunction<JsonObject> here,
            java.util.function.ToIntFunction<JsonElement> recurse) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            int found = here.applyAsInt(object);
            for (var entry : object.entrySet()) {
                found += recurse.applyAsInt(entry.getValue());
            }
            return found;
        }
        if (element.isJsonArray()) {
            int found = 0;
            for (JsonElement child : element.getAsJsonArray()) {
                found += recurse.applyAsInt(child);
            }
            return found;
        }
        return 0;
    }

    private static String typeOf(JsonObject object) {
        return object.has("type") && object.get("type").isJsonPrimitive()
                ? object.get("type").getAsString() : null;
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

        if (Anchors.extendingCaves() && Dimensions.VANILLA_OVERWORLD.equals(vanilla)) {
            for (String name : CAVE_FUNCTIONS) {
                int swapped = repoint(out, VANILLA_CAVES + name, ALTITUDO_CAVES + name);
                if (swapped != 1) {
                    throw new IllegalStateException("expected one reference to " + VANILLA_CAVES
                            + name + " to repoint, found " + swapped);
                }
            }
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

    private static int repoint(JsonElement element, String from, String to) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            int swapped = 0;
            for (var entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && from.equals(value.getAsString())) {
                    entry.setValue(new com.google.gson.JsonPrimitive(to));
                    swapped++;
                } else {
                    swapped += repoint(value, from, to);
                }
            }
            return swapped;
        }
        if (element.isJsonArray()) {
            int swapped = 0;
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement value = array.get(i);
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && from.equals(value.getAsString())) {
                    array.set(i, new com.google.gson.JsonPrimitive(to));
                    swapped++;
                } else {
                    swapped += repoint(value, from, to);
                }
            }
            return swapped;
        }
        return 0;
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
