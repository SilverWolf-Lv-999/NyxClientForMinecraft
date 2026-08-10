package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;
import net.minecraft.core.BlockPos;

public class BlockBreakingProgressEvent implements Event {
    private final int breakerId;
    private final BlockPos pos;
    private final int progress;

    public BlockBreakingProgressEvent(int breakerId, BlockPos pos, int progress) {
        this.breakerId = breakerId;
        this.pos = pos.immutable();
        this.progress = progress;
    }

    public int getBreakerId() {
        return breakerId;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getProgress() {
        return progress;
    }
}
