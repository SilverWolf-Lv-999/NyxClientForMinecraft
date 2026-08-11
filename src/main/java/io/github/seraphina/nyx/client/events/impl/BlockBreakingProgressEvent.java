package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;
import lombok.Getter;
import net.minecraft.core.BlockPos;

@Getter
public class BlockBreakingProgressEvent implements Event {
    private final int breakerId;
    private final BlockPos pos;
    private final int progress;

    public BlockBreakingProgressEvent(int breakerId, BlockPos pos, int progress) {
        this.breakerId = breakerId;
        this.pos = pos.immutable();
        this.progress = progress;
    }

}
