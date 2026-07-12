package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.LevelUpdateEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.RespawnEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.other.Target;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ModuleInfo(name = "nyxclient.module.tpaura.name", description = "nyxclient.module.tpaura.description", category = Category.COMBAT)
public class TpAura extends Module {
    public static final TpAura INSTANCE = new TpAura();

    private static final int TICKS_PER_SECOND = 20;

    public final EnumValue<Targets> targetMode = ValueBuild.enumSetting("target mode", Targets.SINGLE, this);
    public final DoubleValue range = ValueBuild.doubleSetting("range", 16.0D, 1.0D, 128.0D, 0.5D, this);
    public final IntValue cps = ValueBuild.intSetting("cps", 20, 1, TICKS_PER_SECOND, 1, this);
    public final IntValue maxTargets = ValueBuild.intSetting("max targets", 3, 1, 20, 1, () -> targetMode.is(Targets.MUTI), this);
    public final BoolValue oncePerTarget = ValueBuild.boolSetting("once per target", false, value -> {
        if (!value) {
            clearAttackedTargets();
        }
    }, this);
    public final BoolValue swing = ValueBuild.boolSetting("swing", true, this);
    public final BoolValue disableOnSetback = ValueBuild.boolSetting("disable on setback", true, this);

    private final Set<UUID> attackedTargets = new HashSet<>();
    private int attackProgress;

    @Override
    public void onEnable() {
        attackProgress = TICKS_PER_SECOND;

        if (isNull() || mc.gameMode == null || mc.player.connection == null) {
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        attackProgress = 0;
        clearAttackedTargets();
    }

    @EventTarget
    public void onLevelUpdate(LevelUpdateEvent event) {
        clearAttackedTargets();
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        clearAttackedTargets();
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (disableOnSetback.getValue() && event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            setEnabled(false);
        }
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (isNull() || mc.gameMode == null || mc.player.connection == null) {
            setEnabled(false);
            return;
        }

        List<LivingEntity> targets = findTargets();
        if (targets.isEmpty()) {
            attackProgress = Math.min(attackProgress + cps.getValue(), TICKS_PER_SECOND);
            return;
        }

        attackProgress += cps.getValue();
        if (attackProgress < TICKS_PER_SECOND) {
            return;
        }

        attackProgress -= TICKS_PER_SECOND;
        attackTargets(targets);
    }

    private void attackTargets(List<LivingEntity> targets) {
        Vec3 returnPos = mc.player.position();
        float returnYaw = mc.player.getYRot();
        float returnPitch = mc.player.getXRot();
        boolean returnOnGround = mc.player.onGround();
        boolean returnHorizontalCollision = mc.player.horizontalCollision;

        for (LivingEntity target : targets) {
            if (!isValidTarget(target)) {
                continue;
            }

            Vec3 spoofPos = teleportPosition(target);
            Vector2f rotations = rotationsFrom(spoofPos, target);
            sendPosition(spoofPos, rotations.x, rotations.y, returnOnGround, returnHorizontalCollision);
            attackWithRotations(target, rotations);
            markAttacked(target);
        }

        sendPosition(returnPos, returnYaw, returnPitch, returnOnGround, returnHorizontalCollision);
    }

    private void attackWithRotations(LivingEntity target, Vector2f rotations) {
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        mc.player.setYRot(rotations.x);
        mc.player.setXRot(rotations.y);

        try {
            mc.gameMode.attack(mc.player, target);
            if (swing.getValue()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        } finally {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    private void sendPosition(Vec3 pos, float yaw, float pitch, boolean onGround, boolean horizontalCollision) {
        mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
                pos.x,
                pos.y,
                pos.z,
                yaw,
                pitch,
                onGround,
                horizontalCollision
        ));
    }

    private Vec3 teleportPosition(LivingEntity target) {
        AABB box = target.getBoundingBox();
        Vec3 targetCenter = new Vec3(
                (box.minX + box.maxX) * 0.5D,
                target.getY(),
                (box.minZ + box.maxZ) * 0.5D
        );
        Vec3 playerPos = mc.player.position();
        double dx = playerPos.x - targetCenter.x;
        double dz = playerPos.z - targetCenter.z;
        double length = Math.hypot(dx, dz);

        if (length < 1.0E-4D) {
            dx = 1.0D;
            dz = 0.0D;
            length = 1.0D;
        }

        double distance = Math.max(target.getBbWidth() * 0.5D + 0.45D, 0.75D);
        return new Vec3(
                targetCenter.x + dx / length * distance,
                targetCenter.y,
                targetCenter.z + dz / length * distance
        );
    }

    private Vector2f rotationsFrom(Vec3 spoofPos, LivingEntity target) {
        Vec3 eyePos = spoofPos.add(0.0D, mc.player.getEyeHeight(mc.player.getPose()), 0.0D);
        Vec3 targetPos = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        return RotationUtility.calculate(eyePos, targetPos);
    }

    private List<LivingEntity> findTargets() {
        AABB searchBox = mc.player.getBoundingBox().inflate(range.getValue());
        List<Entity> entities = mc.level.getEntities(
                mc.player,
                searchBox,
                entity -> entity instanceof LivingEntity livingEntity && isValidTarget(livingEntity)
        );

        int limit = targetMode.is(Targets.MUTI) ? maxTargets.getValue() : 1;
        return entities.stream()
                .map(LivingEntity.class::cast)
                .sorted(Comparator.comparingDouble(RotationUtility::getEyeDistanceToEntity))
                .limit(limit)
                .toList();
    }

    private boolean isValidTarget(LivingEntity entity) {
        return entity != mc.player
                && entity.isAlive()
                && Target.isTarget(entity)
                && !entity.isSpectator()
                && entity.isPickable()
                && !entity.isInvulnerable()
                && !hasAttacked(entity)
                && RotationUtility.getEyeDistanceToEntity(entity) <= range.getValue();
    }

    private boolean hasAttacked(LivingEntity entity) {
        return oncePerTarget.getValue() && attackedTargets.contains(entity.getUUID());
    }

    private void markAttacked(LivingEntity entity) {
        if (oncePerTarget.getValue()) {
            attackedTargets.add(entity.getUUID());
        }
    }

    private void clearAttackedTargets() {
        attackedTargets.clear();
    }

    public enum Targets {
        SINGLE,
        MUTI
    }
}
