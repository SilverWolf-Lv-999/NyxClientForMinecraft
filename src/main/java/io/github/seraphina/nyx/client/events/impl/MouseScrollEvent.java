package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.callables.EventCancellable;

public class MouseScrollEvent extends EventCancellable {
    private final double scrollX;
    private final double scrollY;

    public MouseScrollEvent(double scrollX, double scrollY) {
        this.scrollX = scrollX;
        this.scrollY = scrollY;
    }

    public double getScrollX() {
        return scrollX;
    }

    public double getScrollY() {
        return scrollY;
    }
}
