package io.github.seraphina.nyxclient.module.movement;

import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.StrafeEvent;
import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;
import io.github.seraphina.nyxclient.utility.player.MovingUtility;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.lowhop.name", description = "nyxclient.module.lowhop.description", category = Category.MOVEMENT)
public class LowHop extends Module {
    public static final LowHop INSTANCE = new LowHop();

    private int airTime;

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (mc.player.onGround()) airTime = 0;
        else airTime++;
        if (MovingUtility.isMoving()) {
            if (mc.player.onGround()) mc.player.jumpFromGround();
            var dist = mc.player.getDeltaMovement().x * mc.player.getDeltaMovement().x + mc.player.getDeltaMovement().z * mc.player.getDeltaMovement().z;
            if (dist < 0.04f) MovingUtility.strafe(2.6f);
        }
        if (airTime % 2 == 0) mc.player.addDeltaMovement(new Vec3(0.0, -0.1, 0.0));
    }
}
