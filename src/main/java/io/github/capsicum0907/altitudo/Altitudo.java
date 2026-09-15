package io.github.capsicum0907.altitudo;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

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
        LOGGER.info("Altitudo {} loaded.", modContainer.getModInfo().getVersion());
    }
}
