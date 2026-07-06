package io.github.seraphina.nyxclient;

import io.github.seraphina.nyxclient.events.api.EventManager;
import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.SetScreenEvent;
import io.github.seraphina.nyxclient.manager.*;

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

    @EventTarget
    public static void setScreen(SetScreenEvent event) {
    }
}
