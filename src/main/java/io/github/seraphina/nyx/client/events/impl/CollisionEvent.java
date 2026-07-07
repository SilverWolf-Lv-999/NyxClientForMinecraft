package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class CollisionEvent implements Event {

    private BlockState blockState;
    private final BlockPos blockPos;

    public CollisionEvent(BlockState blockState, BlockPos blockPos) {
        this.blockState = blockState;
        this.blockPos = blockPos;
    }

    public BlockState getState() {
        return blockState;
    }

    public BlockPos getPos() {
        return blockPos;
    }

    public void setState(BlockState blockState) {
        this.blockState = blockState;
    }

}
