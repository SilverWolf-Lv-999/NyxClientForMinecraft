package io.github.seraphina.nyx.client.manager;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.KeyPressEvent;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.utility.IMinecraft;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

public class KeyManager implements IMinecraft {
    public static final KeyManager INSTANCE = new KeyManager();

    @EventTarget
    public void onKeyPress(KeyPressEvent event) {
        if (event.getAction() != GLFW_PRESS || mc.screen != null) {
            return;
        }

        boolean handled = false;
        for (Module module : ModuleManager.MODULES) {
            if (module.hasKey() && module.getKey() == event.getKey()) {
                module.toggle();
                handled = true;
            }
        }

        if (handled) {
            event.setCancelled(true);
        }
    }
}
