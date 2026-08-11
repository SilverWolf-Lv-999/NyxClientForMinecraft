package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.utility.world.HoleUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

@ModuleInfo(name = "nyxclient.module.holesnap.name", description = "nyxclient.module.holesnap.description", category = Category.MOVEMENT)
public final class HoleSnap extends Module {
    public static final HoleSnap INSTANCE = new HoleSnap();

    private static final double MAX_SPEED = 0.2873D;
    private static final int MAX_STUCK_TICKS = 8;

    public final BoolValue anyHole = ValueBuild.boolSetting("any hole", true, this);
    public final BoolValue up = ValueBuild.boolSetting("up", true, this);
    public final BoolValue grim = ValueBuild.boolSetting("grim", false, this);
    public final IntValue range = ValueBuild.intSetting("range", 5, 1, 50, 1, this);
    public final IntValue timeoutTicks = ValueBuild.intSetting("timeout", 40, 0, 100, 1, this);
    public final DoubleValue rotationSpeed = ValueBuild.doubleSetting(
            "rotation speed",
            0.8D,
            0.0D,
            1.0D,
            0.01D,
            () -> grim.getValue(),
            this
    );
    public final EnumValue<Priority> priority = ValueBuild.enumSetting(
            "priority",
            Priority.Low,
            () -> grim.getValue(),
            this
    );

    private HoleUtility.Hole targetHole;
    private Vec3 targetPosition;
    private boolean resetMove;
    private int stuckTicks;
    private int enabledTicks;

    private HoleSnap() {
    }

    @Override
    public void onEnable() {
        clearState();
        if (isNull() || grim.getValue() && !MovementSync.INSTANCE.isEnabled()) {
            setEnabled(false);
            return;
        }

        targetHole = findTargetHole();
        if (targetHole == null) {
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        if (resetMove && !grim.getValue() && mc.player != null) {
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(0.0D, velocity.y, 0.0D);
        }
        clearState();
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            setEnabled(false);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!grim.getValue()) {
            return;
        }

        if (!MovementSync.INSTANCE.isEnabled()) {
            setEnabled(false);
            return;
        }

        event.setForward(1.0F);
        event.setStrafe(0.0F);
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (isNull()
                || !mc.player.isAlive()
                || mc.player.isFallFlying()
                || mc.player.isPassenger()
                || grim.getValue() && !MovementSync.INSTANCE.isEnabled()) {
            setEnabled(false);
            return;
        }

        targetHole = findTargetHole();
        if (targetHole == null || ++enabledTicks >= timeoutTicks.getValue()) {
            setEnabled(false);
            return;
        }

        targetPosition = targetHole.centerAt(mc.player.getY());
        Vec3 offset = targetPosition.subtract(mc.player.position());
        double distance = Math.hypot(offset.x, offset.z);
        double cappedSpeed = Math.min(MAX_SPEED, distance);
        double x = distance <= 1.0E-6D ? 0.0D : offset.x / distance * cappedSpeed;
        double z = distance <= 1.0E-6D ? 0.0D : offset.z / distance * cappedSpeed;

        if (stuckTicks > MAX_STUCK_TICKS) {
            setEnabled(false);
            return;
        }

        if (grim.getValue()) {
            Vector2f rotations = RotationUtility.calculate(mc.player.position(), targetPosition);
            RotationManager.INSTANCE.setRotations(
                    new Vector2f(rotations.x, mc.player.getXRot()),
                    rotationSpeed.getValue(),
                    priority.getValue()
            );

            if (Math.abs(x) < 0.25D
                    && Math.abs(z) < 0.25D
                    && mc.player.getY() <= targetHole.pos().getY() + 0.8D) {
                setEnabled(false);
                return;
            }
        } else {
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(x, velocity.y, z);
            resetMove = true;

            if (Math.abs(x) < 0.1D
                    && Math.abs(z) < 0.1D
                    && mc.player.getY() <= targetHole.pos().getY() + 0.5D) {
                setEnabled(false);
                return;
            }
        }

        stuckTicks = mc.player.horizontalCollision ? stuckTicks + 1 : 0;
    }

    private HoleUtility.Hole findTargetHole() {
        return HoleUtility.findNearestHole(range.getValue(), true, anyHole.getValue(), up.getValue());
    }

    private void clearState() {
        targetHole = null;
        targetPosition = null;
        resetMove = false;
        stuckTicks = 0;
        enabledTicks = 0;
    }
}
