package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FallFlyingEvent implements Event {

    private float pitch;

    public FallFlyingEvent(float pitch) {
        this.pitch = pitch;
    }

}
