package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;
import lombok.Getter;

@Getter
public class AttackYawEvent implements Event {

    private float yaw;

    public AttackYawEvent(float yaw) {
        this.yaw = yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

}
