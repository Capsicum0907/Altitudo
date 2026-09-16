package io.github.capsicum0907.altitudo;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import org.slf4j.Logger;

/**
 * Entry point. {@link #MODID} must match {@code mod_id} in gradle.properties,
 * which is what the generated neoforge.mods.toml is filled from.
 */
@Mod(Altitudo.MODID)
public class Altitudo {
    public static final String MODID = "altitudo";

    private static final Logger LOGGER = LogUtils.getLogger();

    public Altitudo(IEventBus modEventBus, ModContainer modContainer) {
        // STARTUP, because the pack is registered before any world exists and has to
        // read these by then. A world's height is decided when it is created and
        // never changes, so needing a restart costs nothing.
        modContainer.registerConfig(ModConfig.Type.STARTUP, AltitudoConfig.SPEC);

        modEventBus.addListener(PackRegistration::onAddPackFinders);
        // On the game bus and this late on purpose: the anchors are moved while the
        // data pack registries load, which is after every mod-bus phase. Reporting
        // earlier counts zero and warns about nothing.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerAboutToStartEvent e) -> Anchors.report());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.LevelEvent.Save e) -> OreBands.report());

        LOGGER.info("Altitudo {} loaded.", modContainer.getModInfo().getVersion());
    }
}
