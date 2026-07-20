package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.other.Target;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.utility.player.PlayerUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.spearthrust.name", description = "nyxclient.module.spearthrust.description", category = Category.COMBAT)
public class SpearThrust extends Module {
    public static final SpearThrust INSTANCE = new SpearThrust();

    private static final double VULCAN_HORIZONTAL_SPEED_LIMIT = 0.29D;

    public final EnumValue<ACType> type = ValueBuild.enumSetting("type", ACType.VANILLA, this);

    public final DoubleValue speed = ValueBuild.doubleValue("speed", 1.0, 0.1, 10.0, 0.1, this);
    public final IntValue range = ValueBuild.intSetting("range", 128, 3, 256, 1, this);

    private LivingEntity thrustTarget;

    @Override
    public void onDisable() {
        resetThrust();
    }

    @EventTarget
    public void onPreTick(TickEvent.Pre event) {
        if (!type.is(ACType.VULCAN)) {
            return;
        }

        updateThrustTarget();
        if (canUseVulcanMovement()) {
            clampVulcanHorizontalSpeed();
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!canUseVulcanMovement()) {
            return;
        }

        event.setForward(1.0F);
        event.setStrafe(0.0F);
        event.setSprint(true);
        event.setSneak(false);
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (!updateThrustTarget()) {
            return;
        }

        if (type.is(ACType.VULCAN)) {
            if (canUseVulcanMovement()) {
                applyVulcanPacketThrust();
            }
        } else {
            MovingUtility.setMomentumToward(thrustTarget, speed.getValue());
        }
    }

    private boolean updateThrustTarget() {
        if (mc.player == null || mc.level == null) {
            resetThrust();
            return false;
        }

        ItemStack useItem = mc.player.getUseItem();
        KineticWeapon kineticWeapon = getSpearKineticWeapon(useItem);
        if (!mc.player.isUsingItem() || kineticWeapon == null) {
            resetThrust();
            return false;
        }

        if (mc.player.getTicksUsingItem() < kineticWeapon.delayTicks()) {
            thrustTarget = null;
            return false;
        }

        thrustTarget = crosshairLivingTarget();
        return thrustTarget != null && thrustTarget.isAlive();
    }

    private boolean canUseVulcanMovement() {
        if (!type.is(ACType.VULCAN)
                || mc.player == null
                || mc.level == null
                || thrustTarget == null
                || !isValidThrustTarget(thrustTarget)
                || !mc.player.isUsingItem()
                || mc.player.isPassenger()
                || mc.player.isFallFlying()
                || mc.player.getAbilities().flying
                || mc.player.isInWater()
                || mc.player.isInLava()) {
            return false;
        }

        KineticWeapon kineticWeapon = getSpearKineticWeapon(mc.player.getUseItem());
        return kineticWeapon != null && mc.player.getTicksUsingItem() >= kineticWeapon.delayTicks();
    }

    private void clampVulcanHorizontalSpeed() {
        Vec3 velocity = mc.player.getDeltaMovement();
        double horizontalSpeed = velocity.horizontalDistance();
        double limit = Math.min(speed.getValue(), VULCAN_HORIZONTAL_SPEED_LIMIT);
        if (horizontalSpeed <= limit || horizontalSpeed < 1.0E-6D) {
            return;
        }

        double scale = limit / horizontalSpeed;
        mc.player.setDeltaMovement(velocity.x * scale, velocity.y, velocity.z * scale);
    }

    private void applyVulcanPacketThrust() {
        Vec3 start = mc.player.position();
        Vec3 targetCenter = thrustTarget.getBoundingBox().getCenter();
        double dx = targetCenter.x - start.x;
        double dz = targetCenter.z - start.z;
        double horizontalDistance = Math.hypot(dx, dz);
        if (horizontalDistance < 1.0E-6D) {
            return;
        }

        double stopDistance = (mc.player.getBbWidth() + thrustTarget.getBbWidth()) * 0.5D + 0.1D;
        double moveDistance = Math.min(speed.getValue(), Math.max(0.0D, horizontalDistance - stopDistance));
        if (moveDistance < 1.0E-6D) {
            return;
        }

        double directionX = dx / horizontalDistance;
        double directionZ = dz / horizontalDistance;
        int steps = Math.max(1, (int)Math.ceil(moveDistance / VULCAN_HORIZONTAL_SPEED_LIMIT));
        double stepDistance = moveDistance / steps;
        Vec3 current = start;

        for (int i = 0; i < steps; i++) {
            Vec3 next = current.add(directionX * stepDistance, 0.0D, directionZ * stepDistance);
            Vec3 totalOffset = next.subtract(start);
            if (!mc.level.noBlockCollision(mc.player, mc.player.getBoundingBox().move(totalOffset))) {
                break;
            }

            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                    next.x,
                    next.y,
                    next.z,
                    mc.player.onGround(),
                    mc.player.horizontalCollision
            ));
            current = next;
        }

        if (!current.equals(start)) {
            mc.player.setPos(current);
        }
    }

    private LivingEntity crosshairLivingTarget() {
        if (mc.player == null) return null;
        HitResult hitResult = PlayerUtility.raycastForEntity(
                mc.level,
                mc.player,
                range.getValue(),
                true,
                entity -> entity instanceof LivingEntity livingEntity && isValidThrustTarget(livingEntity)
        );
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        Entity entity = entityHitResult.getEntity();
        if (entity instanceof LivingEntity livingEntity && isValidThrustTarget(livingEntity)) {
            return livingEntity;
        }

        return null;
    }

    private boolean isValidThrustTarget(LivingEntity entity) {
        return entity != mc.player
                && entity.isAlive()
                && Target.isTarget(entity);
    }

    private KineticWeapon getSpearKineticWeapon(ItemStack stack) {
        KineticWeapon kineticWeapon = stack.get(DataComponents.KINETIC_WEAPON);
        if (kineticWeapon == null || !stack.has(DataComponents.PIERCING_WEAPON)) {
            return null;
        }

        return kineticWeapon;
    }

    private void resetThrust() {
        thrustTarget = null;
    }

    public enum ACType {
        VANILLA,
        VULCAN
    }
}
