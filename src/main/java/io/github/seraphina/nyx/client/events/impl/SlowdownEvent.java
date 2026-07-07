package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;

public class SlowdownEvent implements Event {

    private boolean slowdown;

    public SlowdownEvent(boolean slowdown) {
        this.slowdown = slowdown;
    }

    public boolean isSlowdown() {
        return this.slowdown;
    }

    public void setSlowdown(boolean slowdown) {
        this.slowdown = slowdown;
    }

}
