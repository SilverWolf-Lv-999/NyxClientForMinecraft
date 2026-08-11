package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.callables.EventCancellable;
import lombok.Getter;
import net.minecraft.client.input.KeyEvent;

@Getter
public class KeyPressEvent extends EventCancellable {

    private final KeyEvent keyEvent;
    private final int action;

    public KeyPressEvent(KeyEvent keyEvent, int action) {
        this.keyEvent = keyEvent;
        this.action = action;
    }

    public int getKey() {
        return this.keyEvent.key();
    }

    public int getModifiers() {
        return this.keyEvent.modifiers();
    }

}
