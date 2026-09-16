package io.github.capsicum0907.altitudo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.SharedConstants;
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

/**
 * A data pack that is computed rather than stored.
 * <p>
 * {@link PackResources#getResource} only has to return an {@link InputStream}. It
 * never has to be a file, which is what lets the depth be any number the config
 * says instead of one of a handful of prebuilt packs - the thing the mod this
 * replaces could not do, and the reason it shipped six jars.
 * <p>
 * Each entry names the file, the vanilla extent to read it as, and the extent to
 * write. A dimension this mod is not extending contributes no entries, so leaving
 * one alone needs no branch anywhere else.
 */
public final class GeneratedPack implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAMESPACE = "minecraft";

    /** One generated file: what to read, how to read it, and what to make it say. */
    private record Entry(ResourceLocation id, Dimensions vanilla, Dimensions target, Transform how) {
    }

    @FunctionalInterface
    private interface Transform {
        JsonObject apply(JsonObject source, Dimensions vanilla, Dimensions target);
    }

    private final PackLocationInfo location;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<ResourceLocation, byte[]> built = new HashMap<>();

    public GeneratedPack(PackLocationInfo location, Dimensions overworld, Optional<Dimensions> nether) {
        this.location = location;
        add("overworld", "overworld", Dimensions.VANILLA_OVERWORLD, overworld);
        nether.ifPresent(target -> add("the_nether", "nether", Dimensions.VANILLA_NETHER, target));
    }

    /**
     * Adds the two files one dimension needs.
     * <p>
     * ⚠ The two names differ for the nether: its dimension type is
     * {@code the_nether} and its noise settings are {@code nether}. Registry file
     * names are not one spelling per dimension, so both are passed rather than one
     * derived from the other.
     */
    private void add(String dimensionType, String noiseSettings, Dimensions vanilla, Dimensions target) {
        this.entries.add(new Entry(
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, "dimension_type/" + dimensionType + ".json"),
                vanilla, target, Rewrite::dimensionType));
        this.entries.add(new Entry(
                ResourceLocation.fromNamespaceAndPath(NAMESPACE,
                        "worldgen/noise_settings/" + noiseSettings + ".json"),
                vanilla, target, Rewrite::noiseSettings));
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation id) {
        if (packType != PackType.SERVER_DATA) {
            return null;
        }
        Entry entry = entryFor(id);
        if (entry == null) {
            return null;
        }
        byte[] bytes = this.built.computeIfAbsent(id, key -> build(entry));
        return () -> new ByteArrayInputStream(bytes);
    }

    @Nullable
    private Entry entryFor(ResourceLocation id) {
        for (Entry entry : this.entries) {
            if (entry.id().equals(id)) {
                return entry;
            }
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
    private byte[] build(Entry entry) {
        // ServerPacksSource#createVanillaPackSource is marked @VisibleForTesting.
        // It is the only public way to read the built-in data without a running
        // server; noted here so the reason is on record if it ever moves.
        try (VanillaPackResources vanilla = ServerPacksSource.createVanillaPackSource()) {
            IoSupplier<InputStream> source = vanilla.getResource(PackType.SERVER_DATA, entry.id());
            if (source == null) {
                throw new IllegalStateException("vanilla has no " + entry.id());
            }
            JsonObject original;
            try (InputStream in = source.get()) {
                original = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            Dimensions target = entry.target();
            JsonObject result = entry.how().apply(original, entry.vanilla(), target);
            LOGGER.info("Altitudo rewrote {}: generating {}..{}, box up to {}, sea level {}.",
                    entry.id(), target.minY(), target.minY() + target.height() - 1,
                    target.topY(), target.seaLevel());
            return result.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not read vanilla's " + entry.id(), e);
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
        for (Entry entry : this.entries) {
            if (entry.id().getPath().startsWith(prefix)) {
                IoSupplier<InputStream> supplier = getResource(packType, entry.id());
                if (supplier != null) {
                    output.accept(entry.id(), supplier);
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
                Optional.empty());
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
