package io.github.seraphina.nyx.client.events.api.events.callables;

import io.github.seraphina.nyx.client.events.api.events.Event;
import io.github.seraphina.nyx.client.events.api.events.Typed;

public abstract class EventTyped implements Event, Typed {
    private final byte type;

    protected EventTyped(byte eventType) {
        this.type = eventType;
    }

    @Override
    public byte getType() {
        return this.type;
    }
}
