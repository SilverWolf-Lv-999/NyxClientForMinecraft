package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;

@ModuleInfo(name = "nyxclient.module.safewalk.name", description = "nyxclient.module.safewalk.description", category = Category.MOVEMENT)
public class SafeWalk extends Module {
    public static final SafeWalk INSTANCE = new SafeWalk();

    private static final double INPUT_EPSILON = 1.0E-4D;
    private static final double LEDGE_PROBE_DISTANCE = 0.25D;

    private boolean sneakingForSafeWalk;

    @Override
    public void onDisable() {
        sneakingForSafeWalk = false;
    }

    @EventTarget(4)
    public void onMoveInput(MoveInputEvent event) {
        sneakingForSafeWalk = false;

        LocalPlayer player = mc.player;
        if (!canUseSafeWalk(player) || event.isSneak() || event.isJump() || !player.onGround() || !hasHorizontalInput(event)) {
            return;
        }

        if (isMovingTowardLedge(player, event)) {
            event.setSneak(true);
            event.setSprint(false);
            sneakingForSafeWalk = true;
        }
    }

    public boolean shouldStayOnGroundSurface(LocalPlayer player) {
        return sneakingForSafeWalk && canUseSafeWalk(player);
    }

    private boolean canUseSafeWalk(LocalPlayer player) {
        return isEnabled()
                && player != null
                && player == mc.player
                && !player.getAbilities().flying
                && !player.isSpectator()
                && !player.isFallFlying()
                && !player.isPassenger()
                && !player.isInWater()
                && !player.isInLava();
    }

    private boolean hasHorizontalInput(MoveInputEvent event) {
        return Math.abs(event.getForward()) > INPUT_EPSILON || Math.abs(event.getStrafe()) > INPUT_EPSILON;
    }

    private boolean isMovingTowardLedge(LocalPlayer player, MoveInputEvent event) {
        double forward = event.getForward();
        double strafe = event.getStrafe();
        double inputLength = Math.sqrt(forward * forward + strafe * strafe);
        if (inputLength <= INPUT_EPSILON) {
            return false;
        }

        forward /= inputLength;
        strafe /= inputLength;

        double yaw = Math.toRadians(player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double offsetX = (strafe * cos - forward * sin) * LEDGE_PROBE_DISTANCE;
        double offsetZ = (forward * cos + strafe * sin) * LEDGE_PROBE_DISTANCE;
        return canFallAtLeast(player, offsetX, offsetZ, player.maxUpStep());
    }

    private boolean canFallAtLeast(LocalPlayer player, double offsetX, double offsetZ, double height) {
        AABB box = player.getBoundingBox();
        return player.level().noCollision(
                player,
                new AABB(
                        box.minX + 1.0E-7D + offsetX,
                        box.minY - height - 1.0E-7D,
                        box.minZ + 1.0E-7D + offsetZ,
                        box.maxX - 1.0E-7D + offsetX,
                        box.minY,
                        box.maxZ - 1.0E-7D + offsetZ
                )
        );
    }
}
