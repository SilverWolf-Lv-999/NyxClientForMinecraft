package io.github.seraphina.nyxclient.module.player;

import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.StrafeEvent;
import io.github.seraphina.nyxclient.events.impl.TickEvent;
import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;
import io.github.seraphina.nyxclient.module.movement.Scaffold;

@ModuleInfo(name = "nyxclient.module.nodelay.name", description = "nyxclient.module.nodelay.description", category = Category.PLAYER)
public class DelayRemover extends Module {
    public static final DelayRemover INSTANCE = new DelayRemover();
    @EventTarget
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        mc.player.noJumpDelay = 0;
    }
}
