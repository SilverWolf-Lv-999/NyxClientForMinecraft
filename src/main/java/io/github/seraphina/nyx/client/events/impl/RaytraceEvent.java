package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.Entity;

@Getter
public class RaytraceEvent implements Event {

    @Setter
    private float yaw;
    @Setter
    private float pitch;

    private final Entity entity;

    public RaytraceEvent(Entity entity, float yaw, float pitch) {
        this.entity = entity;
        this.yaw = yaw;
        this.pitch = pitch;
    }

}
