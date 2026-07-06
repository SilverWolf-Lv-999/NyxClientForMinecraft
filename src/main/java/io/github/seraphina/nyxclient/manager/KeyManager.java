package io.github.seraphina.nyxclient.manager;

import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.KeyPressEvent;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

public class KeyManager {
    public static final KeyManager INSTANCE = new KeyManager();

    @EventTarget
    public void onKeyPress(KeyPressEvent event) {
        if (event.getAction() != GLFW_PRESS) {
            return;
        }

        ModuleManager.MODULES.forEach(module -> {
            if (module.getKey() == event.getKey()) {
                module.toggle();
            }
        });
    }
}
