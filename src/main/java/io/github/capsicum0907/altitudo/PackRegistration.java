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

/**
 * Puts the generated pack in the list, on, and at the top - without anyone having
 * to do it by hand.
 * <p>
 * The mod this replaces left all three to the player, so every new world meant
 * finding the pack, enabling it, and walking it up past everything else with an
 * arrow button. With two hundred mods installed that is minutes of clicking per
 * world, repeated, with a silently wrong world as the penalty for getting it
 * wrong once.
 */
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
        java.util.Optional<Dimensions> nether;
        try {
            target = Dimensions.fromConfig();
            nether = AltitudoConfig.EXTEND_NETHER.get()
                    ? Optional.of(Dimensions.netherFromConfig())
                    : Optional.empty();
        } catch (IllegalStateException e) {
            // Refusing to register is the loud failure: worlds generate at vanilla
            // height and the log says why. Registering a pack built from values the
            // game will reject would instead crash inside world creation.
            LOGGER.error("Altitudo is not applying anything: {}", e.getMessage());
            return;
        }

        PackLocationInfo location = new PackLocationInfo(
                ID,
                Component.literal("Altitudo"),
                PackSource.BUILT_IN,
                Optional.empty());

        // required: on without being switched on. defaultPosition TOP: above the
        // packs it has to override. fixedPosition: and it cannot be dragged out of
        // that place. NeoForge's addPackFinders helper hardcodes the last one to
        // false, which is why this builds the Pack itself rather than calling it.
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
