package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class StrafeEvent implements Event {

    private float yaw;

    public StrafeEvent(float yaw) {
        this.yaw = yaw;
    }

}
