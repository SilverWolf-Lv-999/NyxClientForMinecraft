package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.bus.EventHandler;
import io.github.seraphina.nyx.client.events.bus.EventPriority;
import io.github.seraphina.nyx.client.events.impl.JumpEvent;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.PostSendPositionEvent;
import io.github.seraphina.nyx.client.events.impl.SendPositionEvent;
import io.github.seraphina.nyx.client.events.impl.StrafeEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.other.Target;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(name = "nyxclient.module.anchoraura.name", description = "nyxclient.module.anchoraura.description", category = Category.COMBAT)
public class AnchorAura extends Module {
    public static final AnchorAura INSTANCE = new AnchorAura();

    private static final int TICKS_PER_SECOND = 20;
    private static final double ANCHOR_EXPLOSION_RADIUS = 5.0D;
    private static final double TARGET_SEARCH_EXTRA_RANGE = ANCHOR_EXPLOSION_RADIUS + 2.0D;
    private static final int PLACE_SCAN_RADIUS = 3;
    private static final int POST_USE_SLOT_DELAY_TICKS = 0;
    private static final float ACTION_TARGET_ROTATION_EPSILON = 3.0F;
    private static final float ACTION_ROTATION_SYNC_EPSILON = 0.01F;
    private static final float OUTGOING_ROTATION_DEDUP_YAW_STEP = 1.0F;
    private static final float OUTGOING_ROTATION_DEDUP_PITCH_STEP = 1.0F;
    private static final float OUTGOING_ROTATION_DUPLICATE_EPSILON = 1.0E-4F;
    private static final int MOVEMENT_FIX_SPRINT_LOCK_TICKS = 2;
    private static final Direction[] PLACE_DIRECTIONS = {
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST,
            Direction.DOWN
    };

    public final EnumValue<Select> target = ValueBuild.enumSetting("target", Select.SINGLE, this);

    public final IntValue switchTick = ValueBuild.intSetting(
            "switch tick",
            2, 1, 60, 1,
            () -> target.getValue() == Select.SWITCH,
            this
    );

    public final IntValue actionSpeed = ValueBuild.intSetting(
            "actionTimer",
            10, 1, 40, 1,
            this
    );

    public final DoubleValue placeRange = ValueBuild.doubleSetting(
            "place range",
            3.0, 0.1, 6.0, 0.1,
            this
    );

    public final BoolValue safePlace = ValueBuild.boolSetting(
            "safe place",
            true,
            this
    );

    private int actionProgress;
    private int switchProgress;
    private int lockedTargetId = -1;
    private int switchTargetIndex;
    private int originalHotbarSlot = InventoryUtility.NOT_FOUND;
    private int postUseSlotDelayTicks;
    private boolean usedInteractionThisTick;
    private Vector2f lastOutgoingRotations;
    private Vector2f lastControlledRotations;
    private Vector2f movementFixRotations;
    private QueuedAction movementFixAction;
    private QueuedAction queuedAction;
    private QueuedAction syncedAction;
    private boolean controllingRotations;
    private boolean releaseRotationsAfterPosition;
    private boolean postUseMovePacketSeen;
    private Vector2f postUseRotations;
    private int movementFixSprintLockTicks;
    private int placeRotationVariant;
    private BlockPos activeAnchorPos;
    private Stage stage = Stage.PLACE_ANCHOR;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        restoreOriginalHotbarSlot();
        resetState();
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        usedInteractionThisTick = false;
        queuedAction = null;

        if (!canRun()) {
            restoreOriginalHotbarSlot();
            resetState();
            return;
        }

        tickTimers();
        tickActionDelays();

        if (shouldPauseForItemUse()) {
            pauseForItemUse();
            return;
        }

        if (postUseSlotDelayTicks > 0) {
            if (!releaseRotationsAfterPosition) {
                releaseActionRotations();
            }
            return;
        }

        if (syncedAction != null) {
            runSyncedAction();
            return;
        }

        if (releaseRotationsAfterPosition) {
            if (!postUseMovePacketSeen) {
                holdActionRotations(postUseRotations);
                return;
            }

            releaseActionRotations();
        }

        if (!canAct()) {
            releaseActionRotations();
            restoreOriginalHotbarSlotIfIdle();
            return;
        }

        if (!hasRequiredHotbarItems()) {
            releaseActionRotations();
            restoreOriginalHotbarSlotIfIdle();
            return;
        }

        tickStage();

        if (queuedAction == null) {
            releaseActionRotations();
            restoreOriginalHotbarSlotIfIdle();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST - 1)
    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            if (packet.hasRotation()) {
                updateLastOutgoingRotations(packet);
            }

