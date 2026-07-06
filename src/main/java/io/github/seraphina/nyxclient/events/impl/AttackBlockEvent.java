package io.github.seraphina.nyxclient.events.impl;

import io.github.seraphina.nyxclient.events.api.events.callables.EventCancellable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class AttackBlockEvent extends EventCancellable {

    private BlockPos blockPos;
    private Direction direction;

    public AttackBlockEvent(BlockPos blockPos, Direction direction) {
        this.blockPos = blockPos;
        this.direction = direction;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public void setBlockPos(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

}
