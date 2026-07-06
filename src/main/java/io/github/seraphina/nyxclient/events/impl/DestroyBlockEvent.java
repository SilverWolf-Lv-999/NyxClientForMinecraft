package io.github.seraphina.nyxclient.events.impl;

import io.github.seraphina.nyxclient.events.api.events.callables.EventCancellable;
import net.minecraft.core.BlockPos;

public class DestroyBlockEvent extends EventCancellable {

    private final BlockPos pos;

    public DestroyBlockEvent(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos getPos() {
        return this.pos;
    }

}
