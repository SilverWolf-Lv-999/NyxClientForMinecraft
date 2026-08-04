package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.callables.EventCancellable;
import net.minecraft.world.phys.HitResult;

public class StartUseItemEvent extends EventCancellable {
    private HitResult hitResult;

    public StartUseItemEvent(HitResult hitResult) {
        this.hitResult = hitResult;
    }

    public HitResult getHitResult() {
        return this.hitResult;
    }

    public void setHitResult(HitResult hitResult) {
        this.hitResult = hitResult;
    }
}
