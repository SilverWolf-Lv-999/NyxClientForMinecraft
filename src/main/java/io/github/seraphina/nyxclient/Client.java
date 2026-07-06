package io.github.seraphina.nyxclient;

import io.github.seraphina.nyxclient.events.api.EventManager;
import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.SetScreenEvent;
import io.github.seraphina.nyxclient.manager.ConfigManager;
import io.github.seraphina.nyxclient.manager.KeyManager;
import io.github.seraphina.nyxclient.manager.ModuleManager;
import io.github.seraphina.nyxclient.ui.mainui.MainUI;
import net.minecraft.client.gui.screens.TitleScreen;

public class Client {
    public static final Client INSTANCE = new Client();
    public static final String CLIENT_NAME = "Nyx";
    public void init() {
        ModuleManager.init();
        ConfigManager.init();
        EventManager.register(this);
        EventManager.register(KeyManager.class);
    }

    @EventTarget
    public static void setScreen(SetScreenEvent event) {
        if (event.getScreen() instanceof TitleScreen) {
            event.setScreen(new MainUI());
        }
    }
}
