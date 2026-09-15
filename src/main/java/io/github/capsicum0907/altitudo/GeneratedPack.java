package io.github.capsicum0907.altitudo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.SharedConstants;

/**
 * A data pack that is computed rather than stored.
 * <p>
 * {@link PackResources#getResource} only has to return an {@link InputStream}. It
 * never has to be a file, which is what lets the depth be any number the config
 * says instead of one of a handful of prebuilt packs - the thing the mod this
 * replaces could not do, and the reason it shipped six jars.
 */
public final class GeneratedPack implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAMESPACE = "minecraft";

    private static final ResourceLocation DIMENSION_TYPE =
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "dimension_type/overworld.json");
    private static final ResourceLocation NOISE_SETTINGS =
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "worldgen/noise_settings/overworld.json");

    private final PackLocationInfo location;
    private final Dimensions target;
    private final Map<ResourceLocation, byte[]> built = new HashMap<>();

    public GeneratedPack(PackLocationInfo location, Dimensions target) {
        this.location = location;
        this.target = target;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation id) {
        if (packType != PackType.SERVER_DATA) {
            return null;
        }
        BiFunction<JsonObject, Dimensions, JsonObject> rewrite = rewriteFor(id);
        if (rewrite == null) {
            return null;
        }
        byte[] bytes = this.built.computeIfAbsent(id, key -> build(key, rewrite));
        return () -> new ByteArrayInputStream(bytes);
    }

    @Nullable
    private static BiFunction<JsonObject, Dimensions, JsonObject> rewriteFor(ResourceLocation id) {
        if (DIMENSION_TYPE.equals(id)) {
            return Rewrite::dimensionType;
        }
        if (NOISE_SETTINGS.equals(id)) {
            return Rewrite::noiseSettings;
        }
        return null;
    }

    /**
     * Reads what vanilla ships for this file and changes the numbers in it.
     * <p>
     * The source is vanilla's own pack rather than the one below this in the stack,
     * because a pack cannot see past itself. Another data pack that reshapes terrain
     * therefore wins or loses by pack order, the same as it would against any other
     * pack - it is not silently merged.
     */
    private byte[] build(ResourceLocation id, BiFunction<JsonObject, Dimensions, JsonObject> rewrite) {
        // ServerPacksSource#createVanillaPackSource is marked @VisibleForTesting.
        // It is the only public way to read the built-in data without a running
        // server; noted here so the reason is on record if it ever moves.
        try (VanillaPackResources vanilla = ServerPacksSource.createVanillaPackSource()) {
            IoSupplier<InputStream> source = vanilla.getResource(PackType.SERVER_DATA, id);
            if (source == null) {
                throw new IllegalStateException("vanilla has no " + id);
            }
            JsonObject original;
            try (InputStream in = source.get()) {
                original = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            JsonObject result = rewrite.apply(original, this.target);
            LOGGER.info("Altitudo rewrote {} for {}..{} (height {}, sea level {}).",
                    id, this.target.minY(), this.target.topY(),
                    this.target.height(), this.target.seaLevel());
            return result.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not read vanilla's " + id, e);
        }
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput output) {
        if (packType != PackType.SERVER_DATA || !NAMESPACE.equals(namespace)) {
            return;
        }
        // The separator is load-bearing. Registries are listed by their directory,
        // and a bare prefix test answers "worldgen/noise" with the file that lives
        // in "worldgen/noise_settings" - which is then read as a noise parameter
        // named "settings/overworld" and fails on a field it was never going to
        // have. Valid JSON handed to the wrong registry.
        String prefix = path.endsWith("/") ? path : path + "/";
        for (ResourceLocation id : new ResourceLocation[] { DIMENSION_TYPE, NOISE_SETTINGS }) {
            if (id.getPath().startsWith(prefix)) {
                IoSupplier<InputStream> supplier = getResource(packType, id);
                if (supplier != null) {
                    output.accept(id, supplier);
                }
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of(NAMESPACE) : Set.of();
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (!PackMetadataSection.TYPE.getMetadataSectionName()
                .equals(deserializer.getMetadataSectionName())) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T section = (T) new PackMetadataSection(
                Component.literal("Altitudo"),
                SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA),
                java.util.Optional.empty());
        return section;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {
    }
}
