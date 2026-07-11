package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.bus.EventHandler;
import io.github.seraphina.nyx.client.events.bus.EventPriority;
import io.github.seraphina.nyx.client.events.impl.JumpEvent;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
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
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(name = "nyxclient.module.crystalaura.name", description = "nyxclient.module.crystalaura.description", category = Category.COMBAT)
public class CrystalAura extends Module {
    public static final CrystalAura INSTANCE = new CrystalAura();

    private static final int TICKS_PER_SECOND = 20;
    private static final double CRYSTAL_EXPLOSION_RADIUS = 6.0D;
    private static final double TARGET_SEARCH_EXTRA_RANGE = CRYSTAL_EXPLOSION_RADIUS + 2.0D;
    private static final int PLACE_SCAN_RADIUS = 3;
    private static final int POST_USE_SLOT_DELAY_TICKS = 3;
    private static final int POST_ATTACK_PLACE_DELAY_TICKS = 2;
    private static final float ACTION_TARGET_ROTATION_EPSILON = 3.0F;
    private static final float OUTGOING_ROTATION_DEDUP_YAW_STEP = 1.0F;
    private static final float OUTGOING_ROTATION_DEDUP_PITCH_STEP = 1.0F;
    private static final float OUTGOING_ROTATION_DUPLICATE_EPSILON = 1.0E-4F;
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
            ()-> target.getValue() == Select.SWITCH,
            this
    );

    public final IntValue placeSpeed = ValueBuild.intSetting("placeTimer",
            10, 1, 40, 1,
            this
    );

    public final IntValue breakSpeed = ValueBuild.intSetting("breakTimer",
            10, 1, 40, 1,
            this
    );

    public final DoubleValue placeRange = ValueBuild.doubleSetting(
            "place range",
            3.0, 0.1, 6.0, 0.1,
            this
    );

    public final DoubleValue breakRange = ValueBuild.doubleSetting(
            "break range",
            3.0, 0.1, 6.0, 0.1,
            this
    );

    public final BoolValue oneHit = ValueBuild.boolSetting(
            "one hit",
            true,
            this
    );

    public final BoolValue safePlace = ValueBuild.boolSetting(
            "safe place",
            true,
            this
    );

    public final BoolValue autoPlaceBlock = ValueBuild.boolSetting(
            "auto place block",
            true,
            this
    );

    public final BoolValue autoPlaceProtectBlock = ValueBuild.boolSetting(
            "auto place protect block",
            true,
            this
    );

    private int placeProgress;
    private int breakProgress;
    private int switchProgress;
    private int lockedTargetId = -1;
    private int switchTargetIndex;
    private int originalHotbarSlot = InventoryUtility.NOT_FOUND;
    private int postUseSlotDelayTicks;
    private int postAttackPlaceDelayTicks;
    private boolean usedInteractionThisTick;
    private Vector2f lastOutgoingRotations;
    private Vector2f lastControlledRotations;
    private Vector2f movementFixRotations;
    private QueuedAction queuedAction;
    private QueuedAction syncedAction;
    private boolean controllingRotations;
    private int placeRotationVariant;
    private BlockPos pendingSupportBasePos;

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

        runSyncedAction();

        List<LivingEntity> targets = findTargets();
        if (targets.isEmpty()) {
            lockedTargetId = -1;
            pendingSupportBasePos = null;
            restoreOriginalHotbarSlot();
            releaseActionRotations();
            return;
        }

        List<LivingEntity> activeTargets = selectTargets(targets);

        if (canBreak()) {
            CrystalTarget crystalTarget = findBestCrystal(activeTargets);
            if (crystalTarget != null && breakCrystal(crystalTarget.crystal())) {
                return;
            }
        }

        if (!usedInteractionThisTick && canPlace() && postAttackPlaceDelayTicks == 0) {
            PlaceTarget placeTarget = findPendingSupportPlace(activeTargets);
            if (placeTarget == null) {
                placeTarget = findBestPlace(activeTargets);
            }

            SupportTarget supportTarget = autoPlaceBlock.getValue() && placeTarget == null && pendingSupportBasePos == null
                    ? findBestSupportBlock(activeTargets)
                    : null;
            if (placeTarget != null) {
                if (safePlace.getValue() && !placeTarget.covered()) {
                    if (autoPlaceProtectBlock.getValue() && placeProtectBlock(crystalPosition(placeTarget.basePos()))) {
                        return;
                    } else {
                        restoreOriginalHotbarSlotIfIdle();
                    }
                    return;
                }

                if (placeCrystal(placeTarget.basePos())) {
                    return;
                } else {
                    restoreOriginalHotbarSlotIfIdle();
                }
                return;
            } else if (supportTarget != null && placeSupportBlock(supportTarget.pos())) {
                return;
            } else {
                restoreOriginalHotbarSlotIfIdle();
                return;
            }
        }

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
        }
    }

    @EventHandler(priority = EventPriority.LOWEST - 1)
    public void onSendPosition(SendPositionEvent event) {
        if (shouldPauseForItemUse()) {
            pauseForItemUse();
            return;
        }

        QueuedAction action = queuedAction;
        if (action != null) {
            setOutgoingRotations(event, action.rotations());
            syncedAction = action;
            return;
        }

        if (movementFixRotations != null) {
            setOutgoingRotations(event, movementFixRotations);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        movementFixRotations = null;

        if (!shouldFixMovement(event)) {
            return;
        }

        Vector2f rotations = movementRotations();
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
            stopSprintingForMovementFix();
        }

        movementFixRotations = new Vector2f(rotations);
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
                && !mc.player.isSpectator();
    }

    private void tickTimers() {
        placeProgress = Math.min(placeProgress + placeSpeed.getValue(), TICKS_PER_SECOND);
        breakProgress = Math.min(breakProgress + breakSpeed.getValue(), TICKS_PER_SECOND);

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

        if (postAttackPlaceDelayTicks > 0) {
            postAttackPlaceDelayTicks--;
        }
    }

    private boolean canPlace() {
        return placeProgress >= TICKS_PER_SECOND;
    }

    private boolean canBreak() {
        return breakProgress >= TICKS_PER_SECOND;
    }

    private void spendPlace() {
        placeProgress = Math.max(0, placeProgress - TICKS_PER_SECOND);
    }

    private void spendBreak() {
        breakProgress = Math.max(0, breakProgress - TICKS_PER_SECOND);
    }

    private List<LivingEntity> findTargets() {
        double searchRange = Math.max(placeRange.getValue(), breakRange.getValue()) + TARGET_SEARCH_EXTRA_RANGE;
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

    private CrystalTarget findBestCrystal(List<LivingEntity> targets) {
        double range = breakRange.getValue();
        AABB searchBox = mc.player.getBoundingBox().inflate(range);
        List<Entity> crystals = mc.level.getEntities(
                mc.player,
                searchBox,
                entity -> entity instanceof EndCrystal crystal && isBreakableCrystal(crystal, range)
        );

        CrystalTarget best = null;
        for (Entity entity : crystals) {
            EndCrystal crystal = (EndCrystal) entity;
            Vec3 explosionPos = crystal.position();

            LivingEntity damagedTarget = bestDamagedTarget(targets, explosionPos);
            if (damagedTarget == null) {
                continue;
            }

            double score = scoreExplosion(damagedTarget, explosionPos);
            if (score <= 0.0D) {
                continue;
            }

            CrystalTarget candidate = new CrystalTarget(crystal, damagedTarget, score);
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }

        return best;
    }

    private boolean isBreakableCrystal(EndCrystal crystal, double range) {
        return crystal.isAlive()
                && !crystal.isRemoved()
                && crystal.distanceToSqr(mc.player) <= range * range
                && (!safePlace.getValue() || hasCoverFrom(crystal.position()));
    }

    private LivingEntity bestDamagedTarget(List<LivingEntity> targets, Vec3 explosionPos) {
        LivingEntity bestTarget = null;
        double bestScore = 0.0D;

        for (LivingEntity target : targets) {
            double score = scoreExplosion(target, explosionPos);
            if (score > bestScore) {
                bestScore = score;
                bestTarget = target;
            }
        }

        return bestTarget;
    }

    private PlaceTarget findBestPlace(List<LivingEntity> targets) {
        PlaceTarget best = null;

        for (LivingEntity target : targets) {
            for (BlockPos basePos : scanBasePositions(target)) {
                if (!canPlaceCrystalAt(basePos)) {
                    continue;
                }

                PlaceTarget candidate = createPlaceTarget(target, basePos);
                if (candidate == null) {
                    continue;
                }

                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private PlaceTarget findPendingSupportPlace(List<LivingEntity> targets) {
        if (pendingSupportBasePos == null) {
            return null;
        }

        BlockPos basePos = pendingSupportBasePos;
        if (!canPlaceCrystalAt(basePos)) {
            if (!isCrystalBase(basePos)) {
                pendingSupportBasePos = null;
            }
            return null;
        }

        PlaceTarget best = null;
        for (LivingEntity target : targets) {
            PlaceTarget candidate = createPlaceTarget(target, basePos);
            if (candidate != null && (best == null || candidate.score() > best.score())) {
                best = candidate;
            }
        }

        if (best == null) {
            pendingSupportBasePos = null;
        }
        return best;
    }

    private PlaceTarget createPlaceTarget(LivingEntity target, BlockPos basePos) {
        Vec3 explosionPos = crystalPosition(basePos);
        boolean covered = hasCoverFrom(explosionPos);
        if (safePlace.getValue() && !covered && !autoPlaceProtectBlock.getValue()) {
            return null;
        }

        double targetScore = scoreExplosion(target, explosionPos);
        if (targetScore <= 0.0D) {
            return null;
        }

        double targetDamage = estimateExplosionDamage(target, explosionPos);
        double selfScore = estimateExplosionDamage(mc.player, explosionPos);
        double score = targetScore - selfScore * (safePlace.getValue() ? 0.25D : 0.55D);
        return score <= 0.0D ? null : new PlaceTarget(basePos, target, score, targetDamage, covered);
    }

    private List<BlockPos> scanBasePositions(LivingEntity target) {
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

        positions.sort(Comparator.comparingDouble(pos -> crystalPosition(pos).distanceToSqr(target.position())));
        return positions;
    }

    private boolean canPlaceCrystalAt(BlockPos basePos) {
        if (!isCrystalBase(basePos)) {
            return false;
        }

        return canUseCrystalBase(basePos);
    }

    private boolean canUseCrystalBase(BlockPos basePos) {
        if (!RotationUtility.isGrimDirection(basePos, Direction.UP) || !RotationUtility.canSee(basePos, Direction.UP)) {
            return false;
        }

        Vec3 crystalPos = crystalPosition(basePos);
        if (mc.player.getEyePosition().distanceToSqr(crystalPos) > placeRange.getValue() * placeRange.getValue()) {
            return false;
        }

        BlockPos crystalBlockPos = basePos.above();
        if (!mc.level.isEmptyBlock(crystalBlockPos)) {
            return false;
        }

        return mc.level.getEntities(null, crystalSpawnBox(basePos)).isEmpty();
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

    private boolean isCrystalBase(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    private double scoreExplosion(LivingEntity target, Vec3 explosionPos) {
        double damage = estimateExplosionDamage(target, explosionPos);
        if (damage <= 0.0D) {
            return 0.0D;
        }

        double distancePenalty = Math.sqrt(target.distanceToSqr(mc.player)) * 0.2D;
        return damage - distancePenalty;
    }

    private double estimateExplosionDamage(LivingEntity entity, Vec3 explosionPos) {
        double distanceRatio = Math.sqrt(entity.distanceToSqr(explosionPos)) / (CRYSTAL_EXPLOSION_RADIUS * 2.0D);
        if (distanceRatio > 1.0D) {
            return 0.0D;
        }

        double exposure = getExposure(explosionPos, entity.getBoundingBox());
        double impact = (1.0D - distanceRatio) * exposure;
        return ((impact * impact + impact) / 2.0D * 7.0D * CRYSTAL_EXPLOSION_RADIUS * 2.0D) + 1.0D;
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

    private boolean hasCoverFrom(Vec3 explosionPos) {
        Vec3 eyePos = mc.player.getEyePosition();
        HitResult result = mc.level.clip(new ClipContext(
                eyePos,
                explosionPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return result.getType() != HitResult.Type.MISS;
    }

    private boolean breakCrystal(EndCrystal crystal) {
        Vector2f appliedRotations = prepareActionRotations(RotationUtility.calculate(crystal.position()));
        queuedAction = QueuedAction.breakCrystal(crystal, appliedRotations);
        return true;
    }

    private boolean placeCrystal(BlockPos basePos) {
        int crystalSlot = findCrystalSlot();
        if (!Inventory.isHotbarSlot(crystalSlot)) {
            return false;
        }

        return useHotbarOnBlock(crystalSlot, basePos, Direction.UP);
    }

    private boolean placeSupportBlock(BlockPos pos) {
        int blockSlot = findSupportBlockSlot();
        if (!Inventory.isHotbarSlot(blockSlot) || !canPlaceBlockAt(pos)) {
            return false;
        }

        ClickFace clickFace = findClickFace(pos);
        if (clickFace == null) {
            return false;
        }

        if (!useHotbarOnBlock(blockSlot, clickFace.blockPos(), clickFace.direction())) {
            return false;
        }

        pendingSupportBasePos = pos;
        return true;
    }

    private boolean placeProtectBlock(Vec3 explosionPos) {
        int blockSlot = findSupportBlockSlot();
        if (!Inventory.isHotbarSlot(blockSlot) || !Inventory.isHotbarSlot(findCrystalSlot())) {
            return false;
        }

        for (BlockPos pos : protectBlockPositions(explosionPos)) {
            if (!wouldCoverExplosion(pos, explosionPos) || !canPlaceBlockAt(pos)) {
                continue;
            }

            ClickFace clickFace = findClickFace(pos);
            if (clickFace != null) {
                return useHotbarOnBlock(blockSlot, clickFace.blockPos(), clickFace.direction());
            }
        }

        return false;
    }

    private List<BlockPos> protectBlockPositions(Vec3 explosionPos) {
        List<BlockPos> positions = new ArrayList<>();
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 delta = explosionPos.subtract(eyePos);
        double distance = delta.length();
        if (distance <= 1.0E-6D) {
            return positions;
        }

        int steps = Mth.clamp((int) Math.ceil(distance * 4.0D), 2, 32);
        for (int step = 1; step < steps; step++) {
            Vec3 sample = eyePos.add(delta.scale((double) step / steps));
            BlockPos samplePos = BlockPos.containing(sample);
            addProtectBlockPosition(positions, samplePos);
            addProtectBlockPosition(positions, samplePos.below());
        }

        positions.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eyePos)));
        return positions;
    }

    private void addProtectBlockPosition(List<BlockPos> positions, BlockPos pos) {
        if (!positions.contains(pos)) {
            positions.add(pos);
        }
    }

    private boolean wouldCoverExplosion(BlockPos pos, Vec3 explosionPos) {
        return intersectsSegment(new AABB(pos).inflate(1.0E-4D), mc.player.getEyePosition(), explosionPos);
    }

    private boolean intersectsSegment(AABB box, Vec3 start, Vec3 end) {
        SegmentRange range = new SegmentRange(0.0D, 1.0D);
        range = clipSegmentAxis(start.x, end.x - start.x, box.minX, box.maxX, range);
        if (range == null) {
            return false;
        }

        range = clipSegmentAxis(start.y, end.y - start.y, box.minY, box.maxY, range);
        if (range == null) {
            return false;
        }

        return clipSegmentAxis(start.z, end.z - start.z, box.minZ, box.maxZ, range) != null;
    }

    private SegmentRange clipSegmentAxis(double start, double delta, double min, double max, SegmentRange range) {
        if (Math.abs(delta) <= 1.0E-8D) {
            return start >= min && start <= max ? range : null;
        }

        double enter = (min - start) / delta;
        double exit = (max - start) / delta;
        if (enter > exit) {
            double swap = enter;
            enter = exit;
            exit = swap;
        }

        double clippedMin = Math.max(range.min(), enter);
        double clippedMax = Math.min(range.max(), exit);
        return clippedMin <= clippedMax ? new SegmentRange(clippedMin, clippedMax) : null;
    }

    private SupportTarget findBestSupportBlock(List<LivingEntity> targets) {
        if (!canPlanSupportBlock()) {
            return null;
        }

        SupportTarget best = null;

        for (LivingEntity target : targets) {
            for (BlockPos basePos : scanBasePositions(target)) {
                if (!canPlaceSupportBlockAt(basePos)) {
                    continue;
                }

                Vec3 explosionPos = crystalPosition(basePos);
                if (safePlace.getValue() && !hasCoverFrom(explosionPos) && !autoPlaceProtectBlock.getValue()) {
                    continue;
                }

                double score = scoreExplosion(target, explosionPos);
                if (score <= 0.0D) {
                    continue;
                }

                double targetDamage = estimateExplosionDamage(target, explosionPos);
                double selfScore = estimateExplosionDamage(mc.player, explosionPos);
                double weightedScore = score - selfScore * (safePlace.getValue() ? 0.25D : 0.55D);
                if (weightedScore <= 0.0D) {
                    continue;
                }

                SupportTarget candidate = new SupportTarget(basePos, weightedScore, targetDamage);
                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private boolean canPlanSupportBlock() {
        return Inventory.isHotbarSlot(findCrystalSlot())
                && Inventory.isHotbarSlot(findSupportBlockSlot());
    }

    private boolean canPlaceSupportBlockAt(BlockPos basePos) {
        return canPlaceBlockAt(basePos)
                && canUseCrystalBase(basePos)
                && findClickFace(basePos) != null;
    }

    private boolean canPlaceBlockAt(BlockPos pos) {
        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > placeRange.getValue() * placeRange.getValue()) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        if (!state.canBeReplaced()) {
            return false;
        }

        AABB blockBox = new AABB(pos);
        return mc.level.noCollision(mc.player, blockBox)
                && mc.level.getEntities(null, blockBox).isEmpty();
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

    private boolean useHotbarOnBlock(int hotbarSlot, BlockPos blockPos, Direction direction) {
        int previousSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(previousSlot)) {
            return false;
        }

        Vec3 hitVec = blockHitVec(blockPos, direction);
        Vector2f appliedRotations = preparePlaceRotations(blockPos, direction, RotationUtility.calculate(hitVec));
        queuedAction = QueuedAction.useBlock(hotbarSlot, blockPos, direction, hitVec, appliedRotations);
        return true;
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
        if (!canRun() || shouldPauseForItemUse()) {
            releaseActionRotations();
            return;
        }

        boolean success = switch (action.kind()) {
            case BREAK_CRYSTAL -> runQueuedBreak(action);
            case USE_BLOCK -> runQueuedBlockUse(action);
        };

        if (success) {
            if (action.kind() == QueuedActionKind.BREAK_CRYSTAL) {
                spendBreak();
                postAttackPlaceDelayTicks = POST_ATTACK_PLACE_DELAY_TICKS;
            } else {
                spendPlace();
            }
        }

        releaseActionRotations();
    }

    private boolean runQueuedBreak(QueuedAction action) {
        EndCrystal crystal = action.crystal();
        if (crystal == null || !isBreakableCrystal(crystal, breakRange.getValue())) {
            return false;
        }

        attackWithRotations(action.rotations(), crystal);
        mc.player.swing(InteractionHand.MAIN_HAND);
        usedInteractionThisTick = true;
        return true;
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

    private Vec3 blockHitVec(BlockPos blockPos, Direction direction) {
        return Vec3.atCenterOf(blockPos).add(
                direction.getStepX() * 0.5D,
                direction.getStepY() * 0.5D,
                direction.getStepZ() * 0.5D
        );
    }

    private Vector2f preparePlaceRotations(BlockPos blockPos, Direction direction, Vector2f rotations) {
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
        queuedAction = null;
        syncedAction = null;
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
        if (!controllingRotations) {
            return null;
        }

        if (RotationManager.INSTANCE.isActive()) {
            return new Vector2f(RotationManager.INSTANCE.getRotation());
        }

        return lastControlledRotations == null ? null : new Vector2f(lastControlledRotations);
    }

    private void stopSprintingForMovementFix() {
        if (mc.player != null && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
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

    private int findSupportBlockSlot() {
        int obsidianSlot = InventoryUtility.findHotbarSlot(this::isSupportBlockStack);
        if (Inventory.isHotbarSlot(obsidianSlot)) {
            return obsidianSlot;
        }

        return InventoryUtility.findHotbarSlot(stack -> stack.is(Blocks.BEDROCK.asItem()));
    }

    private int findCrystalSlot() {
        return InventoryUtility.findHotbarSlot(Items.END_CRYSTAL);
    }

    private boolean isSupportBlockStack(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
    }

    private Vec3 crystalPosition(BlockPos basePos) {
        return new Vec3(basePos.getX() + 0.5D, basePos.getY() + 1.0D, basePos.getZ() + 0.5D);
    }

    private void resetState() {
        releaseActionRotations();
        placeProgress = TICKS_PER_SECOND;
        breakProgress = TICKS_PER_SECOND;
        switchProgress = 0;
        lockedTargetId = -1;
        switchTargetIndex = -1;
        originalHotbarSlot = InventoryUtility.NOT_FOUND;
        postUseSlotDelayTicks = 0;
        postAttackPlaceDelayTicks = 0;
        usedInteractionThisTick = false;
        queuedAction = null;
        syncedAction = null;
        lastOutgoingRotations = currentPlayerRotations();
        movementFixRotations = null;
        placeRotationVariant = 0;
        pendingSupportBasePos = null;
    }

    private record CrystalTarget(EndCrystal crystal, LivingEntity target, double score) {
    }

    private record PlaceTarget(BlockPos basePos, LivingEntity target, double score, double targetDamage, boolean covered) {
    }

    private record SupportTarget(BlockPos pos, double score, double targetDamage) {
    }

    private record ClickFace(BlockPos blockPos, Direction direction) {
    }

    private record SegmentRange(double min, double max) {
    }

    private record QueuedAction(
            QueuedActionKind kind,
            EndCrystal crystal,
            int hotbarSlot,
            BlockPos blockPos,
            Direction direction,
            Vec3 hitVec,
            Vector2f rotations
    ) {
        private static QueuedAction breakCrystal(EndCrystal crystal, Vector2f rotations) {
            return new QueuedAction(
                    QueuedActionKind.BREAK_CRYSTAL,
                    crystal,
                    InventoryUtility.NOT_FOUND,
                    null,
                    null,
                    null,
                    rotations == null ? null : new Vector2f(rotations)
            );
        }

        private static QueuedAction useBlock(int hotbarSlot, BlockPos blockPos, Direction direction, Vec3 hitVec, Vector2f rotations) {
            return new QueuedAction(
                    QueuedActionKind.USE_BLOCK,
                    null,
                    hotbarSlot,
                    blockPos,
                    direction,
                    hitVec,
                    rotations == null ? null : new Vector2f(rotations)
            );
        }
    }

    private enum QueuedActionKind {
        BREAK_CRYSTAL,
        USE_BLOCK
    }

    private record MovementInput(float forward, float strafe) {
    }

    private record HorizontalVector(double x, double z) {
        private double lengthSqr() {
            return x * x + z * z;
        }
    }

    public enum Select {
        SINGLE, // 锁定一个目标不切换
        SWITCH, // 在目标间切换
        MUTI // 多目标同时攻击
    }
}
