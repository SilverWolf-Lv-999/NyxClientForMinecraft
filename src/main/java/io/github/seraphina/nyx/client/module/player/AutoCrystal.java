package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.bus.EventHandler;
import io.github.seraphina.nyx.client.events.bus.EventPriority;
import io.github.seraphina.nyx.client.events.impl.ClickEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.SendPositionEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.Comparator;
import java.util.List;

@ModuleInfo(name = "nyxclient.module.autocrystal.name", description = "nyxclient.module.autocrystal.description", category = Category.PLAYER)
public class AutoCrystal extends Module {
    public static final AutoCrystal INSTANCE = new AutoCrystal();

    private static final int TICKS_PER_SECOND = 20;
    private static final float OUTGOING_ROTATION_DEDUP_YAW_STEP = 1.0F;
    private static final float OUTGOING_ROTATION_DEDUP_PITCH_STEP = 1.0F;

    public final IntValue rightCps = ValueBuild.intSetting("right cps", 10, 1, TICKS_PER_SECOND, 1, this);
    public final IntValue leftCps = ValueBuild.intSetting("left cps", 10, 1, TICKS_PER_SECOND, 1, this);

    private int rightClickProgress = TICKS_PER_SECOND;
    private int leftClickProgress = TICKS_PER_SECOND;
    private boolean usedInteractionThisTick;
    private Vector2f forcedOutgoingRotations;
    private Vector2f lastOutgoingRotations;
    private Vector2f syncedRotations;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventTarget
    public void onClick(ClickEvent event) {
        if (!shouldHandleInput()) {
            return;
        }

        event.setCancelled(true);
        drainUseClicks();
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        usedInteractionThisTick = false;

        if (!shouldHandleInput()) {
            refillClickProgress();
            return;
        }

        tickClickProgress();

        InteractionHand crystalHand = crystalHand();
        BlockTarget blockTarget = currentBlockTarget();
        BlockPos basePos = blockTarget != null ? blockTarget.basePos() : currentCrystalBaseTarget();
        if (basePos == null || crystalHand == null) {
            return;
        }

        if (tryAttackCrystal(basePos)) {
            return;
        }

        tryPlaceCrystal(blockTarget, crystalHand);
    }

    @EventHandler(priority = EventPriority.LOWEST - 1)
    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            ServerboundMovePlayerPacket outgoingPacket = packet;
            if (forcedOutgoingRotations != null) {
                outgoingPacket = applyMovePacketRotations(packet, forcedOutgoingRotations);
                event.setPacket(outgoingPacket);
                forcedOutgoingRotations = null;
            }

            if (outgoingPacket.hasRotation()) {
                updateLastOutgoingRotations(outgoingPacket);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST - 1)
    public void onSendPosition(SendPositionEvent event) {
        if (forcedOutgoingRotations == null) {
            return;
        }

        event.setYaw(forcedOutgoingRotations.x);
        event.setPitch(forcedOutgoingRotations.y);
        syncPlayerRotation(forcedOutgoingRotations);
    }

    private boolean shouldHandleInput() {
        return canRun()
                && mc.options.keyUse.isDown()
                && crystalHand() != null
                && currentBaseTarget() != null;
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator();
    }

    private boolean tryAttackCrystal(BlockPos basePos) {
        if (usedInteractionThisTick || leftClickProgress < TICKS_PER_SECOND) {
            return false;
        }

        EndCrystal crystal = findCrystalAbove(basePos);
        if (crystal == null) {
            return false;
        }

        if (!attackCrystal(crystal)) {
            return false;
        }

        leftClickProgress = Math.max(0, leftClickProgress - TICKS_PER_SECOND);
        return true;
    }

    private boolean tryPlaceCrystal(BlockTarget blockTarget, InteractionHand hand) {
        if (usedInteractionThisTick
                || rightClickProgress < TICKS_PER_SECOND
                || blockTarget == null
                || !canPlaceCrystalAt(blockTarget.basePos())) {
            return false;
        }

        if (!useCrystalOnBlock(blockTarget.hitResult(), hand)) {
            return false;
        }

        rightClickProgress = Math.max(0, rightClickProgress - TICKS_PER_SECOND);
        return true;
    }

    private boolean useCrystalOnBlock(BlockHitResult hitResult, InteractionHand hand) {
        Vector2f rotations = applyActionRotations(RotationUtility.calculate(hitResult.getLocation()));
        InteractionResult result = useItemOnWithRotations(rotations, hitResult, hand);
        if (!result.consumesAction()) {
            return false;
        }

        mc.player.swing(hand);
        mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
        usedInteractionThisTick = true;
        return true;
    }

    private boolean attackCrystal(EndCrystal crystal) {
        Vector2f rotations = applyActionRotations(RotationUtility.calculate(crystal.position()));
        attackWithRotations(rotations, crystal);
        mc.player.swing(InteractionHand.MAIN_HAND);
        usedInteractionThisTick = true;
        return true;
    }

