package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;

public class FallFlyingEvent implements Event {

    private float pitch;

    public FallFlyingEvent(float pitch) {
        this.pitch = pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getPitch() {
        return this.pitch;
    }

}
