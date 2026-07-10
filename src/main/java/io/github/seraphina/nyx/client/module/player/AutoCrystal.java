package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.bus.EventHandler;
import io.github.seraphina.nyx.client.events.bus.EventPriority;
import io.github.seraphina.nyx.client.events.impl.ClickEvent;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.SendPositionEvent;
import io.github.seraphina.nyx.client.events.impl.StartUseItemEvent;
import io.github.seraphina.nyx.client.events.impl.StrafeEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RaytraceUtility;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
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
    private static final double INSTANT_ROTATION_SPEED = 180.0D;
    private static final float ACTION_ROTATION_SYNC_EPSILON = 1.0F;
    private static final float ACTION_TARGET_ROTATION_EPSILON = 2.0F;
    private static final float OUTGOING_ROTATION_DEDUP_YAW_STEP = 1.0F;
    private static final float OUTGOING_ROTATION_DEDUP_PITCH_STEP = 1.0F;
    private static final float OUTGOING_ROTATION_DUPLICATE_EPSILON = 1.0E-4F;

    public final IntValue rightCps = ValueBuild.intSetting("right cps", 10, 1, TICKS_PER_SECOND, 1, this);
    public final IntValue leftCps = ValueBuild.intSetting("left cps", 10, 1, TICKS_PER_SECOND, 1, this);

    private int rightClickProgress = TICKS_PER_SECOND;
    private int leftClickProgress = TICKS_PER_SECOND;
    private boolean usedInteractionThisTick;
    private boolean waitForUseRelease;
    private Vector2f lastOutgoingRotations;
    private Vector2f lastControlledRotations;
    private Vector2f movementFixRotations;
    private boolean controllingRotations;
    private ActionTarget preparedActionTarget;

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
        if (shouldBlockUseInput()) {
            event.setCancelled(true);
            drainUseClicks();
            return;
        }

        if (!shouldHandleInput()) {
            return;
        }

        event.setCancelled(true);
        drainUseClicks();
    }

    @EventTarget
    public void onStartUseItem(StartUseItemEvent event) {
        if (!shouldBlockUseInput()) {
            return;
        }

        event.setCancelled(true);
        drainUseClicks();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        movementFixRotations = null;

        if (!shouldFixMovement(event)) {
            return;
        }

        ActionTarget target = previewActionTarget();
        if (target == null) {
            return;
        }

        Vector2f rotations = outgoingRotationsForMovement(target);
        if (rotations == null) {
            return;
        }

        MovementInput fixedInput = correctedMovementInput(
                event.getForward(),
                event.getStrafe(),
                mc.player.getYRot(),
                rotations.x
        );

        event.setForward(fixedInput.forward());
        event.setStrafe(fixedInput.strafe());
        if (fixedInput.forward() <= 0.0F) {
            event.setSprint(false);
        }

        movementFixRotations = new Vector2f(rotations);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStrafe(StrafeEvent event) {
        if (movementFixRotations != null && shouldFixMovement()) {
            event.setYaw(movementFixRotations.x);
        }
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        tickUseReleaseLock();
        usedInteractionThisTick = false;

        if (!canRun()) {
            releaseControlledRotations();
            clearPreparedAction();
            refillClickProgress();
            return;
        }

        tickClickProgress();

        if (waitForUseRelease || !mc.options.keyUse.isDown()) {
            releaseControlledRotations();
            clearPreparedAction();
            return;
        }

        InteractionHand hand = crystalHand();
        if (hand == null) {
            releaseControlledRotations();
            clearPreparedAction();
            return;
        }

        runPreparedAction(hand);
    }

    private void runPreparedAction(InteractionHand hand) {
        ActionTarget target = preparedActionTarget;
        if (target == null) {
            return;
        }

        if (!hasReadyInteraction(target)) {
            if (!hasActionOpportunity(target)) {
                clearPreparedAction();
            }
            return;
        }

        if (!isActionRotationSynced(target.rotations())) {
            return;
        }

        clearPreparedAction();
        if (tryAttackCrystal(target.basePos(), target.rotations())) {
            return;
        }

        tryPlaceCrystal(target.blockTarget(), hand, target.rotations());
    }

    @EventHandler(priority = EventPriority.LOWEST - 1)
    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            if (packet.hasRotation()) {
                updateLastOutgoingRotations(packet);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST - 1)
    public void onSendPosition(SendPositionEvent event) {
        ActionTarget target = currentActionTarget();
        if (target == null) {
            releaseControlledRotations();
            clearPreparedAction();
            setOutgoingRotations(event, movementFixRotations);
            return;
        }

        Vector2f outgoingRotations = outgoingRotationsForAction(target);
        setOutgoingRotations(event, outgoingRotations);
        if (hasActionOpportunity(target)) {
            preparedActionTarget = new ActionTarget(target.blockTarget(), target.basePos(), outgoingRotations);
        } else {
            clearPreparedAction();
        }
    }

    private void clearPreparedAction() {
        preparedActionTarget = null;
    }

    private boolean shouldHandleInput() {
        return canRun()
                && !waitForUseRelease
                && mc.options.keyUse.isDown()
                && crystalHand() != null
                && currentBaseTarget() != null;
    }

    private boolean shouldBlockUseInput() {
        return canRun()
                && waitForUseRelease
                && mc.options.keyUse.isDown();
    }

    private boolean shouldFixMovement(MoveInputEvent event) {
        return shouldFixMovement()
                && (event.getForward() != 0.0F || event.getStrafe() != 0.0F);
    }

    private boolean shouldFixMovement() {
        return canRun()
                && !waitForUseRelease
                && mc.options.keyUse.isDown()
                && crystalHand() != null;
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator();
    }

    private boolean tryAttackCrystal(BlockPos basePos, Vector2f rotations) {
        if (usedInteractionThisTick || leftClickProgress < TICKS_PER_SECOND) {
            return false;
        }

        EndCrystal crystal = findCrystalAbove(basePos);
        if (crystal == null) {
            return false;
        }

        if (!isRotationForCrystal(crystal, rotations)) {
            return false;
        }

        if (!attackCrystal(crystal, rotations)) {
            return false;
        }

        leftClickProgress = Math.max(0, leftClickProgress - TICKS_PER_SECOND);
        return true;
    }

    private boolean tryPlaceCrystal(BlockTarget blockTarget, InteractionHand hand, Vector2f rotations) {
        if (usedInteractionThisTick
                || rightClickProgress < TICKS_PER_SECOND
                || blockTarget == null
                || !canPlaceCrystalAt(blockTarget.basePos())) {
            return false;
        }

        if (!isRotationForBlockHit(blockTarget.hitResult(), rotations)) {
            return false;
        }

        if (!useCrystalOnBlock(blockTarget.hitResult(), hand, rotations)) {
            return false;
        }

        rightClickProgress = Math.max(0, rightClickProgress - TICKS_PER_SECOND);
        return true;
    }

    private boolean useCrystalOnBlock(BlockHitResult hitResult, InteractionHand hand, Vector2f rotations) {
        if (!canPlaceCrystalAt(hitResult.getBlockPos())) {
            return false;
        }

        boolean wasLastUsableCrystal = usableCrystalCountInHands() <= 1;
        InteractionResult result = useItemOnWithRotations(rotations, hitResult, hand);
        if (!result.consumesAction()) {
            return false;
        }

        mc.player.swing(hand);
        mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
        usedInteractionThisTick = true;
        if (wasLastUsableCrystal || !hasCrystalInHands()) {
            waitForUseRelease = mc.options.keyUse.isDown();
        }
        return true;
    }

    private boolean attackCrystal(EndCrystal crystal, Vector2f rotations) {
        if (crystal == null || !crystal.isAlive() || crystal.isRemoved()) {
            return false;
        }

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        mc.player.setYRot(rotations.x);
        mc.player.setXRot(rotations.y);

        try {
            // Packet-only left click: avoid local attack side effects and keep the view silent.
            mc.player.connection.send(ServerboundInteractPacket.createAttackPacket(crystal, mc.player.isShiftKeyDown()));
            mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        } finally {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }

        usedInteractionThisTick = true;
        return true;
    }

    private boolean isRotationForCrystal(EndCrystal crystal, Vector2f rotations) {
        return closeActionRotations(rotations, legitimizeRotations(RotationUtility.calculate(crystal)));
    }

    private boolean isRotationForBlockHit(BlockHitResult hitResult, Vector2f rotations) {
        return closeActionRotations(rotations, legitimizeRotations(RotationUtility.calculate(hitResult.getLocation())));
    }

    private boolean closeActionRotations(Vector2f actual, Vector2f expected) {
        return actual != null
                && expected != null
                && Math.abs(Mth.wrapDegrees(actual.x - expected.x)) <= ACTION_TARGET_ROTATION_EPSILON
                && Math.abs(actual.y - expected.y) <= ACTION_TARGET_ROTATION_EPSILON;
    }

    private BlockPos currentBaseTarget() {
        BlockTarget blockTarget = currentBlockTarget();
        if (blockTarget != null) {
            return blockTarget.basePos();
        }

        return currentCrystalBaseTarget();
    }

    private BlockTarget currentBlockTarget() {
        HitResult manualHitResult = manualAimHitResult();
        if (!(manualHitResult instanceof BlockHitResult hitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos basePos = hitResult.getBlockPos();
        if (!isCrystalBaseTarget(basePos)) {
            return null;
        }

        return new BlockTarget(basePos, hitResult);
    }

    private BlockPos currentCrystalBaseTarget() {
        HitResult manualHitResult = manualAimHitResult();
        if (!(manualHitResult instanceof EntityHitResult hitResult) || !(hitResult.getEntity() instanceof EndCrystal crystal)) {
            return null;
        }

        BlockPos basePos = crystal.blockPosition().below();
        return isCrystalBaseTarget(basePos) ? basePos : null;
    }

    private HitResult manualAimHitResult() {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        return RaytraceUtility.raytrace(currentPlayerRotations(), manualAimRange());
    }

    private double manualAimRange() {
        if (mc.player == null) {
            return 0.0D;
        }

        return Math.max(mc.player.blockInteractionRange(), mc.player.entityInteractionRange());
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

    private int usableCrystalCountInHands() {
        return usableCrystalCount(mc.player.getItemInHand(InteractionHand.MAIN_HAND))
                + usableCrystalCount(mc.player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private int usableCrystalCount(ItemStack stack) {
        return isCrystalStack(stack) ? stack.getCount() : 0;
    }

    private boolean hasCrystalInHands() {
        return hasCrystalItem(mc.player.getItemInHand(InteractionHand.MAIN_HAND))
                || hasCrystalItem(mc.player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private boolean hasCrystalItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.END_CRYSTAL);
    }

    private void tickUseReleaseLock() {
        if (!canRun() || !mc.options.keyUse.isDown()) {
            waitForUseRelease = false;
        }
    }

    private void drainUseClicks() {
        while (mc.options.keyUse.consumeClick()) {
        }
    }

    private void tickClickProgress() {
        rightClickProgress = Math.min(rightClickProgress + rightCps.getValue(), TICKS_PER_SECOND);
        leftClickProgress = Math.min(leftClickProgress + leftCps.getValue(), TICKS_PER_SECOND);
    }

    private ActionTarget previewActionTarget() {
        if (!canRun() || waitForUseRelease || !mc.options.keyUse.isDown()) {
            return null;
        }

        InteractionHand hand = crystalHand();
        if (hand == null) {
            return null;
        }

        BlockTarget blockTarget = currentBlockTarget();
        BlockPos basePos = blockTarget != null ? blockTarget.basePos() : currentCrystalBaseTarget();
        if (basePos == null) {
            return null;
        }

        Vector2f rotations = targetRotations(blockTarget, basePos);
        return rotations == null ? null : new ActionTarget(blockTarget, basePos, legitimizeRotations(rotations));
    }

    private ActionTarget currentActionTarget() {
        if (!canRun() || waitForUseRelease || !mc.options.keyUse.isDown()) {
            return null;
        }

        InteractionHand hand = crystalHand();
        if (hand == null) {
            return null;
        }

        BlockTarget blockTarget = currentBlockTarget();
        BlockPos basePos = blockTarget != null ? blockTarget.basePos() : currentCrystalBaseTarget();
        if (basePos == null) {
            return null;
        }

        Vector2f rotations = updateControlledRotations(blockTarget, basePos);
        return rotations == null ? null : new ActionTarget(blockTarget, basePos, rotations);
    }

    private Vector2f updateControlledRotations(BlockTarget blockTarget, BlockPos basePos) {
        Vector2f rotations = targetRotations(blockTarget, basePos);
        if (rotations == null) {
            releaseControlledRotations();
            return null;
        }

        Vector2f appliedRotations = setTrackingRotations(rotations);
        lastControlledRotations = new Vector2f(appliedRotations);
        controllingRotations = true;
        return appliedRotations;
    }

    private Vector2f outgoingRotationsForMovement(ActionTarget target) {
        if (target == null) {
            return null;
        }

        if (!isActionRotationSynced(target.rotations())) {
            return target.rotations();
        }

        if (hasReadyInteraction(target)) {
            return makeUniqueOutgoingRotations(target.rotations());
        }

        return lastOutgoingRotations == null ? target.rotations() : new Vector2f(lastOutgoingRotations);
    }

    private Vector2f outgoingRotationsForAction(ActionTarget target) {
        Vector2f outgoingRotations = movementFixRotations != null ? movementFixRotations : target.rotations();
        if (hasReadyInteraction(target) && isActionRotationSynced(outgoingRotations)) {
            return makeUniqueOutgoingRotations(outgoingRotations);
        }

        return outgoingRotations;
    }

    private boolean hasReadyInteraction(ActionTarget target) {
        return canAttackCrystalNow(target.basePos()) || canPlaceCrystalNow(target.blockTarget());
    }

    private boolean hasActionOpportunity(ActionTarget target) {
        return findCrystalAbove(target.basePos()) != null
                || (target.blockTarget() != null && canPlaceCrystalAt(target.blockTarget().basePos()));
    }

    private boolean canAttackCrystalNow(BlockPos basePos) {
        return !usedInteractionThisTick
                && leftClickProgress >= TICKS_PER_SECOND
                && findCrystalAbove(basePos) != null;
    }

    private boolean canPlaceCrystalNow(BlockTarget blockTarget) {
        return !usedInteractionThisTick
                && rightClickProgress >= TICKS_PER_SECOND
                && blockTarget != null
                && canPlaceCrystalAt(blockTarget.basePos());
    }

    private Vector2f targetRotations(BlockTarget blockTarget, BlockPos basePos) {
        EndCrystal crystal = findCrystalAbove(basePos);
        if (crystal != null) {
            return RotationUtility.calculate(crystal);
        }

        if (blockTarget != null) {
            return RotationUtility.calculate(blockTarget.hitResult().getLocation());
        }

        return null;
    }

    private Vector2f setTrackingRotations(Vector2f rotations) {
        return setRotationManagerRotations(rotations);
    }

    private Vector2f setRotationManagerRotations(Vector2f rotations) {
        Vector2f fixedRotations = legitimizeRotations(rotations);
        RotationManager.INSTANCE.setSmoothed(false);
        RotationManager.INSTANCE.setRotations(fixedRotations, INSTANT_ROTATION_SPEED, Priority.Highest);
        return new Vector2f(RotationManager.INSTANCE.getRotation());
    }

    private Vector2f releaseControlledRotations() {
        if (!controllingRotations) {
            return null;
        }

        Vector2f playerRotations = null;
        boolean restoreRealRotations = mc.player != null && (!RotationManager.INSTANCE.isActive() || shouldReleaseRotationManager());
        if (restoreRealRotations) {
            playerRotations = currentPlayerRotations();
            RotationManager.INSTANCE.setSmoothed(false);
            RotationManager.INSTANCE.setRotations(playerRotations, INSTANT_ROTATION_SPEED, Priority.Highest);
            RotationManager.INSTANCE.setActive(false);
        }

        controllingRotations = false;
        lastControlledRotations = null;
        return playerRotations;
    }

    private boolean shouldReleaseRotationManager() {
        if (!RotationManager.INSTANCE.isActive()) {
            return false;
        }

        Vector2f managerRotations = RotationManager.INSTANCE.getRotation();
        return lastControlledRotations == null || closeRotations(managerRotations, lastControlledRotations);
    }

    private boolean closeRotations(Vector2f first, Vector2f second) {
        return first != null
                && second != null
                && Math.abs(Mth.wrapDegrees(first.x - second.x)) <= 0.01F
                && Math.abs(first.y - second.y) <= 0.01F;
    }

    private Vector2f makeUniqueOutgoingRotations(Vector2f rotations) {
        Vector2f uniqueRotations = legitimizeRotations(rotations);
        if (!sameOutgoingRotations(uniqueRotations, lastOutgoingRotations)) {
            return uniqueRotations;
        }

        Vector2f steppedRotations = RotationUtility.applySensitivityPatch(
                new Vector2f(uniqueRotations.x, uniqueRotations.y + pitchDedupStep(uniqueRotations)),
                lastOutgoingRotations
        );
        uniqueRotations = legitimizeRotations(steppedRotations);
        if (!sameOutgoingRotations(uniqueRotations, lastOutgoingRotations)) {
            return uniqueRotations;
        }

        steppedRotations = RotationUtility.applySensitivityPatch(
                new Vector2f(uniqueRotations.x + OUTGOING_ROTATION_DEDUP_YAW_STEP, uniqueRotations.y),
                lastOutgoingRotations
        );
        uniqueRotations = legitimizeRotations(steppedRotations);
        if (!sameOutgoingRotations(uniqueRotations, lastOutgoingRotations)) {
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

    private boolean sameOutgoingRotations(Vector2f first, Vector2f second) {
        return first != null
                && second != null
                && Math.abs(Mth.wrapDegrees(first.x - second.x)) <= OUTGOING_ROTATION_DUPLICATE_EPSILON
                && Math.abs(first.y - second.y) <= OUTGOING_ROTATION_DUPLICATE_EPSILON;
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
        return lastOutgoingRotations != null ? new Vector2f(lastOutgoingRotations) : currentPlayerRotations();
    }

    private Vector2f currentPlayerRotations() {
        if (mc.player == null) {
            return new Vector2f();
        }

        return new Vector2f(mc.player.getYRot(), mc.player.getXRot());
    }

    private void updateLastOutgoingRotations(ServerboundMovePlayerPacket packet) {
        Vector2f fallbackRotations = currentPlayerRotations();
        lastOutgoingRotations = new Vector2f(
                packet.getYRot(fallbackRotations.x),
                packet.getXRot(fallbackRotations.y)
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

    private boolean isActionRotationSynced(Vector2f rotations) {
        return rotations != null
                && lastOutgoingRotations != null
                && Math.abs(Mth.wrapDegrees(rotations.x - lastOutgoingRotations.x)) <= ACTION_ROTATION_SYNC_EPSILON
                && Math.abs(rotations.y - lastOutgoingRotations.y) <= ACTION_ROTATION_SYNC_EPSILON;
    }

    private void setOutgoingRotations(SendPositionEvent event, Vector2f rotations) {
        Vector2f outgoingRotations = movementFixRotations != null ? movementFixRotations : rotations;
        if (outgoingRotations == null) {
            return;
        }

        event.setYaw(outgoingRotations.x);
        event.setPitch(outgoingRotations.y);
    }

    private MovementInput correctedMovementInput(float forward, float strafe, float fromYaw, float toYaw) {
        HorizontalVector wanted = movementVector(forward, strafe, fromYaw);
        if (wanted.lengthSqr() <= 1.0E-8D) {
            return new MovementInput(forward, strafe);
        }

        MovementInput bestInput = new MovementInput(forward, strafe);
        double bestDot = -Double.MAX_VALUE;

        for (int candidateForward = -1; candidateForward <= 1; candidateForward++) {
            for (int candidateStrafe = -1; candidateStrafe <= 1; candidateStrafe++) {
                if (candidateForward == 0 && candidateStrafe == 0) {
                    continue;
                }

                HorizontalVector candidate = movementVector(candidateForward, candidateStrafe, toYaw);
                double dot = normalizedDot(wanted, candidate);
                if (dot > bestDot) {
                    bestDot = dot;
                    bestInput = new MovementInput(candidateForward, candidateStrafe);
                }
            }
        }

        return bestInput;
    }

    private HorizontalVector movementVector(float forward, float strafe, float yaw) {
        double inputMagnitude = strafe * strafe + forward * forward;
        if (inputMagnitude < 1.0E-4D) {
            return new HorizontalVector(0.0D, 0.0D);
        }

        inputMagnitude = Math.sqrt(inputMagnitude);
        if (inputMagnitude < 1.0D) {
            inputMagnitude = 1.0D;
        }

        double normalizedStrafe = strafe / inputMagnitude;
        double normalizedForward = forward / inputMagnitude;
        float yawRadians = yaw * Mth.DEG_TO_RAD;
        float sinYaw = Mth.sin(yawRadians);
        float cosYaw = Mth.cos(yawRadians);
        return new HorizontalVector(
                normalizedStrafe * cosYaw - normalizedForward * sinYaw,
                normalizedForward * cosYaw + normalizedStrafe * sinYaw
        );
    }

    private double normalizedDot(HorizontalVector first, HorizontalVector second) {
        double length = Math.sqrt(first.lengthSqr() * second.lengthSqr());
        if (length <= 1.0E-8D) {
            return -Double.MAX_VALUE;
        }

        return (first.x() * second.x() + first.z() * second.z()) / length;
    }

    private void refillClickProgress() {
        rightClickProgress = Math.min(rightClickProgress + rightCps.getValue(), TICKS_PER_SECOND);
        leftClickProgress = Math.min(leftClickProgress + leftCps.getValue(), TICKS_PER_SECOND);
    }

    private void resetState() {
        rightClickProgress = TICKS_PER_SECOND;
        leftClickProgress = TICKS_PER_SECOND;
        usedInteractionThisTick = false;
        waitForUseRelease = false;
        releaseControlledRotations();
        lastOutgoingRotations = currentPlayerRotations();
        lastControlledRotations = null;
        movementFixRotations = null;
        controllingRotations = false;
        clearPreparedAction();
    }

    private record ActionTarget(BlockTarget blockTarget, BlockPos basePos, Vector2f rotations) {
    }

    private record BlockTarget(BlockPos basePos, BlockHitResult hitResult) {
    }

    private record MovementInput(float forward, float strafe) {
    }

    private record HorizontalVector(double x, double z) {
        private double lengthSqr() {
            return x * x + z * z;
        }
    }
}
