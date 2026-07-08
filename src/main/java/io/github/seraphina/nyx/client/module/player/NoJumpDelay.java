package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.nojumpdelay.name", description = "nyxclient.module.nojumpdelay.description", category = Category.PLAYER)
public class NoJumpDelay extends Module {
    public static final NoJumpDelay INSTANCE = new NoJumpDelay();
    @EventTarget
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        mc.player.noJumpDelay = 0;
    }
}
