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

public final class GeneratedPack implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VANILLA = "minecraft";
    private static final String NAMESPACE = Altitudo.MODID;

    private record Entry(ResourceLocation id, ResourceLocation source, Dimensions vanilla,
            Dimensions target, Transform how) {
    }

    @FunctionalInterface
    private interface Transform {
        JsonObject apply(JsonObject source, Dimensions vanilla, Dimensions target);
    }

    private final PackLocationInfo location;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<ResourceLocation, byte[]> built = new HashMap<>();
    private byte[] preset;
    private final ResourceLocation presetId =
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "worldgen/world_preset/altitudo.json");

    public GeneratedPack(PackLocationInfo location, Dimensions overworld, Optional<Dimensions> nether) {
        this.location = location;
        add("overworld", "overworld", Dimensions.VANILLA_OVERWORLD, overworld);
        if (Anchors.extendingCaves()) {
            addCaves(Dimensions.VANILLA_OVERWORLD, overworld);
        }
        nether.ifPresent(target -> add("the_nether", "nether", Dimensions.VANILLA_NETHER, target));
        this.preset = Preset.json(nether.isPresent());
    }

    private void add(String dimensionType, String noiseSettings, Dimensions vanilla, Dimensions target) {
        entry("dimension_type/" + dimensionType + ".json", vanilla, target, Rewrite::dimensionType);
        entry("worldgen/noise_settings/" + noiseSettings + ".json", vanilla, target,
                Rewrite::noiseSettings);
    }

    private void addCaves(Dimensions vanilla, Dimensions target) {
        entry("worldgen/density_function/overworld/caves/spaghetti_2d.json", vanilla, target,
                Rewrite::caveSpaghetti);
        entry("worldgen/density_function/overworld/caves/noodle.json", vanilla, target,
                Rewrite::caveNoodle);
    }

    private void entry(String path, Dimensions vanilla, Dimensions target, Transform how) {
        this.entries.add(new Entry(
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
                ResourceLocation.fromNamespaceAndPath(VANILLA, path),
                vanilla, target, how));
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation id) {
        if (packType != PackType.SERVER_DATA) {
            return null;
        }
        if (this.presetId.equals(id)) {
            byte[] bytes = this.preset;
            return () -> new ByteArrayInputStream(bytes);
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

    private byte[] build(Entry entry) {
        try (VanillaPackResources vanilla = ServerPacksSource.createVanillaPackSource()) {
            IoSupplier<InputStream> source = vanilla.getResource(PackType.SERVER_DATA, entry.source());
            if (source == null) {
                throw new IllegalStateException("vanilla has no " + entry.source());
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
            throw new IllegalStateException("could not read vanilla's " + entry.source(), e);
        }
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput output) {
        if (packType != PackType.SERVER_DATA || !NAMESPACE.equals(namespace)) {
            return;
        }
        // Registries are listed by directory: without the separator,
        // "worldgen/noise" also answers with worldgen/noise_settings.
        String prefix = path.endsWith("/") ? path : path + "/";
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(this.presetId);
        for (Entry entry : this.entries) {
            ids.add(entry.id());
        }
        for (ResourceLocation id : ids) {
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
