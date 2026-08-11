package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.callables.EventCancellable;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.phys.HitResult;

@Setter
@Getter
public class StartUseItemEvent extends EventCancellable {
    private HitResult hitResult;

    public StartUseItemEvent(HitResult hitResult) {
        this.hitResult = hitResult;
    }

}
