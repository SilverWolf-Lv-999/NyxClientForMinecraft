package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.StrafeEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.strafe.name", description = "nyxclient.module.strafe.description", category = Category.MOVEMENT)
public final class Strafe extends Module {
    public static final Strafe INSTANCE = new Strafe();

    public final BoolValue airStop = ValueBuild.boolSetting("air stop", true, this);
    public final BoolValue slowCheck = ValueBuild.boolSetting("slow check", true, this);

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!canStrafe()) {
            return;
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        if (!MovingUtility.isMoving()) {
            if (airStop.getValue()) {
                mc.player.setDeltaMovement(0.0D, velocity.y, 0.0D);
            }
            return;
        }

        Vec3 horizontalVelocity = MovingUtility.horizontalVelocity(getBaseMoveSpeed(), event.getYaw());
        mc.player.setDeltaMovement(horizontalVelocity.x, velocity.y, horizontalVelocity.z);
    }

    public double getBaseMoveSpeed() {
        double baseSpeed = 0.2873D;
        if (mc.player == null
                || !mc.player.hasEffect(MobEffects.SPEED)
                || (slowCheck.getValue() && mc.player.hasEffect(MobEffects.SLOWNESS))) {
            return baseSpeed;
        }

        MobEffectInstance speedEffect = mc.player.getEffect(MobEffects.SPEED);
        if (speedEffect != null) {
            baseSpeed *= 1.0D + 0.2D * (speedEffect.getAmplifier() + 1);
        }
        return baseSpeed;
    }

    private boolean canStrafe() {
        return mc.player != null
                && mc.level != null
                && !mc.player.isCrouching()
                && !mc.player.getAbilities().flying
                && !mc.player.isPassenger()
                && !mc.player.isFallFlying()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !BHop.INSTANCE.isEnabled();
    }
}
