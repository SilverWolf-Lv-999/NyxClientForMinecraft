package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.other.Target;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RaytraceUtility;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector2f;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "nyxclient.module.killaura.name", description = "nyxclient.module.killaura.description", category = Category.COMBAT)
public class KillAura extends Module {
    public static final KillAura INSTANCE = new KillAura();

    private static final int MIN_CPS = 1;
    private static final int MAX_CPS = 20;

    public final EnumValue<TargetMode> targetMode = ValueBuild.enumSetting("target mode", TargetMode.SINGLE, this);
    public final IntValue switchDelay = ValueBuild.intSetting(
            "switch delay",
            200,
            0,
            1_000,
            50,
            () -> targetMode.is(TargetMode.SWITCH),
            this
    );
    public final EnumValue<TargetPriority> priority = ValueBuild.enumSetting("priority", TargetPriority.DISTANCE, this);
    public final EnumValue<CombatMode> combatMode = ValueBuild.enumSetting("combat mode", CombatMode.LEGACY, this);
    public final IntValue maxCps = ValueBuild.intSetting(
            "max cps",
            10,
            MIN_CPS,
            MAX_CPS,
            1,
            () -> combatMode.is(CombatMode.LEGACY),
            this
    );
    public final IntValue minCps = ValueBuild.intSetting(
            "min cps",
            7,
            MIN_CPS,
            MAX_CPS,
            1,
            () -> combatMode.is(CombatMode.LEGACY),
            this
    );
    public final BoolValue keepSwing = ValueBuild.boolSetting(
            "keep swing",
            false,
            () -> combatMode.is(CombatMode.MODERN),
            this
    );
    public final DoubleValue attackRange = ValueBuild.doubleSetting("attack range", 3.0D, 3.0D, 8.0D, 0.1D, this);
    public final DoubleValue blockRange = ValueBuild.doubleSetting("block range", 4.0D, 3.0D, 8.0D, 0.1D, this);
    public final DoubleValue wallRange = ValueBuild.doubleSetting("wall range", 0.0D, 0.0D, 8.0D, 0.1D, this);
    public final DoubleValue rotationRange = ValueBuild.doubleSetting("rotation range", 4.0D, 3.0D, 8.0D, 0.1D, this);
    public final EnumValue<AutoBlockMode> autoBlockMode = ValueBuild.enumSetting("auto block mode", AutoBlockMode.NONE, this);
    public final DoubleValue rotationSpeed = ValueBuild.doubleSetting("rotation speed", 180.0D, 0.0D, 180.0D, 5.0D, this);
    public final BoolValue rayCast = ValueBuild.boolSetting("ray cast", false, this);

