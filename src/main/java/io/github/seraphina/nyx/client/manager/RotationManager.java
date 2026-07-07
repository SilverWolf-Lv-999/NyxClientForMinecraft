package io.github.seraphina.nyx.client.manager;

import io.github.seraphina.nyx.client.events.api.EventManager;
import io.github.seraphina.nyx.client.events.bus.EventHandler;
import io.github.seraphina.nyx.client.events.bus.EventPriority;
import io.github.seraphina.nyx.client.events.impl.AttackYawEvent;
import io.github.seraphina.nyx.client.events.impl.FallFlyingEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.RaytraceEvent;
import io.github.seraphina.nyx.client.events.impl.RespawnEvent;
import io.github.seraphina.nyx.client.events.impl.RotationAnimationEvent;
import io.github.seraphina.nyx.client.events.impl.SendPositionEvent;
import io.github.seraphina.nyx.client.events.impl.UseItemRaytraceEvent;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import org.joml.Vector2f;

import java.util.function.Function;

public final class RotationManager {
    public static final RotationManager INSTANCE = new RotationManager();

    private static final Minecraft MC = Minecraft.getInstance();

    private final Vector2f offset = new Vector2f();
    public Vector2f rotations;
    public Vector2f lastRotations = new Vector2f();
    public Vector2f targetRotations;
    public Vector2f animationRotation;
    public Vector2f lastAnimationRotation;

    private boolean active;
    private boolean smoothed;
    private double rotationSpeed;
    private Function<Vector2f, Boolean> raytrace;
    private float randomAngle;
    private boolean serverCorrection;
    private int priority;

    private RotationManager() {
        EventManager.register(this);
    }

    public void setRotations(Vector2f rotations, double rotationSpeed) {
        setRotations(rotations, rotationSpeed, null, Priority.Medium);
    }

    public void setRotations(Vector2f rotations, double rotationSpeed, Priority priority) {
        setRotations(rotations, rotationSpeed, null, priority);
    }

    public void setRotations(Vector2f rotations, double rotationSpeed, Function<Vector2f, Boolean> raytrace) {
        setRotations(rotations, rotationSpeed, raytrace, Priority.Medium);
    }

    public void setRotations(Vector2f rotations, double rotationSpeed, Function<Vector2f, Boolean> raytrace, Priority priority) {
        if (rotations == null || MC.player == null) {
            return;
        }

        if (active && priority.priority < this.priority) {
            return;
        }

        if (serverCorrection) {
            Vector2f playerRotation = getPlayerRotation();
            this.rotations = playerRotation;
            this.lastRotations = new Vector2f(playerRotation);
            this.targetRotations = new Vector2f(playerRotation);
            serverCorrection = false;
            return;
        }

        targetRotations = rotations;
        this.rotationSpeed = rotationSpeed * 18.0;
        this.raytrace = raytrace;
        this.priority = priority.priority;
        active = true;

        smooth();
    }

    public Vector2f getRotation() {
        return active && rotations != null ? rotations : getPlayerRotation();
    }

    public Vector2f getLastRotation() {
        return lastRotations != null ? lastRotations : getPlayerRotation();
    }

