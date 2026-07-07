package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.StrafeEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.bhop.name", description = "nyxclient.module.bhop.description", category = Category.MOVEMENT)
public class BHop extends Module {
    public static final BHop INSTANCE = new BHop();
    public final BoolValue lowhop = ValueBuild.boolSetting("nyxclient.setting.bhop.lowhop.name", false, this);
    public final BoolValue yport = ValueBuild.boolSetting("nyxclient.setting.bhop.yport.name", false, this);

    private int airTime;

    public BHop() {

    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (mc.player.onGround()) airTime = 0;
        else airTime++;
        var dist = Math.sqrt(mc.player.getDeltaMovement().x * mc.player.getDeltaMovement().x + mc.player.getDeltaMovement().z * mc.player.getDeltaMovement().z);
        if (MovingUtility.isMoving()) {
            if (mc.player.onGround()) {
                mc.player.jumpFromGround();
            }
            if (dist < 0.212412) MovingUtility.strafe(2.563452314f);
            if (airTime == 1) MovingUtility.strafe(2.563452314f);
        }
        if (lowhop.getValue()) {
            if (dist > 0.265) MovingUtility.strafe(1.950000012412532f);
            if (airTime < 8) {
                if (airTime % 2 == 0) mc.player.addDeltaMovement(new Vec3(0.0, -0.1, 0.0));
                boolean b = MovingUtility.canMove(mc.player.getDeltaMovement().x * 2, mc.player.getDeltaMovement().z * 2, -0.2);
                if (airTime>6 && !b) mc.player.addDeltaMovement(new Vec3(0.0, 0.42, 0.0));
                if (airTime > 6 && b)mc.player.addDeltaMovement(new Vec3(0.0, -0.1, 0.0));
            }
        }
        if (yport.getValue() && airTime % 3 > 0 && Math.abs(mc.player.getDeltaMovement().y - 0.09800000190734864) < 0.12){
            mc.player.addDeltaMovement(new Vec3(0.0, -0.18000032145361532, 0.0));
        }
    }
}
