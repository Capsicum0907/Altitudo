package io.github.capsicum0907.altitudo;

import java.util.List;
import java.util.Optional;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.neoforge.event.AddPackFindersEvent;

public final class PackRegistration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ID = "altitudo/world_extent";

    private PackRegistration() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }

        Dimensions target;
        Optional<Dimensions> nether;
        try {
            target = Dimensions.fromConfig();
            nether = AltitudoConfig.EXTEND_NETHER.get()
                    ? Optional.of(Dimensions.netherFromConfig())
                    : Optional.empty();
        } catch (IllegalStateException e) {
            LOGGER.error("Altitudo is not applying anything: {}", e.getMessage());
            return;
        }

        PackLocationInfo location = new PackLocationInfo(
                ID,
                Component.literal("Altitudo"),
                PackSource.BUILT_IN,
                Optional.empty());

        // NeoForge's addPackFinders helper fixes fixedPosition to false,
        // which is why the Pack is built here instead.
        PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.TOP, true);

        Pack.Metadata metadata = new Pack.Metadata(
                Component.literal("World height and depth, and the generation that follows them."),
                PackCompatibility.COMPATIBLE,
                FeatureFlagSet.of(),
                List.of(),
                false);

        Pack pack = new Pack(
                location,
                new Pack.ResourcesSupplier() {
                    @Override
                    public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo info) {
                        return new GeneratedPack(info, target, nether);
                    }

                    @Override
                    public net.minecraft.server.packs.PackResources openFull(
                            PackLocationInfo info, Pack.Metadata meta) {
                        return new GeneratedPack(info, target, nether);
                    }
                },
                metadata,
                selection);

        event.addRepositorySource(consumer -> consumer.accept(pack));

        LOGGER.info("Altitudo: world {}..{} (height {}, sea level {}, {} sections).",
                target.minY(), target.topY(), target.height(), target.seaLevel(),
                target.height() / AltitudoConfig.SECTION);
    }
}
