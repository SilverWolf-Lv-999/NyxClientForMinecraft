package io.github.seraphina.nyxclient.module.movement;

import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.StrafeEvent;
import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;
import io.github.seraphina.nyxclient.utility.player.MovingUtility;

@ModuleInfo(name = "nyxclient.module.bhop.name", description = "nyxclient.module.bhop.description", category = Category.MOVEMENT)
public class BHop extends Module {
    public static final BHop INSTANCE = new BHop();

    public BHop() {

    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (MovingUtility.isMoving()) {
            if (mc.player.onGround()) mc.player.jumpFromGround();
            MovingUtility.strafe(2.0f);
        }
    }
}
