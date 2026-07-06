package io.github.seraphina.nyxclient.events.impl;

import io.github.seraphina.nyxclient.events.api.events.callables.EventCancellable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class AttackEntityEvent extends EventCancellable {

    private final Player player;
    private final Entity entity;

    public AttackEntityEvent(Player player, Entity entity) {
        this.player = player;
        this.entity = entity;
    }

    public Player getPlayer() {
        return player;
    }

    public Entity getEntity() {
        return entity;
    }

}
