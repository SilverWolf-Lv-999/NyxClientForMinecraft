package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UseItemRaytraceEvent implements Event {

    private float yaw;
    private float pitch;

    public UseItemRaytraceEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

}
