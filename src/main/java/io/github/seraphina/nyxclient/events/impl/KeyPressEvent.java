package io.github.seraphina.nyxclient.events.impl;

import io.github.seraphina.nyxclient.events.api.events.callables.EventCancellable;
import net.minecraft.client.input.KeyEvent;

public class KeyPressEvent extends EventCancellable {

    private final KeyEvent keyEvent;
    private final int action;

    public KeyPressEvent(KeyEvent keyEvent, int action) {
        this.keyEvent = keyEvent;
        this.action = action;
    }

    public KeyEvent getKeyEvent() {
        return this.keyEvent;
    }

    public int getAction() {
        return this.action;
    }

    public int getKey() {
        return this.keyEvent.key();
    }

    public int getModifiers() {
        return this.keyEvent.modifiers();
    }

}
