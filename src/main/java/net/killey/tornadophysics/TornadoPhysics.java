package net.killey.tornadophysics;

import net.killey.tornadophysics.event.TornadoEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;


@Mod(TornadoPhysics.MODID)
public class TornadoPhysics {
    public static final String MODID = "tornadophysics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TornadoPhysics(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NeoForge.EVENT_BUS.register(new TornadoEvent());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Tornado Physics Initialized");
    }
}
