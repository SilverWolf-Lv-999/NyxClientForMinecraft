package io.github.seraphina.nyxclient;

import io.github.seraphina.nyxclient.events.api.EventManager;
import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.SetScreenEvent;
import io.github.seraphina.nyxclient.manager.*;
import io.github.seraphina.nyxclient.utility.render.Shaders;

public class Client {
    public static final Client INSTANCE = new Client();
    public static final String CLIENT_NAME = "Nyx";
    public void init() {
        ModuleManager.init();
        ConfigManager.init();
        EventManager.register(this);
        EventManager.register(KeyManager.INSTANCE);
        RotationManager.INSTANCE.getClass();
        Shaders.init();
        FontManager.init();
    }

    @EventTarget
    public static void setScreen(SetScreenEvent event) {
    }
}
