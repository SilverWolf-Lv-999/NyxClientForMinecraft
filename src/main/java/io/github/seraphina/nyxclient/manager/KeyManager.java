package io.github.seraphina.nyxclient.manager;

import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.KeyPressEvent;

import java.awt.event.KeyEvent;

public class KeyManager {
    @EventTarget
    public static void onKeyPress(KeyPressEvent event) {
        if (event.getAction() == KeyEvent.KEY_PRESSED) {
            ModuleManager.MODULES.forEach(module -> {
                if (module.getKey() == event.getKey()) {
                    module.toggle();
                }
            });
        }
    }
}
