package io.github.seraphina.nyx.client;

import io.github.seraphina.nyx.client.events.api.EventManager;
import io.github.seraphina.nyx.client.manager.*;

public class NyxClient {
    public static final NyxClient INSTANCE = new NyxClient();
    public static final String CLIENT_NAME = "Nyx";
    public void init() {
        ModuleManager.init();
        ConfigManager.init();
        EventManager.register(this);
        EventManager.register(KeyManager.INSTANCE);
        RotationManager.INSTANCE.getClass();
        FontManager.init();
    }

//    @EventTarget
//    public static void setScreen(SetScreenEvent event) {
//        if (event.getScreen() instanceof TitleScreen) {
//            event.setScreen(new MainUI());
//        }
//    }
}
