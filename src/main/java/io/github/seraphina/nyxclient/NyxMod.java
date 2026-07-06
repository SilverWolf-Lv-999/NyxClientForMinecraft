package io.github.seraphina.nyxclient;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NyxMod.MODID)
public class NyxMod {
    public static final String MODID = "nyxclient";

    private static final Logger LOGGER = LogUtils.getLogger();

    public NyxMod(IEventBus modEventBus, ModContainer modContainer) {

    }
}
