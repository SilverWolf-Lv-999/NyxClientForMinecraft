package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;

public class JumpEvent implements Event {

    private float yaw;

    public JumpEvent(float yaw) {
        this.yaw = yaw;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

}
