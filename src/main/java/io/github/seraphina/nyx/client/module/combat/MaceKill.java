package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.DebugUtility;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector2f;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@ModuleInfo(name = "nyxclient.module.macekill.name", description = "nyxclient.module.macekill.description", category = Category.COMBAT)
public class MaceKill extends Module {
    public static final MaceKill INSTANCE = new MaceKill();

    private static final double GRAVITY = 0.08D;
    private static final double DRAG = 0.98D;
    private static final int STATUS_INTERVAL_TICKS = 20;

    public final DoubleValue range = ValueBuild.doubleValue("range", 4.5D, 1.0D, 8.0D, 0.1D, this);
    public final IntValue packets = ValueBuild.intSetting("packets", 5, 1, 20, 1, this);

    private boolean sendingMovePacket;
    private boolean skipDisableAttack;
    private double spoofedY;
    private double fallVelocity;
    private double spoofedFallDistance;
    private int sentPackets;
    private int statusTicks;

    @Override
    public void onEnable() {
        skipDisableAttack = false;
        resetFallState();
        DebugUtility.msg("MaceKill enabled: range=", format(range.getValue()), ", packets=", packets.getValue());
    }

    @Override
    public void onDisable() {
        if (skipDisableAttack) {
            skipDisableAttack = false;
            DebugUtility.msg("MaceKill disabled: server corrected position");
            resetFallState();
            return;
        }

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            DebugUtility.msg("MaceKill disabled: player unavailable");
            resetFallState();
            return;
        }

        LivingEntity target = findTarget(range.getValue());
        if (target == null) {
            DebugUtility.msg("MaceKill disabled: no target within ", format(range.getValue()), " blocks");
            resetFallState();
            return;
        }

        attackTarget(target);
        resetFallState();
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        event.setForward(0.0F);
        event.setStrafe(0.0F);
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (!sendingMovePacket && event.getPacket() instanceof ServerboundMovePlayerPacket) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            skipDisableAttack = true;
            setEnabled(false);
            return;
        }

        if (mc.player != null
                && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet
                && packet.getId() == mc.player.getId()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (mc.player.onGround()) {
            resetFallState();
            return;
        }

        spoofedY = Math.min(spoofedY, mc.player.getY());

        for (int i = 0; i < packets.getValue(); i++) {
            fallVelocity = (fallVelocity - GRAVITY) * DRAG;
            spoofedY += fallVelocity;
            spoofedFallDistance = Math.max(spoofedFallDistance, mc.player.getY() - spoofedY);

            sendMovePacket(new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(),
                    spoofedY,
                    mc.player.getZ(),
                    mc.player.getYRot(),
                    mc.player.getXRot(),
                    false,
                    mc.player.horizontalCollision
            ));

            sentPackets++;
        }

        mc.player.fallDistance = Math.max(mc.player.fallDistance, (float) spoofedFallDistance);
        sendFallStatus();
    }

    private void attackTarget(LivingEntity target) {
        Vector2f rotations = RotationUtility.calculate(target, true, range.getValue());
        RotationManager.INSTANCE.setRotations(rotations, 180.0D, Priority.Highest);

        sendMovePacket(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                rotations.x,
                rotations.y,
                false,
                mc.player.horizontalCollision
        ));

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);

        DebugUtility.msg(
                "MaceKill attacked ",
                target.getName().getString(),
                ": packets=",
                sentPackets,
                ", fall=",
                format(spoofedFallDistance)
        );
    }

    private LivingEntity findTarget(double attackRange) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        AABB searchBox = mc.player.getBoundingBox().inflate(attackRange);
        List<Entity> entities = mc.level.getEntities(
                mc.player,
                searchBox,
                entity -> entity instanceof LivingEntity livingEntity && isValidTarget(livingEntity, attackRange)
        );

        return entities.stream()
                .map(LivingEntity.class::cast)
                .min(Comparator.comparingDouble(RotationUtility::getEyeDistanceToEntity))
                .orElse(null);
    }

    private boolean isValidTarget(LivingEntity entity, double attackRange) {
        return entity != mc.player
                && entity.isAlive()
                && !entity.isSpectator()
                && entity.isPickable()
                && !entity.isInvulnerable()
                && RotationUtility.getEyeDistanceToEntity(entity) <= attackRange;
    }

    private void sendMovePacket(ServerboundMovePlayerPacket packet) {
        if (mc.player == null || mc.player.connection == null) {
            return;
        }

        sendingMovePacket = true;
        try {
            mc.player.connection.send(packet);
        } finally {
            sendingMovePacket = false;
        }
    }

    private void sendFallStatus() {
        if (sentPackets == packets.getValue()) {
            DebugUtility.msg("MaceKill falling: packets=", sentPackets, ", fall=", format(spoofedFallDistance));
            return;
        }

        statusTicks++;
        if (statusTicks >= STATUS_INTERVAL_TICKS) {
            statusTicks = 0;
            DebugUtility.msg("MaceKill falling: packets=", sentPackets, ", fall=", format(spoofedFallDistance));
        }
    }

    private void resetFallState() {
        spoofedY = mc.player != null ? mc.player.getY() : 0.0D;
        fallVelocity = 0.0D;
        spoofedFallDistance = 0.0D;
        sentPackets = 0;
        statusTicks = 0;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
