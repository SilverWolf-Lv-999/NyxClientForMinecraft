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
            MovingUtility.strafe(2.1f);
        }
        var dist = Math.sqrt(mc.player.getDeltaMovement().x * mc.player.getDeltaMovement().x + mc.player.getDeltaMovement().z * mc.player.getDeltaMovement().z);
        if (dist > 0.265) MovingUtility.strafe(1.9f);
        if (airTime < 8) {
            if (airTime % 2 == 0) mc.player.addDeltaMovement(new Vec3(0.0, -0.1, 0.0));
            boolean b = MovingUtility.canMove(mc.player.getDeltaMovement().x * 2, mc.player.getDeltaMovement().z * 2, -0.2);
            if (airTime>6 && !b) mc.player.addDeltaMovement(new Vec3(0.0, 0.42, 0.0));
            if (airTime > 6 && b)mc.player.addDeltaMovement(new Vec3(0.0, -0.1, 0.0));
        }
    }
}
