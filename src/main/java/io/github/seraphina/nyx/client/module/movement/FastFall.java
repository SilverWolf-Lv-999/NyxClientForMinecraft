package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.fastfall.name", description = "nyxclient.module.fastfall.description", category = Category.MOVEMENT)
public class FastFall extends Module {
    public static final FastFall INSTANCE = new FastFall();

    private static final double GRAVITY = 0.08D;
    private static final double AIR_DRAG = 0.98D;
    private static final double GROUND_MARGIN = 0.03D;
    private static final double COLLISION_PROBE_STEP = 0.05D;

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.GRIM, this);
    public final DoubleValue speed = ValueBuild.doubleSetting("speed", 1.0D, 0.1D, 4.0D, 0.1D, this);
    public final IntValue packets = ValueBuild.intSetting("packets", 4, 1, 10, 1, () -> mode.is(Mode.GRIM), this);
    public final DoubleValue range = ValueBuild.doubleSetting("range", 5.0D, 1.0D, 10.0D, 0.5D, this);

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canFastFall()) {
            return;
        }

        if (mode.is(Mode.GRIM)) {
            grimFastFall();
        } else {
            vanillaFastFall();
        }
    }

    private boolean canFastFall() {
        return mc.player != null
                && mc.level != null
                && !mc.player.onGround()
                && !mc.options.keyJump.isDown()
                && !mc.player.getAbilities().flying
                && !mc.player.isSpectator()
                && !mc.player.isFallFlying()
                && !mc.player.isPassenger()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && mc.player.getDeltaMovement().y <= 0.0D
                && distanceToGround() > GROUND_MARGIN;
    }

    private void grimFastFall() {
        if (mc.player.connection == null) {
            return;
        }

        double remainingDistance = Math.min(speed.getValue(), distanceToGround() - GROUND_MARGIN);
        if (remainingDistance <= 0.0D) {
            return;
        }

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        double simulatedVelocity = mc.player.getDeltaMovement().y;
        double movedDistance = 0.0D;

        for (int i = 0; i < packets.getValue() && remainingDistance > 0.0D; i++) {
            double moveVelocity = simulatedVelocity - GRAVITY;
            double fullStepDistance = -moveVelocity;
            if (fullStepDistance <= 0.0D) {
                break;
            }

            double stepDistance = Math.min(fullStepDistance, remainingDistance);
            movedDistance += stepDistance;
            remainingDistance -= stepDistance;
            double packetY = y - movedDistance;
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                    x,
                    packetY,
                    z,
                    false,
                    mc.player.horizontalCollision
            ));

            simulatedVelocity = -stepDistance * AIR_DRAG;
        }

        if (movedDistance <= 0.0D) {
            return;
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(velocity.x, Math.min(velocity.y, simulatedVelocity), velocity.z);
        mc.player.setPos(x, y - movedDistance, z);
    }

    private void vanillaFastFall() {
        double targetVelocity = -Math.min(speed.getValue(), distanceToGround() - GROUND_MARGIN);
        Vec3 velocity = mc.player.getDeltaMovement();
        if (velocity.y > targetVelocity) {
            mc.player.setDeltaMovement(velocity.x, targetVelocity, velocity.z);
        }
    }

    private double distanceToGround() {
        if (mc.player == null || mc.level == null) {
            return 0.0D;
        }

        AABB box = mc.player.getBoundingBox();
        double maxDistance = range.getValue();
        for (double distance = COLLISION_PROBE_STEP; distance <= maxDistance; distance += COLLISION_PROBE_STEP) {
            if (!mc.level.noBlockCollision(mc.player, box.move(0.0D, -distance, 0.0D))) {
                return distance;
            }
        }

        return 0.0D;
    }

    public enum Mode {
        GRIM, VANILLA
    }
}
