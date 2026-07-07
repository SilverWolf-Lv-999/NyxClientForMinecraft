package io.github.seraphina.nyx.client;

import io.github.seraphina.nyx.client.events.api.EventManager;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.SetScreenEvent;
import io.github.seraphina.nyx.client.manager.*;
import io.github.seraphina.nyx.client.ui.mainui.MainUI;
import io.github.seraphina.nyx.client.utility.render.Shaders;
import net.minecraft.client.gui.screens.TitleScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NyxClient {
    public static final NyxClient INSTANCE = new NyxClient();
    public static final String CLIENT_NAME = "Nyx";
    public static final Logger LOGGER = LogManager.getLogger(CLIENT_NAME);

    public void init() {
        LOGGER.info("Initializing NyxClient");
        Shaders.init();
        ModuleManager.init();
        ConfigManager.init();
        EventManager.register(this);
        EventManager.register(KeyManager.INSTANCE);
        RotationManager.INSTANCE.getClass();
        FontManager.init();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Shaders.close();
            LOGGER.info("Shutting down NyxClient");
        }));
    }

    @EventTarget
    public static void setScreen(SetScreenEvent event) {
        if (event.getScreen() instanceof TitleScreen) {
            event.setScreen(new MainUI());
        }
    }
}