    private LivingEntity target;
    private Vector2f targetRotations;
    private long nextAttackTime;
    private long nextSwitchTime;
    private boolean blocking;
    private boolean forcedUseKey;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        stopBlocking();
        resetState();
    }

    @EventTarget
    public void onPreTick(TickEvent.Pre event) {
        if (!canRun()) {
            clearTarget();
            stopBlocking();
            return;
        }

        List<LivingEntity> targets = findTargets();
        target = selectTarget(targets);
        targetRotations = target == null ? null : RotationUtility.calculate(target, true, rotationRange.getValue());

        if (targetRotations != null) {
            RotationManager.INSTANCE.setRotations(targetRotations, rotationSpeed.getValue(), Priority.High);
        }

        updateBlocking();
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (!canRun() || target == null || targetRotations == null || !canAttack(target)) {
            return;
        }

        if (keepSwing.getValue() && combatMode.is(CombatMode.MODERN)) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        if (combatMode.is(CombatMode.MODERN) && mc.player.getAttackStrengthScale(0.5F) < 1.0F) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextAttackTime) {
            return;
        }

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        nextAttackTime = now + attackDelayMillis();
    }

    private List<LivingEntity> findTargets() {
        AABB searchBox = mc.player.getBoundingBox().inflate(rotationRange.getValue());
        Comparator<LivingEntity> comparator = targetComparator();

        return mc.level.getEntities(
                        mc.player,
                        searchBox,
                        entity -> entity instanceof LivingEntity livingEntity && isValidTarget(livingEntity)
                )
                .stream()
                .map(LivingEntity.class::cast)
                .sorted(comparator)
                .toList();
    }

    private LivingEntity selectTarget(List<LivingEntity> targets) {
        if (targets.isEmpty()) {
            nextSwitchTime = 0L;
            return null;
        }

        if (targetMode.is(TargetMode.SINGLE)) {
            return targets.getFirst();
        }

        long now = System.currentTimeMillis();
        if (target == null || !targets.contains(target) || now >= nextSwitchTime) {
            LivingEntity selectedTarget = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
            nextSwitchTime = now + switchDelay.getValue();
            return selectedTarget;
        }

        return target;
    }

    private Comparator<LivingEntity> targetComparator() {
        return switch (priority.getValue()) {
            case HEALTH -> Comparator.comparingDouble(entity -> entity.getHealth() + entity.getAbsorptionAmount());
            case FOV -> Comparator.comparingDouble(this::rotationDifference);
            case LIVING_TIME -> Comparator.comparingInt((LivingEntity entity) -> entity.tickCount).reversed();
            case ARMOR -> Comparator.comparingInt(LivingEntity::getArmorValue);
            case DISTANCE -> Comparator.comparingDouble(RotationUtility::getEyeDistanceToEntity);
        };
    }

    private boolean isValidTarget(LivingEntity entity) {
        return entity != mc.player
                && entity.isAlive()
                && Target.isTarget(entity)
                && !entity.isSpectator()
                && entity.isPickable()
                && !entity.isInvulnerable()
                && RotationUtility.getEyeDistanceToEntity(entity) <= rotationRange.getValue();
    }

    private boolean canAttack(LivingEntity entity) {
        double range = canSee(entity) ? attackRange.getValue() : wallRange.getValue();
        if (RotationUtility.getEyeDistanceToEntity(entity) > range) {
            return false;
        }

        if (!rayCast.getValue()) {
            return true;
        }

        HitResult hitResult = RaytraceUtility.raytrace(targetRotations, range);
        return hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() == entity;
    }

    private boolean canSee(LivingEntity entity) {
        return RaytraceUtility.canSeePointFrom(mc.player.getEyePosition(), entity.getBoundingBox().getCenter());
    }

    private void updateBlocking() {
        if (target == null || !canBlock(target)) {
            stopBlocking();
            return;
        }

        startBlocking();
    }

    private boolean canBlock(LivingEntity entity) {
        return !autoBlockMode.is(AutoBlockMode.NONE)
                && RotationUtility.getEyeDistanceToEntity(entity) <= blockRange.getValue()
                && getShieldHand() != null;
    }

    private void startBlocking() {
        if (blocking) {
            return;
        }

        blocking = true;
        switch (autoBlockMode.getValue()) {
            case USE_ITEM -> {
                mc.options.keyUse.setDown(true);
                forcedUseKey = true;
            }
            case VANILLA -> mc.gameMode.useItem(mc.player, getShieldHand());
            case FAKE, NONE -> {
            }
        }
    }

    private void stopBlocking() {
        if (forcedUseKey) {
            mc.options.keyUse.setDown(false);
            forcedUseKey = false;
        }

        if (blocking && autoBlockMode.is(AutoBlockMode.VANILLA) && mc.player != null && mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
        }

        blocking = false;
    }

    private InteractionHand getShieldHand() {
        if (mc.player == null) {
            return null;
        }

        if (mc.player.getMainHandItem().is(Items.SHIELD)) {
            return InteractionHand.MAIN_HAND;
        }

        return mc.player.getOffhandItem().is(Items.SHIELD) ? InteractionHand.OFF_HAND : null;
    }

    private long attackDelayMillis() {
        if (combatMode.is(CombatMode.MODERN)) {
            return 0L;
        }

        int minimum = Math.min(minCps.getValue(), maxCps.getValue());
        int maximum = Math.max(minCps.getValue(), maxCps.getValue());
        int cps = ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
        return 700L / cps;
    }

    private double rotationDifference(LivingEntity entity) {
        Vector2f rotations = RotationUtility.getRotationsToEntity(entity);
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(rotations.x - mc.player.getYRot()));
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && !mc.player.isSpectator()
                && mc.screen == null;
    }

    private void clearTarget() {
        target = null;
        targetRotations = null;
        nextSwitchTime = 0L;
    }

    private void resetState() {
        clearTarget();
        nextAttackTime = 0L;
        blocking = false;
        forcedUseKey = false;
    }

    public enum TargetMode {
        SINGLE,
        SWITCH
    }

    public enum TargetPriority {
        DISTANCE,
        HEALTH,
        FOV,
        LIVING_TIME,
        ARMOR
    }

    public enum CombatMode {
        LEGACY,
        MODERN
    }

    public enum AutoBlockMode {
        NONE,
        FAKE,
        USE_ITEM,
        VANILLA
    }
}
