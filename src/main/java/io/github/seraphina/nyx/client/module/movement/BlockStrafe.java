package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.StrafeEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.utility.player.PlayerUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.blockstrafe.name", description = "nyxclient.module.blockstrafe.description", category = Category.MOVEMENT)
public final class BlockStrafe extends Module {
    public static final BlockStrafe INSTANCE = new BlockStrafe();

    public final IntValue speed = ValueBuild.intSetting("speed", 10, 0, 20, 1, () -> true, true, this);

    private BlockStrafe() {
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (mc.player == null || mc.level == null || !PlayerUtility.isInsideBlock()) {
            return;
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        if (!MovingUtility.isMoving()) {
            mc.player.setDeltaMovement(0.0D, velocity.y, 0.0D);
            return;
        }

        double moveSpeed = 0.002873D * speed.getValue();
        Vec3 horizontalVelocity = MovingUtility.horizontalVelocity(moveSpeed, event.getYaw());
        mc.player.setDeltaMovement(horizontalVelocity.x, velocity.y, horizontalVelocity.z);
    }
}
