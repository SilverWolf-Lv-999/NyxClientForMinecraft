package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SlowdownEvent implements Event {

    private boolean slowdown;

    public SlowdownEvent(boolean slowdown) {
        this.slowdown = slowdown;
    }

}