    public boolean isDone() {
        return rotations != null
                && targetRotations != null
                && Math.abs(Mth.wrapDegrees(rotations.x - targetRotations.x)) <= 1.0F
                && Math.abs(Mth.wrapDegrees(rotations.y - targetRotations.y)) <= 1.0F;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isSmoothed() {
        return smoothed;
    }

    public void setSmoothed(boolean smoothed) {
        this.smoothed = smoothed;
    }

    private void smooth() {
        if (smoothed || targetRotations == null || MC.player == null) {
            return;
        }

        if (rotations == null) {
            rotations = getPlayerRotation();
        }

        if (lastRotations == null) {
            lastRotations = new Vector2f(rotations);
        }

        float targetYaw = targetRotations.x;
        float targetPitch = targetRotations.y;

        if (raytrace != null && (Math.abs(targetYaw - rotations.x) > 5.0F || Math.abs(targetPitch - rotations.y) > 5.0F)) {
            Vector2f trueTargetRotations = new Vector2f(targetRotations);

            double speed = Math.random() * Math.random() * Math.random() * 20.0;
            randomAngle += (float) ((20.0F + (float) (Math.random() - 0.5) * (Math.random() * Math.random() * Math.random() * 360.0)) * (MC.player.tickCount / 10 % 2 == 0 ? -1 : 1));

            offset.x += (float) (-Mth.sin((float) Math.toRadians(randomAngle)) * speed);
            offset.y += (float) (Mth.cos((float) Math.toRadians(randomAngle)) * speed);

            targetYaw += offset.x;
            targetPitch += offset.y;

            if (!raytrace.apply(new Vector2f(targetYaw, targetPitch))) {
                randomAngle = (float) Math.toDegrees(Math.atan2(trueTargetRotations.x - targetYaw, targetPitch - trueTargetRotations.y)) - 180.0F;

                targetYaw -= offset.x;
                targetPitch -= offset.y;

                offset.x += (float) (-Mth.sin((float) Math.toRadians(randomAngle)) * speed);
                offset.y += (float) (Mth.cos((float) Math.toRadians(randomAngle)) * speed);

                targetYaw += offset.x;
                targetPitch += offset.y;
            }

            if (!raytrace.apply(new Vector2f(targetYaw, targetPitch))) {
                offset.set(0.0F, 0.0F);
                targetYaw = (float) (targetRotations.x + Math.random() * 2.0);
                targetPitch = (float) (targetRotations.y + Math.random() * 2.0);
            }
        }

        rotations = RotationUtility.smooth(lastRotations, new Vector2f(targetYaw, targetPitch), rotationSpeed + Math.random());
        smoothed = true;
    }

    @EventHandler
    private void onRespawn(RespawnEvent event) {
        lastRotations = null;
        rotations = null;
        targetRotations = null;
        animationRotation = null;
        lastAnimationRotation = null;
        active = false;
        smoothed = false;
        priority = 0;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (active && rotations != null && event.getPacket() instanceof ServerboundUseItemPacket packet) {
            event.setPacket(new ServerboundUseItemPacket(packet.getHand(), packet.getSequence(), rotations.x, rotations.y));
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket || event.getPacket() instanceof ClientboundPlayerRotationPacket) {
            serverCorrection = true;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTick(PlayerTickEvent event) {
        if (MC.player == null) {
            return;
        }

        if (!active || rotations == null || lastRotations == null || targetRotations == null) {
            Vector2f playerRotation = getPlayerRotation();
            rotations = new Vector2f(playerRotation);
            lastRotations = new Vector2f(playerRotation);
            targetRotations = new Vector2f(playerRotation);
        }

        if (active) {
            smooth();
        }
    }

    @EventHandler
    private void onAnimation(RotationAnimationEvent event) {
        if (active && animationRotation != null && lastAnimationRotation != null) {
            event.setYaw(animationRotation.x);
            event.setLastYaw(lastAnimationRotation.x);
            event.setPitch(animationRotation.y);
            event.setLastPitch(lastAnimationRotation.y);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onSendPosition(SendPositionEvent event) {
        if (MC.player == null) {
            return;
        }

        if (active && rotations != null) {
            float yaw = rotations.x;
            float pitch = rotations.y;

            if (!Float.isNaN(yaw) && !Float.isNaN(pitch)) {
                event.setYaw(yaw);
                event.setPitch(pitch);
            }

            if (Math.abs((rotations.x - MC.player.getYRot()) % 360.0F) < 1.0F && Math.abs(rotations.y - MC.player.getXRot()) < 1.0F) {
                active = false;
                priority = 0;
                correctDisabledRotations();
            }

            lastRotations = new Vector2f(rotations);
        } else {
            lastRotations = getPlayerRotation();
        }

        lastAnimationRotation = animationRotation;
        animationRotation = new Vector2f(event.getYaw(), event.getPitch());
        targetRotations = getPlayerRotation();
        raytrace = null;
        smoothed = false;
    }

    @EventHandler
    private void onRaytrace(RaytraceEvent event) {
        if (active && rotations != null && event.getEntity() == MC.player) {
            event.setYaw(rotations.x);
            event.setPitch(rotations.y);
        }
    }

    @EventHandler
    private void onItemRaytrace(UseItemRaytraceEvent event) {
        if (active && rotations != null) {
            event.setYaw(rotations.x);
            event.setPitch(rotations.y);
        }
    }

    @EventHandler
    private void onFallFlying(FallFlyingEvent event) {
        if (active && rotations != null) {
            event.setPitch(rotations.y);
        }
    }

    @EventHandler
    private void onAttack(AttackYawEvent event) {
        if (active && rotations != null) {
            event.setYaw(rotations.x);
        }
    }

    private void correctDisabledRotations() {
        if (MC.player == null || lastRotations == null) {
            return;
        }

        Vector2f playerRotations = getPlayerRotation();
        Vector2f fixedRotations = RotationUtility.resetRotation(RotationUtility.applySensitivityPatch(playerRotations, lastRotations));

        if (fixedRotations != null) {
            MC.player.setYRot(fixedRotations.x);
            MC.player.setXRot(fixedRotations.y);
        }
    }

    private Vector2f getPlayerRotation() {
        if (MC.player == null) {
            return new Vector2f();
        }

        return new Vector2f(MC.player.getYRot(), MC.player.getXRot());
    }
}
