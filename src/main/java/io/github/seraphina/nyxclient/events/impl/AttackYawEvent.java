package io.github.seraphina.nyxclient.events.impl;

import io.github.seraphina.nyxclient.events.api.events.Event;

public class AttackYawEvent implements Event {

    private float yaw;

    public AttackYawEvent(float yaw) {
        this.yaw = yaw;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

}
