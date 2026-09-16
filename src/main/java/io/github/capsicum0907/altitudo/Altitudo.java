package io.github.capsicum0907.altitudo;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import org.slf4j.Logger;

@Mod(Altitudo.MODID)
public class Altitudo {
    public static final String MODID = "altitudo";

    private static final Logger LOGGER = LogUtils.getLogger();

    public Altitudo(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.STARTUP, AltitudoConfig.SPEC);

        modEventBus.addListener(PackRegistration::onAddPackFinders);
        // On save, not on start: the chunk generators are built while a level loads,
        // so anything counted here reads zero before that.
        NeoForge.EVENT_BUS.addListener((LevelEvent.Save e) -> {
            Anchors.report();
            Bands.report();
        });

        LOGGER.info("Altitudo {} loaded.", modContainer.getModInfo().getVersion());
    }
}