    private BlockPos currentBaseTarget() {
        BlockTarget blockTarget = currentBlockTarget();
        if (blockTarget != null) {
            return blockTarget.basePos();
        }

        return currentCrystalBaseTarget();
    }

    private BlockTarget currentBlockTarget() {
        if (!(mc.hitResult instanceof BlockHitResult hitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos basePos = hitResult.getBlockPos();
        if (!isCrystalBaseTarget(basePos)) {
            return null;
        }

        return new BlockTarget(basePos, hitResult);
    }

    private BlockPos currentCrystalBaseTarget() {
        if (!(mc.hitResult instanceof EntityHitResult hitResult) || !(hitResult.getEntity() instanceof EndCrystal crystal)) {
            return null;
        }

        BlockPos basePos = crystal.blockPosition().below();
        return isCrystalBaseTarget(basePos) ? basePos : null;
    }

    private boolean isCrystalBaseTarget(BlockPos basePos) {
        return isCrystalBase(basePos) && mc.level.isEmptyBlock(basePos.above());
    }

    private boolean canPlaceCrystalAt(BlockPos basePos) {
        return isCrystalBaseTarget(basePos) && mc.level.getEntities((Entity) null, crystalSpawnBox(basePos)).isEmpty();
    }

    private boolean isCrystalBase(BlockPos pos) {
        return mc.level.getBlockState(pos).is(Blocks.OBSIDIAN) || mc.level.getBlockState(pos).is(Blocks.BEDROCK);
    }

    private EndCrystal findCrystalAbove(BlockPos basePos) {
        List<Entity> crystals = mc.level.getEntities(
                (Entity) null,
                crystalSpawnBox(basePos),
                entity -> entity instanceof EndCrystal crystal && crystal.isAlive() && !crystal.isRemoved()
        );

        Vec3 targetPos = crystalPosition(basePos);
        return crystals.stream()
                .map(EndCrystal.class::cast)
                .min(Comparator.comparingDouble(crystal -> crystal.position().distanceToSqr(targetPos)))
                .orElse(null);
    }

    private AABB crystalSpawnBox(BlockPos basePos) {
        BlockPos crystalBlockPos = basePos.above();
        return new AABB(
                crystalBlockPos.getX(),
                crystalBlockPos.getY(),
                crystalBlockPos.getZ(),
                crystalBlockPos.getX() + 1.0D,
                crystalBlockPos.getY() + 2.0D,
                crystalBlockPos.getZ() + 1.0D
        );
    }

    private Vec3 crystalPosition(BlockPos basePos) {
        return new Vec3(basePos.getX() + 0.5D, basePos.getY() + 1.0D, basePos.getZ() + 0.5D);
    }

    private InteractionHand crystalHand() {
        if (isCrystalStack(mc.player.getItemInHand(InteractionHand.MAIN_HAND))) {
            return InteractionHand.MAIN_HAND;
        }

        if (isCrystalStack(mc.player.getItemInHand(InteractionHand.OFF_HAND))) {
            return InteractionHand.OFF_HAND;
        }

        return null;
    }

    private boolean isCrystalStack(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(Items.END_CRYSTAL)
                && stack.isItemEnabled(mc.level.enabledFeatures())
                && !mc.player.getCooldowns().isOnCooldown(stack);
    }

    private void drainUseClicks() {
        while (mc.options.keyUse.consumeClick()) {
        }
    }

    private void tickClickProgress() {
        rightClickProgress = Math.min(rightClickProgress + rightCps.getValue(), TICKS_PER_SECOND);
        leftClickProgress = Math.min(leftClickProgress + leftCps.getValue(), TICKS_PER_SECOND);
    }

    private Vector2f applyActionRotations(Vector2f rotations) {
        Vector2f fixedRotations = legitimizeRotations(rotations);
        RotationManager.INSTANCE.setRotations(fixedRotations, 180.0D, Priority.Highest);

        Vector2f appliedRotations = makeUniqueOutgoingRotations(RotationManager.INSTANCE.getRotation());
        RotationManager.INSTANCE.setRotations(appliedRotations, 180.0D, Priority.Highest);
        syncPlayerRotation(appliedRotations);
        forcedOutgoingRotations = new Vector2f(appliedRotations);
        return appliedRotations;
    }

    private Vector2f makeUniqueOutgoingRotations(Vector2f rotations) {
        Vector2f uniqueRotations = legitimizeRotations(rotations);
        if (!sameRotations(uniqueRotations, lastOutgoingRotations)) {
            return uniqueRotations;
        }

        Vector2f steppedRotations = RotationUtility.applySensitivityPatch(
                new Vector2f(uniqueRotations.x, uniqueRotations.y + pitchDedupStep(uniqueRotations)),
                lastOutgoingRotations
        );
        uniqueRotations = legitimizeRotations(steppedRotations);
        if (!sameRotations(uniqueRotations, lastOutgoingRotations)) {
            return uniqueRotations;
        }

        steppedRotations = RotationUtility.applySensitivityPatch(
                new Vector2f(uniqueRotations.x + OUTGOING_ROTATION_DEDUP_YAW_STEP, uniqueRotations.y),
                lastOutgoingRotations
        );
        uniqueRotations = legitimizeRotations(steppedRotations);
        if (!sameRotations(uniqueRotations, lastOutgoingRotations)) {
            return uniqueRotations;
        }

        return legitimizeRotations(new Vector2f(
                lastOutgoingRotations.x + OUTGOING_ROTATION_DEDUP_YAW_STEP,
                Mth.clamp(lastOutgoingRotations.y + pitchDedupStep(lastOutgoingRotations), -90.0F, 90.0F)
        ));
    }

    private float pitchDedupStep(Vector2f rotations) {
        if (rotations != null && rotations.y >= 89.0F) {
            return -OUTGOING_ROTATION_DEDUP_PITCH_STEP;
        }

        return OUTGOING_ROTATION_DEDUP_PITCH_STEP;
    }

    private boolean sameRotations(Vector2f first, Vector2f second) {
        return first != null
                && second != null
                && Float.compare(first.x, second.x) == 0
                && Float.compare(first.y, second.y) == 0;
    }

    private Vector2f legitimizeRotations(Vector2f rotations) {
        if (mc.player == null || rotations == null) {
            return new Vector2f();
        }

        Vector2f baseRotations = currentSyncedRotations();
        float yaw = baseRotations.x + Mth.wrapDegrees(rotations.x - baseRotations.x);
        float pitch = Mth.clamp(rotations.y, -90.0F, 90.0F);
        return new Vector2f(yaw, pitch);
    }

    private Vector2f currentSyncedRotations() {
        return syncedRotations != null ? new Vector2f(syncedRotations) : currentPlayerRotations();
    }

    private Vector2f currentPlayerRotations() {
        if (mc.player == null) {
            return new Vector2f();
        }

        return new Vector2f(mc.player.getYRot(), mc.player.getXRot());
    }

    private void syncPlayerRotation(Vector2f rotations) {
        if (mc.player == null || rotations == null) {
            return;
        }

        syncedRotations = new Vector2f(rotations);
        mc.player.setYRot(rotations.x);
        mc.player.setXRot(rotations.y);
    }

    private void updateLastOutgoingRotations(ServerboundMovePlayerPacket packet) {
        Vector2f fallbackRotations = currentPlayerRotations();
        lastOutgoingRotations = new Vector2f(
                packet.getYRot(fallbackRotations.x),
                packet.getXRot(fallbackRotations.y)
        );
        syncedRotations = new Vector2f(lastOutgoingRotations);
    }

    private ServerboundMovePlayerPacket applyMovePacketRotations(ServerboundMovePlayerPacket packet, Vector2f rotations) {
        if (rotations == null) {
            return packet;
        }

        if (packet.hasPosition()) {
            return new ServerboundMovePlayerPacket.PosRot(
                    packet.getX(0.0D),
                    packet.getY(0.0D),
                    packet.getZ(0.0D),
                    rotations.x,
                    rotations.y,
                    packet.isOnGround(),
                    packet.horizontalCollision()
            );
        }

        return new ServerboundMovePlayerPacket.Rot(
                rotations.x,
                rotations.y,
                packet.isOnGround(),
                packet.horizontalCollision()
        );
    }

    private InteractionResult useItemOnWithRotations(Vector2f rotations, BlockHitResult hitResult, InteractionHand hand) {
        if (mc.player == null || mc.gameMode == null) {
            return InteractionResult.FAIL;
        }

        if (rotations == null) {
            return mc.gameMode.useItemOn(mc.player, hand, hitResult);
        }

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        mc.player.setYRot(rotations.x);
        mc.player.setXRot(rotations.y);

        try {
            return mc.gameMode.useItemOn(mc.player, hand, hitResult);
        } finally {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    private void attackWithRotations(Vector2f rotations, Entity entity) {
        if (mc.player == null || mc.gameMode == null) {
            return;
        }

        if (rotations == null) {
            mc.gameMode.attack(mc.player, entity);
            return;
        }

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        mc.player.setYRot(rotations.x);
        mc.player.setXRot(rotations.y);

        try {
            mc.gameMode.attack(mc.player, entity);
        } finally {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    private void refillClickProgress() {
        rightClickProgress = Math.min(rightClickProgress + rightCps.getValue(), TICKS_PER_SECOND);
        leftClickProgress = Math.min(leftClickProgress + leftCps.getValue(), TICKS_PER_SECOND);
    }

    private void resetState() {
        rightClickProgress = TICKS_PER_SECOND;
        leftClickProgress = TICKS_PER_SECOND;
        usedInteractionThisTick = false;
        forcedOutgoingRotations = null;
        lastOutgoingRotations = currentPlayerRotations();
        syncedRotations = new Vector2f(lastOutgoingRotations);
    }

    private record BlockTarget(BlockPos basePos, BlockHitResult hitResult) {
    }
}