            if (isPostUseRotationConfirmed()) {
                postUseMovePacketSeen = true;
            }
        } else if (event.getPacket() instanceof ServerboundClientTickEndPacket && isPostUseRotationConfirmed()) {
            postUseMovePacketSeen = true;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST - 1)
    public void onSendPosition(SendPositionEvent event) {
        if (shouldPauseForItemUse()) {
            pauseForItemUse();
            return;
        }

        if (releaseRotationsAfterPosition) {
            setOutgoingRotations(event, postUseRotations);
            return;
        }

        if (syncedAction != null) {
            if (isRunnableAction(syncedAction)) {
                setOutgoingRotations(event, syncedAction.rotations());
            } else {
                releaseActionRotations();
            }
            return;
        }

        if (movementFixAction != null && movementFixRotations != null) {
            QueuedAction action = actionWithRotations(movementFixAction, movementFixRotations);
            if (isRunnableAction(action)) {
                setOutgoingRotations(event, movementFixRotations);
                syncedAction = action;
            } else {
                releaseActionRotations();
            }
            return;
        }

        QueuedAction action = queuedAction;
        if (action != null) {
            QueuedAction refreshedAction = refreshQueuedAction(action);
            if (refreshedAction == null) {
                releaseActionRotations();
                return;
            }

            setOutgoingRotations(event, refreshedAction.rotations());
            syncedAction = refreshedAction;
            return;
        }

        Vector2f rotations = movementFixRotations;
        if (rotations == null && releaseRotationsAfterPosition) {
            rotations = postUseRotations;
        }

        setOutgoingRotations(event, rotations);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPostSendPosition(PostSendPositionEvent event) {
        if (!releaseRotationsAfterPosition || !postUseMovePacketSeen) {
            return;
        }

        releaseActionRotations();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        movementFixRotations = null;
        movementFixAction = null;

        if (!shouldFixMovement(event)) {
            applyMovementFixSprintLock(event);
            return;
        }

        Vector2f rotations = movementRotations();
        if (rotations == null) {
            applyMovementFixSprintLock(event);
            return;
        }

        float playerYaw = mc.player.getYRot();
        MovementInput fixedInput = correctedMovementInput(
                event.getForward(),
                event.getStrafe(),
                playerYaw,
                rotations.x
        );

        event.setForward(fixedInput.forward());
        event.setStrafe(fixedInput.strafe());
        if (shouldLockSprintForMovementFix(fixedInput, playerYaw, rotations.x)) {
            movementFixSprintLockTicks = MOVEMENT_FIX_SPRINT_LOCK_TICKS;
        }
        applyMovementFixSprintLock(event);

        movementFixRotations = new Vector2f(rotations);
        movementFixAction = queuedAction == null ? null : actionWithRotations(queuedAction, rotations);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStrafe(StrafeEvent event) {
        if (movementFixRotations != null && shouldFixMovement()) {
            event.setYaw(movementFixRotations.x);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJump(JumpEvent event) {
        if (movementFixRotations != null && shouldFixMovement()) {
            event.setYaw(movementFixRotations.x);
        }
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator()
                && !Level.NETHER.equals(mc.level.dimension());
    }

    private void tickTimers() {
        actionProgress = Math.min(actionProgress + actionSpeed.getValue(), TICKS_PER_SECOND);

        if (target.getValue() == Select.SWITCH) {
            switchProgress++;
        } else {
            switchProgress = 0;
        }
    }

    private void tickActionDelays() {
        if (postUseSlotDelayTicks > 0) {
            postUseSlotDelayTicks--;
        }

        if (movementFixSprintLockTicks > 0) {
            movementFixSprintLockTicks--;
        }
    }

    private boolean canAct() {
        return actionProgress >= TICKS_PER_SECOND;
    }

    private void spendAction() {
        actionProgress = Math.max(0, actionProgress - TICKS_PER_SECOND);
    }

    private boolean hasRequiredHotbarItems() {
        return switch (stage) {
            case PLACE_ANCHOR -> Inventory.isHotbarSlot(findAnchorSlot()) && Inventory.isHotbarSlot(findGlowstoneSlot());
            case CHARGE_ANCHOR -> Inventory.isHotbarSlot(findGlowstoneSlot());
            case DETONATE_ANCHOR -> Inventory.isHotbarSlot(findDetonateSlot());
        };
    }

    private void tickStage() {
        if (activeAnchorPos != null && !isAnchorBlock(activeAnchorPos)) {
            activeAnchorPos = null;
            stage = Stage.PLACE_ANCHOR;
        }

        if (stage == Stage.CHARGE_ANCHOR && activeAnchorPos != null && anchorCharge(activeAnchorPos) > 0) {
            stage = Stage.DETONATE_ANCHOR;
        }

        if (stage == Stage.DETONATE_ANCHOR && activeAnchorPos != null && anchorCharge(activeAnchorPos) <= 0) {
            stage = Stage.CHARGE_ANCHOR;
        }

        switch (stage) {
            case PLACE_ANCHOR -> tickPlaceAnchor();
            case CHARGE_ANCHOR -> tickChargeAnchor();
            case DETONATE_ANCHOR -> tickDetonateAnchor();
        }
    }

    private void tickPlaceAnchor() {
        List<LivingEntity> targets = findTargets();
        if (targets.isEmpty()) {
            lockedTargetId = -1;
            return;
        }

        List<LivingEntity> activeTargets = selectTargets(targets);
        PlaceTarget placeTarget = findBestPlace(activeTargets);
        if (placeTarget != null) {
            placeAnchor(placeTarget.pos());
        }
    }

    private void tickChargeAnchor() {
        if (activeAnchorPos == null || anchorCharge(activeAnchorPos) > 0) {
            stage = Stage.DETONATE_ANCHOR;
            return;
        }

        chargeAnchor(activeAnchorPos);
    }

    private void tickDetonateAnchor() {
        if (activeAnchorPos == null) {
            stage = Stage.PLACE_ANCHOR;
            return;
        }

        if (anchorCharge(activeAnchorPos) <= 0) {
            stage = Stage.CHARGE_ANCHOR;
            return;
        }

        detonateAnchor(activeAnchorPos);
    }

    private List<LivingEntity> findTargets() {
        double searchRange = placeRange.getValue() + TARGET_SEARCH_EXTRA_RANGE;
        AABB searchBox = mc.player.getBoundingBox().inflate(searchRange);
        List<Entity> entities = mc.level.getEntities(
                mc.player,
                searchBox,
                entity -> entity instanceof LivingEntity livingEntity && isValidTarget(livingEntity)
        );

        return entities.stream()
                .map(LivingEntity.class::cast)
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player)))
                .toList();
    }

    private boolean isValidTarget(LivingEntity entity) {
        return entity != mc.player
                && entity.isAlive()
                && Target.isTarget(entity)
                && !entity.isSpectator()
                && entity.isPickable()
                && !entity.isInvulnerable()
                && (!(entity instanceof Player player) || !player.isCreative());
    }

    private List<LivingEntity> selectTargets(List<LivingEntity> targets) {
        if (target.getValue() == Select.MUTI) {
            return targets;
        }

        LivingEntity selected = target.getValue() == Select.SINGLE
                ? selectSingleTarget(targets)
                : selectSwitchTarget(targets);
        return selected == null ? List.of() : List.of(selected);
    }

    private LivingEntity selectSingleTarget(List<LivingEntity> targets) {
        LivingEntity locked = findById(targets, lockedTargetId);
        if (locked != null) {
            return locked;
        }

        LivingEntity selected = targets.getFirst();
        lockedTargetId = selected.getId();
        return selected;
    }

    private LivingEntity selectSwitchTarget(List<LivingEntity> targets) {
        if (switchProgress >= switchTick.getValue() || findById(targets, lockedTargetId) == null) {
            switchProgress = 0;
            switchTargetIndex = Math.floorMod(switchTargetIndex + 1, targets.size());
            lockedTargetId = targets.get(switchTargetIndex).getId();
        }

        LivingEntity selected = findById(targets, lockedTargetId);
        if (selected != null) {
            return selected;
        }

        switchTargetIndex = Math.min(switchTargetIndex, targets.size() - 1);
        selected = targets.get(switchTargetIndex);
        lockedTargetId = selected.getId();
        return selected;
    }

    private LivingEntity findById(List<LivingEntity> targets, int entityId) {
        if (entityId == -1) {
            return null;
        }

        for (LivingEntity target : targets) {
            if (target.getId() == entityId) {
                return target;
            }
        }

        return null;
    }

    private PlaceTarget findBestPlace(List<LivingEntity> targets) {
        PlaceTarget best = null;

        for (LivingEntity target : targets) {
            for (BlockPos pos : scanAnchorPositions(target)) {
                if (!canPlaceAnchorAt(pos)) {
                    continue;
                }

                Vec3 explosionPos = anchorExplosionPosition(pos);
                double targetDamage = estimateExplosionDamage(target, explosionPos);
                if (targetDamage <= 0.0D) {
                    continue;
                }

                double selfDamage = estimateExplosionDamage(mc.player, explosionPos);
                if (safePlace.getValue() && wouldKillSelf(selfDamage)) {
                    continue;
                }

                double score = scoreExplosion(target, explosionPos, targetDamage, selfDamage);
                if (score <= 0.0D) {
                    continue;
                }

                PlaceTarget candidate = new PlaceTarget(pos, target, score, targetDamage, selfDamage);
                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private List<BlockPos> scanAnchorPositions(LivingEntity target) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos center = target.blockPosition();
        int minY = center.getY() - 1;
        int maxY = center.getY() + 2;

        for (int y = minY; y <= maxY; y++) {
            for (int x = -PLACE_SCAN_RADIUS; x <= PLACE_SCAN_RADIUS; x++) {
                for (int z = -PLACE_SCAN_RADIUS; z <= PLACE_SCAN_RADIUS; z++) {
                    if (Math.abs(x) + Math.abs(z) > PLACE_SCAN_RADIUS + 1) {
                        continue;
                    }

                    positions.add(new BlockPos(center.getX() + x, y, center.getZ() + z));
                }
            }
        }

        positions.sort(Comparator.comparingDouble(pos -> anchorExplosionPosition(pos).distanceToSqr(target.position())));
        return positions;
    }

    private boolean canPlaceAnchorAt(BlockPos pos) {
        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > placeRange.getValue() * placeRange.getValue()) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        if (!state.canBeReplaced()) {
            return false;
        }

        AABB blockBox = new AABB(pos);
        return mc.level.noCollision(mc.player, blockBox)
                && mc.level.getEntities(null, blockBox).isEmpty()
                && findClickFace(pos) != null;
    }

    private double scoreExplosion(LivingEntity target, Vec3 explosionPos, double targetDamage, double selfDamage) {
        double distancePenalty = Math.sqrt(target.distanceToSqr(mc.player)) * 0.2D;
        double selfWeight = safePlace.getValue() ? 0.65D : 0.35D;
        double score = targetDamage - selfDamage * selfWeight - distancePenalty;

        if (targetDamage >= effectiveHealth(target)) {
            score += 8.0D;
        }

        return score;
    }

    private boolean wouldKillSelf(double damage) {
        return damage >= Math.max(1.0D, effectiveHealth(mc.player) - 1.0D);
    }

    private double effectiveHealth(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private double estimateExplosionDamage(LivingEntity entity, Vec3 explosionPos) {
        double distanceRatio = Math.sqrt(entity.distanceToSqr(explosionPos)) / (ANCHOR_EXPLOSION_RADIUS * 2.0D);
        if (distanceRatio > 1.0D) {
            return 0.0D;
        }

        double exposure = getExposure(explosionPos, entity.getBoundingBox());
        double impact = (1.0D - distanceRatio) * exposure;
        return ((impact * impact + impact) / 2.0D * 7.0D * ANCHOR_EXPLOSION_RADIUS * 2.0D) + 1.0D;
    }

    private double getExposure(Vec3 explosionPos, AABB box) {
        int visible = 0;
        int total = 0;

        for (double x = 0.0D; x <= 1.0D; x += 0.5D) {
            for (double y = 0.0D; y <= 1.0D; y += 0.5D) {
                for (double z = 0.0D; z <= 1.0D; z += 0.5D) {
                    Vec3 sample = new Vec3(
                            Mth.lerp(x, box.minX, box.maxX),
                            Mth.lerp(y, box.minY, box.maxY),
                            Mth.lerp(z, box.minZ, box.maxZ)
                    );
                    total++;

                    if (canTraceExplosion(explosionPos, sample)) {
                        visible++;
                    }
                }
            }
        }

        return total == 0 ? 0.0D : (double) visible / (double) total;
    }

    private boolean canTraceExplosion(Vec3 explosionPos, Vec3 sample) {
        HitResult result = mc.level.clip(new ClipContext(
                explosionPos,
                sample,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return result.getType() == HitResult.Type.MISS;
    }

    private boolean placeAnchor(BlockPos pos) {
        int anchorSlot = findAnchorSlot();
        if (!Inventory.isHotbarSlot(anchorSlot)
                || !Inventory.isHotbarSlot(findGlowstoneSlot())
                || !canPlaceAnchorAt(pos)) {
            return false;
        }

        ClickFace clickFace = findClickFace(pos);
        if (clickFace == null) {
            return false;
        }

        return queueUseBlock(QueuedActionKind.PLACE_ANCHOR, anchorSlot, clickFace.blockPos(), clickFace.direction(), pos);
    }

    private boolean chargeAnchor(BlockPos pos) {
        int glowstoneSlot = findGlowstoneSlot();
        if (!Inventory.isHotbarSlot(glowstoneSlot) || !isAnchorBlock(pos) || anchorCharge(pos) > 0) {
            return false;
        }

        ClickFace clickFace = findAnchorClickFace(pos);
        if (clickFace == null) {
            return false;
        }

        return queueUseBlock(QueuedActionKind.CHARGE_ANCHOR, glowstoneSlot, clickFace.blockPos(), clickFace.direction(), pos);
    }

    private boolean detonateAnchor(BlockPos pos) {
        int detonateSlot = findDetonateSlot();
        if (!Inventory.isHotbarSlot(detonateSlot) || !isAnchorBlock(pos) || anchorCharge(pos) <= 0) {
            return false;
        }

        ClickFace clickFace = findAnchorClickFace(pos);
        if (clickFace == null) {
            return false;
        }

        return queueUseBlock(QueuedActionKind.DETONATE_ANCHOR, detonateSlot, clickFace.blockPos(), clickFace.direction(), pos);
    }

    private boolean queueUseBlock(QueuedActionKind kind, int hotbarSlot, BlockPos blockPos, Direction direction, BlockPos anchorPos) {
        int previousSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(previousSlot)) {
            return false;
        }

        QueuedAction action = createUseBlockAction(kind, hotbarSlot, blockPos, direction, anchorPos);
        if (action == null) {
            return false;
        }

        queuedAction = action;
        return true;
    }

    private QueuedAction refreshQueuedAction(QueuedAction action) {
        if (action == null || !canStillRunAction(action)) {
            return null;
        }

        ClickFace clickFace = switch (action.kind()) {
            case PLACE_ANCHOR -> findClickFace(action.anchorPos());
            case CHARGE_ANCHOR, DETONATE_ANCHOR -> findAnchorClickFace(action.anchorPos());
        };
        if (clickFace == null) {
            return null;
        }

        return createUseBlockAction(
                action.kind(),
                action.hotbarSlot(),
                clickFace.blockPos(),
                clickFace.direction(),
                action.anchorPos()
        );
    }

    private QueuedAction createUseBlockAction(QueuedActionKind kind, int hotbarSlot, BlockPos blockPos, Direction direction, BlockPos anchorPos) {
        Vec3 hitVec = blockHitVec(blockPos, direction);
        Vector2f appliedRotations = preparePlaceRotations(RotationUtility.calculate(hitVec));
        return QueuedAction.useBlock(kind, hotbarSlot, blockPos, direction, hitVec, anchorPos, appliedRotations);
    }

    private ClickFace findClickFace(BlockPos targetPos) {
        for (Direction direction : PLACE_DIRECTIONS) {
            BlockPos neighbor = targetPos.relative(direction.getOpposite());
            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (neighborState.canBeReplaced()) {
                continue;
            }

            if (!neighborState.blocksMotion()) {
                continue;
            }

            if (!RotationUtility.isGrimDirection(neighbor, direction) || !RotationUtility.canSee(neighbor, direction)) {
                continue;
            }

            return new ClickFace(neighbor, direction);
        }

        return null;
    }

    private ClickFace findAnchorClickFace(BlockPos anchorPos) {
        for (Direction direction : PLACE_DIRECTIONS) {
            if (!RotationUtility.isGrimDirection(anchorPos, direction) || !RotationUtility.canSee(anchorPos, direction)) {
                continue;
            }

            return new ClickFace(anchorPos, direction);
        }

        return null;
    }

    private boolean isAnchorBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR);
    }

    private int anchorCharge(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.RESPAWN_ANCHOR) ? state.getValue(RespawnAnchorBlock.CHARGE) : 0;
    }

    private Vec3 anchorExplosionPosition(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    private Vec3 blockHitVec(BlockPos blockPos, Direction direction) {
        return Vec3.atCenterOf(blockPos).add(
                direction.getStepX() * 0.5D,
                direction.getStepY() * 0.5D,
                direction.getStepZ() * 0.5D
        );
    }

    private boolean shouldPauseForItemUse() {
        return mc.player != null && mc.player.isUsingItem();
    }

    private void pauseForItemUse() {
        releaseActionRotations();
    }

    private void runSyncedAction() {
        QueuedAction action = syncedAction;
        if (action == null) {
            return;
        }

        syncedAction = null;
        runQueuedAction(action);
    }

    private void runQueuedAction(QueuedAction action) {
        if (!canRun() || shouldPauseForItemUse() || !isRunnableAction(action)) {
            releaseActionRotations();
            return;
        }

        if (!isActionRotationSynced(action.rotations())) {
            releaseActionRotations();
            return;
        }

        if (runQueuedBlockUse(action)) {
            spendAction();
            completeAction(action);
        }

        if (usedInteractionThisTick) {
            releaseRotationsAfterPosition = true;
            postUseMovePacketSeen = false;
            postUseRotations = new Vector2f(action.rotations());
            holdActionRotations(postUseRotations);
            return;
        }

        releaseActionRotations();
    }

    private boolean canStillRunAction(QueuedAction action) {
        return switch (action.kind()) {
            case PLACE_ANCHOR -> canPlaceAnchorAt(action.anchorPos())
                    && InventoryUtility.getStack(action.hotbarSlot()).is(Items.RESPAWN_ANCHOR)
                    && Inventory.isHotbarSlot(findGlowstoneSlot());
            case CHARGE_ANCHOR -> isAnchorBlock(action.anchorPos())
                    && anchorCharge(action.anchorPos()) <= 0
                    && InventoryUtility.getStack(action.hotbarSlot()).is(Items.GLOWSTONE);
            case DETONATE_ANCHOR -> isAnchorBlock(action.anchorPos())
                    && anchorCharge(action.anchorPos()) > 0
                    && isDetonateSlot(action.hotbarSlot());
        };
    }

    private boolean isRunnableAction(QueuedAction action) {
        return action != null
                && canStillRunAction(action)
                && isActionFaceValid(action);
    }

    private boolean isActionFaceValid(QueuedAction action) {
        if (action == null || mc.level == null) {
            return false;
        }

        if (action.kind() == QueuedActionKind.PLACE_ANCHOR) {
            BlockState clickedState = mc.level.getBlockState(action.blockPos());
            if (clickedState.canBeReplaced() || !clickedState.blocksMotion()) {
                return false;
            }
        } else if (!isAnchorBlock(action.blockPos())) {
            return false;
        }

        return RotationUtility.isGrimDirection(action.blockPos(), action.direction())
                && RotationUtility.canSee(action.blockPos(), action.direction());
    }

    private boolean runQueuedBlockUse(QueuedAction action) {
        if (!Inventory.isHotbarSlot(action.hotbarSlot())) {
            return false;
        }

        int previousSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(previousSlot)) {
            return false;
        }

        boolean changedSlot = previousSlot != action.hotbarSlot();
        if (changedSlot && !selectWorkHotbarSlot(action.hotbarSlot(), previousSlot)) {
            return false;
        }

        BlockHitResult hitResult = new BlockHitResult(action.hitVec(), action.direction(), action.blockPos(), false);
        InteractionResult result;
        try {
            result = useItemOnWithRotations(action.rotations(), hitResult);
        } finally {
            beginPostUseDelay();
        }

        if (result.consumesAction()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
            mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
            placeRotationVariant++;
            return true;
        }

        return false;
    }

    private void completeAction(QueuedAction action) {
        switch (action.kind()) {
            case PLACE_ANCHOR -> {
                activeAnchorPos = action.anchorPos();
                stage = Stage.CHARGE_ANCHOR;
            }
            case CHARGE_ANCHOR -> stage = Stage.DETONATE_ANCHOR;
            case DETONATE_ANCHOR -> {
                activeAnchorPos = null;
                stage = Stage.PLACE_ANCHOR;
            }
        }
    }

    private Vector2f preparePlaceRotations(Vector2f rotations) {
        Vector2f baseRotations = legitimizeRotations(rotations);
        Vector2f placeRotations = variedPlaceRotations(baseRotations);
        Vector2f uniqueRotations = makeUniqueOutgoingRotations(placeRotations);
        if (!sameOutgoingRotations(uniqueRotations, lastOutgoingRotations)
                && closeActionRotations(uniqueRotations, baseRotations)) {
            return prepareActionRotations(uniqueRotations);
        }

        return prepareActionRotations(placeRotations);
    }

    private Vector2f prepareActionRotations(Vector2f rotations) {
        Vector2f appliedRotations = legitimizeRotations(rotations);
        return holdActionRotations(appliedRotations);
    }

    private Vector2f holdActionRotations(Vector2f rotations) {
        if (rotations == null) {
            return null;
        }

        Vector2f appliedRotations = legitimizeRotations(rotations);
        RotationManager.INSTANCE.setSmoothed(false);
        RotationManager.INSTANCE.setRotations(appliedRotations, 180.0D, Priority.Highest);
        appliedRotations = new Vector2f(RotationManager.INSTANCE.getRotation());
        lastControlledRotations = new Vector2f(appliedRotations);
        controllingRotations = true;
        return appliedRotations;
    }

    private Vector2f variedPlaceRotations(Vector2f rotations) {
        Vector2f baseRotations = legitimizeRotations(rotations);
        int variant = Math.floorMod(placeRotationVariant, 4);
        float yawOffset = switch (variant) {
            case 1 -> 0.35F;
            case 2 -> -0.35F;
            case 3 -> 0.2F;
            default -> -0.2F;
        };
        float pitchOffset = switch (variant) {
            case 1 -> -0.25F;
            case 2 -> 0.3F;
            case 3 -> 0.2F;
            default -> -0.3F;
        };

        Vector2f variedRotations = legitimizeRotations(RotationUtility.applySensitivityPatch(
                new Vector2f(baseRotations.x + yawOffset, baseRotations.y + pitchOffset),
                currentSyncedRotations()
        ));
        return closeActionRotations(variedRotations, baseRotations) ? variedRotations : baseRotations;
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

    private void setOutgoingRotations(SendPositionEvent event, Vector2f rotations) {
        if (rotations == null) {
            return;
        }

        event.setYaw(rotations.x);
        event.setPitch(rotations.y);
    }

    private void releaseActionRotations() {
        if (controllingRotations && shouldReleaseRotationManager()) {
            Vector2f playerRotations = currentPlayerRotations();
            RotationManager.INSTANCE.setSmoothed(false);
            RotationManager.INSTANCE.setRotations(playerRotations, 180.0D, Priority.Highest);
            RotationManager.INSTANCE.setActive(false);
        }

        controllingRotations = false;
        lastControlledRotations = null;
        movementFixRotations = null;
        movementFixAction = null;
        queuedAction = null;
        syncedAction = null;
        releaseRotationsAfterPosition = false;
        postUseMovePacketSeen = false;
        postUseRotations = null;
    }

    private QueuedAction actionWithRotations(QueuedAction action, Vector2f rotations) {
        if (action == null) {
            return null;
        }

        return QueuedAction.useBlock(
                action.kind(),
                action.hotbarSlot(),
                action.blockPos(),
                action.direction(),
                action.hitVec(),
                action.anchorPos(),
                rotations
        );
    }

    private boolean shouldReleaseRotationManager() {
        if (!RotationManager.INSTANCE.isActive()) {
            return false;
        }

        return lastControlledRotations == null
                || closeRotations(RotationManager.INSTANCE.getRotation(), lastControlledRotations);
    }

    private boolean closeRotations(Vector2f first, Vector2f second) {
        return first != null
                && second != null
                && Math.abs(Mth.wrapDegrees(first.x - second.x)) <= 0.01F
                && Math.abs(first.y - second.y) <= 0.01F;
    }

    private boolean closeActionRotations(Vector2f actual, Vector2f expected) {
        return actual != null
                && expected != null
                && Math.abs(Mth.wrapDegrees(actual.x - expected.x)) <= ACTION_TARGET_ROTATION_EPSILON
                && Math.abs(actual.y - expected.y) <= ACTION_TARGET_ROTATION_EPSILON;
    }

    private boolean isActionRotationSynced(Vector2f rotations) {
        return rotations != null
                && lastOutgoingRotations != null
                && Math.abs(Mth.wrapDegrees(rotations.x - lastOutgoingRotations.x)) <= ACTION_ROTATION_SYNC_EPSILON
                && Math.abs(rotations.y - lastOutgoingRotations.y) <= ACTION_ROTATION_SYNC_EPSILON;
    }

    private boolean isPostUseRotationConfirmed() {
        return releaseRotationsAfterPosition
                && postUseRotations != null
                && isActionRotationSynced(postUseRotations);
    }

    private boolean shouldFixMovement(MoveInputEvent event) {
        return shouldFixMovement()
                && (event.getForward() != 0.0F || event.getStrafe() != 0.0F);
    }

    private boolean shouldFixMovement() {
        return canRun()
                && controllingRotations
                && movementRotations() != null;
    }

    private Vector2f movementRotations() {
        if (releaseRotationsAfterPosition && postUseRotations != null) {
            return new Vector2f(postUseRotations);
        }

        if (syncedAction != null && syncedAction.rotations() != null) {
            return new Vector2f(syncedAction.rotations());
        }

        if (queuedAction != null && queuedAction.rotations() != null) {
            return new Vector2f(queuedAction.rotations());
        }

        if (!controllingRotations) {
            return null;
        }

        if (lastControlledRotations != null) {
            return new Vector2f(lastControlledRotations);
        }

        return RotationManager.INSTANCE.isActive() ? new Vector2f(RotationManager.INSTANCE.getRotation()) : null;
    }

    private void stopSprintingForMovementFix() {
        if (mc.player != null && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }

    private boolean shouldLockSprintForMovementFix(MovementInput fixedInput, float playerYaw, float movementYaw) {
        return fixedInput.forward() <= 0.0F
                || fixedInput.strafe() != 0.0F
                || Math.abs(Mth.wrapDegrees(movementYaw - playerYaw)) > 35.0F;
    }

    private void applyMovementFixSprintLock(MoveInputEvent event) {
        if (movementFixSprintLockTicks <= 0) {
            return;
        }

        event.setSprint(false);
        stopSprintingForMovementFix();
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

    private InteractionResult useItemOnWithRotations(Vector2f rotations, BlockHitResult hitResult) {
        if (mc.player == null || mc.gameMode == null) {
            return InteractionResult.FAIL;
        }

        if (rotations == null) {
            return mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        }

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        mc.player.setYRot(rotations.x);
        mc.player.setXRot(rotations.y);

        try {
            return mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        } finally {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    private void beginPostUseDelay() {
        usedInteractionThisTick = true;
        postUseSlotDelayTicks = POST_USE_SLOT_DELAY_TICKS;
    }

    private boolean selectWorkHotbarSlot(int hotbarSlot, int currentSlot) {
        if (!Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        if (!Inventory.isHotbarSlot(originalHotbarSlot) && Inventory.isHotbarSlot(currentSlot)) {
            originalHotbarSlot = currentSlot;
        }

        return InventoryUtility.selectHotbarSlot(hotbarSlot);
    }

    private void restoreOriginalHotbarSlot() {
        if (!Inventory.isHotbarSlot(originalHotbarSlot)) {
            originalHotbarSlot = InventoryUtility.NOT_FOUND;
            return;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != originalHotbarSlot) {
            InventoryUtility.selectHotbarSlot(originalHotbarSlot);
        }

        originalHotbarSlot = InventoryUtility.NOT_FOUND;
    }

    private void restoreOriginalHotbarSlotIfIdle() {
        if (usedInteractionThisTick
                || queuedAction != null
                || postUseSlotDelayTicks > 0) {
            return;
        }

        restoreOriginalHotbarSlot();
    }

    private int findAnchorSlot() {
        return InventoryUtility.findHotbarSlot(Items.RESPAWN_ANCHOR);
    }

    private int findGlowstoneSlot() {
        return InventoryUtility.findHotbarSlot(Items.GLOWSTONE);
    }

    private int findDetonateSlot() {
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (isDetonateSlot(selectedSlot)) {
            return selectedSlot;
        }

        int anchorSlot = findAnchorSlot();
        if (isDetonateSlot(anchorSlot)) {
            return anchorSlot;
        }

        int emptySlot = InventoryUtility.findEmptyHotbarSlot();
        if (Inventory.isHotbarSlot(emptySlot)) {
            return emptySlot;
        }

        for (int slot = InventoryUtility.HOTBAR_START; slot < InventoryUtility.HOTBAR_END; slot++) {
            if (isDetonateSlot(slot)) {
                return slot;
            }
        }

        return InventoryUtility.NOT_FOUND;
    }

    private boolean isDetonateSlot(int hotbarSlot) {
        return Inventory.isHotbarSlot(hotbarSlot)
                && !InventoryUtility.getStack(hotbarSlot).is(Items.GLOWSTONE);
    }

    private void resetState() {
        releaseActionRotations();
        actionProgress = TICKS_PER_SECOND;
        switchProgress = 0;
        lockedTargetId = -1;
        switchTargetIndex = -1;
        originalHotbarSlot = InventoryUtility.NOT_FOUND;
        postUseSlotDelayTicks = 0;
        usedInteractionThisTick = false;
        queuedAction = null;
        syncedAction = null;
        lastOutgoingRotations = currentPlayerRotations();
        movementFixRotations = null;
        movementFixAction = null;
        releaseRotationsAfterPosition = false;
        postUseMovePacketSeen = false;
        postUseRotations = null;
        movementFixSprintLockTicks = 0;
        placeRotationVariant = 0;
        activeAnchorPos = null;
        stage = Stage.PLACE_ANCHOR;
    }

    private record PlaceTarget(BlockPos pos, LivingEntity target, double score, double targetDamage, double selfDamage) {
    }

    private record ClickFace(BlockPos blockPos, Direction direction) {
    }

    private record QueuedAction(
            QueuedActionKind kind,
            int hotbarSlot,
            BlockPos blockPos,
            Direction direction,
            Vec3 hitVec,
            BlockPos anchorPos,
            Vector2f rotations
    ) {
        private static QueuedAction useBlock(
                QueuedActionKind kind,
                int hotbarSlot,
                BlockPos blockPos,
                Direction direction,
                Vec3 hitVec,
                BlockPos anchorPos,
                Vector2f rotations
        ) {
            return new QueuedAction(
                    kind,
                    hotbarSlot,
                    blockPos,
                    direction,
                    hitVec,
                    anchorPos,
                    rotations == null ? null : new Vector2f(rotations)
            );
        }
    }

    private enum QueuedActionKind {
        PLACE_ANCHOR,
        CHARGE_ANCHOR,
        DETONATE_ANCHOR
    }

    private enum Stage {
        PLACE_ANCHOR,
        CHARGE_ANCHOR,
        DETONATE_ANCHOR
    }

    private record MovementInput(float forward, float strafe) {
    }

    private record HorizontalVector(double x, double z) {
        private double lengthSqr() {
            return x * x + z * z;
        }
    }

    public enum Select {
        SINGLE,
        SWITCH,
        MUTI
    }
}
