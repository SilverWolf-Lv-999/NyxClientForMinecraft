package io.github.seraphina.nyx.mod;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NyxMod.MODID)
@OnlyIn(Dist.CLIENT)
public class NyxMod {
    public static final String MODID = "nyxclient";

    private static final Logger LOGGER = LogUtils.getLogger();

    public NyxMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info(MODID + "  initialized");
        modContainer.getModInfo().getModId();
        modEventBus.unregister(this);
    }
}
