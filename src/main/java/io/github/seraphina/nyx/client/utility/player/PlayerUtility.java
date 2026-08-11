package io.github.seraphina.nyx.client.utility.player;

import io.github.seraphina.nyx.client.utility.IMinecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.common.CommonHooks;

import java.util.Optional;
import java.util.function.Predicate;

public final class PlayerUtility implements IMinecraft {
    public static boolean isInsideBlock() {
        return mc.player != null
                && mc.level != null
                && !mc.level.noBlockCollision(mc.player, mc.player.getBoundingBox().deflate(1.0E-7D));
    }

    public static void sendMsg(String msg) {
        if (mc.player == null || mc.player.connection == null || msg == null || msg.isBlank()) {
            return;
        }

        mc.player.connection.sendChat(msg.trim());
    }

    public static void runCmd(String cmd) {
        if (mc.player == null || mc.player.connection == null || cmd == null || cmd.isBlank()) {
            return;
        }

        String command = cmd.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (!command.isBlank()) {
            mc.player.connection.sendCommand(command);
        }
    }

    /**
     * Mirrors {@code GameRenderer#pick}. A block hit remains available as a fallback,
     * but it never truncates entity selection.
     */
    public static HitResult getPlayerInteractionHitResult(float partialTick) {
        Entity cameraEntity = mc.getCameraEntity();
        if (mc.level == null || mc.player == null || cameraEntity == null) {
            return null;
        }

        return getInteractionHitResult(
                mc.level,
                cameraEntity,
                mc.player.blockInteractionRange(),
                mc.player.entityInteractionRange(),
                partialTick
        );
    }

    /**
     * Mirrors {@code GameRenderer#pick} while removing only block and entity
     * occlusion from the entity target selection path.
     */
    public static HitResult getInteractionHitResult(
            Level level,
            Entity originEntity,
            double blockInteractionRange,
            double entityInteractionRange,
            float partialTick
    ) {
        double maximumRange = Math.max(blockInteractionRange, entityInteractionRange);
        Vec3 start = originEntity.getEyePosition(partialTick);
        Vec3 view = originEntity.getViewVector(partialTick);
        Vec3 end = start.add(view.x * maximumRange, view.y * maximumRange, view.z * maximumRange);
        BlockHitResult blockHitResult = level.clip(
                new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, originEntity)
        );
        AABB range = originEntity.getBoundingBox().expandTowards(view.scale(maximumRange)).inflate(1.0D);
        EntityHitResult entityHitResult = findEntityHitResult(
                level,
                originEntity,
                start,
                end,
                range,
                EntitySelector.CAN_BE_PICKED,
                start.distanceToSqr(end)
        );

        if (entityHitResult != null) {
            return filterHitResult(entityHitResult, start, entityInteractionRange);
        }

        return filterHitResult(blockHitResult, start, blockInteractionRange);
    }

    /**
     * Returns the closest valid entity on the supplied ray. The boolean only controls
     * whether a block hit is returned when no entity is hit; blocks never occlude entities.
     */
    public static HitResult raycastForEntity(Level level, Entity originEntity, float distance, boolean returnBlockHitResult) {
        Vec3 start = originEntity.getEyePosition();
        Vec3 end = originEntity.getLookAngle().scale(distance).add(start);
        return raycastForEntity(level, originEntity, start, end, returnBlockHitResult);
    }

    public static HitResult raycastForEntity(
            Level level,
            Entity originEntity,
            float distance,
            boolean returnBlockHitResult,
            Predicate<? super Entity> filter
    ) {
        Vec3 start = originEntity.getEyePosition();
        Vec3 end = originEntity.getLookAngle().scale(distance).add(start);
        return raycastForEntity(level, originEntity, start, end, returnBlockHitResult, filter);
    }

    public static HitResult raycastForEntity(
            Level level,
            Entity originEntity,
            Vec3 start,
            Vec3 end,
            boolean returnBlockHitResult
    ) {
        return raycastForEntity(level, originEntity, start, end, returnBlockHitResult, EntitySelector.CAN_BE_PICKED);
    }

    public static HitResult raycastForEntity(
            Level level,
            Entity originEntity,
            Vec3 start,
            Vec3 end,
            boolean returnBlockHitResult,
            Predicate<? super Entity> filter
    ) {
        Predicate<? super Entity> raycastFilter = filter == null
                ? EntitySelector.CAN_BE_PICKED
                : entity -> EntitySelector.CAN_BE_PICKED.test(entity) && filter.test(entity);
        AABB range = originEntity.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0D);
        EntityHitResult entityHitResult = findEntityHitResult(
                level,
                originEntity,
                start,
                end,
                range,
                raycastFilter,
                start.distanceToSqr(end)
        );
        if (entityHitResult != null) {
            return entityHitResult;
        }

        if (returnBlockHitResult) {
            return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, originEntity));
        }

        return createMissHitResult(end, start);
    }

    public static boolean attackEntity(Entity entity) {
        if (entity == null) {
            return false;
        }

        return attack(new EntityHitResult(entity, entity.getBoundingBox().getCenter()));
    }

    public static boolean attackBlock(BlockPos blockPos, Direction direction) {
        if (blockPos == null || direction == null) {
            return false;
        }

        return attack(createBlockHitResult(blockPos, direction));
    }

    /**
     * Mirrors the target handling in {@code Minecraft#startAttack}. The caller supplies
     * the target directly, so no crosshair, reach, or occlusion test is performed here.
     */
    public static boolean attack(HitResult hitResult) {
        if (!isInteractionReady() || hitResult == null || mc.player.isHandsBusy()) {
            return false;
        }

        ItemStack heldStack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!heldStack.isItemEnabled(mc.level.enabledFeatures())) {
            return false;
        }

        var inputEvent = ClientHooks.onClickInput(0, mc.options.keyAttack, InteractionHand.MAIN_HAND);
        if (inputEvent.isCanceled()) {
            if (inputEvent.shouldSwingHand()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            return false;
        }

        boolean destroyedImmediately = false;
        if (hitResult instanceof EntityHitResult entityHitResult) {
            mc.gameMode.attack(mc.player, entityHitResult.getEntity());
        } else if (hitResult instanceof BlockHitResult blockHitResult
                && !mc.level.getBlockState(blockHitResult.getBlockPos()).isAir()) {
            mc.gameMode.startDestroyBlock(blockHitResult.getBlockPos(), blockHitResult.getDirection());
            destroyedImmediately = mc.level.getBlockState(blockHitResult.getBlockPos()).isAir();
        } else {
            mc.player.resetAttackStrengthTicker();
            CommonHooks.onEmptyLeftClick(mc.player);
        }

        if (inputEvent.shouldSwingHand()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        return destroyedImmediately;
    }

    public static boolean continueDestroyBlock(BlockPos blockPos, Direction direction) {
        if (!isInteractionReady() || blockPos == null || direction == null || mc.player.isUsingItem()) {
            return false;
        }

        if (mc.level.getBlockState(blockPos).isAir()) {
            mc.gameMode.stopDestroyBlock();
            return false;
        }

        var inputEvent = ClientHooks.onClickInput(0, mc.options.keyAttack, InteractionHand.MAIN_HAND);
        if (inputEvent.isCanceled()) {
            if (inputEvent.shouldSwingHand()) {
                mc.level.addBreakingBlockEffect(blockPos, direction, createBlockHitResult(blockPos, direction));
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            return false;
        }

        boolean isDestroying = mc.gameMode.continueDestroyBlock(blockPos, direction);
        if (isDestroying && inputEvent.shouldSwingHand()) {
            mc.level.addBreakingBlockEffect(blockPos, direction, createBlockHitResult(blockPos, direction));
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        return isDestroying;
    }

    public static void stopDestroyBlock() {
        if (isInteractionReady()) {
            mc.gameMode.stopDestroyBlock();
        }
    }

    public static boolean interactEntity(Entity entity) {
        if (entity == null) {
            return false;
        }

        return interact(new EntityHitResult(entity, entity.getBoundingBox().getCenter()));
    }

    public static boolean interactEntity(Entity entity, Vec3 hitLocation, InteractionHand hand) {
        if (entity == null || hitLocation == null || hand == null) {
            return false;
        }

        return interact(new EntityHitResult(entity, hitLocation), hand);
    }

    public static boolean interactBlock(BlockPos blockPos, Direction direction, InteractionHand hand) {
        if (blockPos == null || direction == null || hand == null) {
            return false;
        }

        return interact(createBlockHitResult(blockPos, direction), hand);
    }

    /**
     * Places against the support face adjacent to {@code placePos}. This has no
     * client-side line-of-sight or reach check.
     */
    public static boolean placeBlock(BlockPos placePos, Direction supportFace, InteractionHand hand) {
        if (placePos == null || supportFace == null || hand == null) {
            return false;
        }

        BlockPos supportPos = placePos.relative(supportFace.getOpposite());
        return interactBlock(supportPos, supportFace, hand);
    }

    public static boolean useItem(InteractionHand hand) {
        return interact(null, hand);
    }

    public static void releaseUsingItem() {
        if (isInteractionReady()) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
    }

    /**
     * Mirrors {@code Minecraft#startUseItem}. The target comes from the caller instead
     * of the game's crosshair result, so occluding blocks and entities are ignored.
     */
    public static boolean interact(HitResult hitResult) {
        if (!canStartInteraction()) {
            return false;
        }

        for (InteractionHand hand : InteractionHand.values()) {
            InteractionAttemptResult result = interactWithHand(hitResult, hand);
            if (result == InteractionAttemptResult.SUCCESS) {
                return true;
            }
            if (result == InteractionAttemptResult.STOPPED) {
                return false;
            }
        }

        return false;
    }

    public static boolean interact(HitResult hitResult, InteractionHand hand) {
        return canStartInteraction()
                && hand != null
                && interactWithHand(hitResult, hand) == InteractionAttemptResult.SUCCESS;
    }

    public static BlockHitResult createBlockHitResult(BlockPos blockPos, Direction direction) {
        Vec3 hitLocation = Vec3.atCenterOf(blockPos).add(
                direction.getStepX() * 0.5D,
                direction.getStepY() * 0.5D,
                direction.getStepZ() * 0.5D
        );
        return new BlockHitResult(hitLocation, direction, blockPos, false);
    }

    public static HitResult checkEntityIntersecting(Entity entity, Vec3 start, Vec3 end, float bbInflation) {
        AABB targetBox = entity.getBoundingBox().inflate(entity.getPickRadius() + bbInflation);
        Optional<Vec3> hitPosition = targetBox.clip(start, end);
        if (targetBox.contains(start)) {
            return new EntityHitResult(entity, hitPosition.orElse(start));
        }

        return hitPosition.<HitResult>map(position -> new EntityHitResult(entity, position))
                .orElseGet(() -> createMissHitResult(end, start));
    }

    private static InteractionAttemptResult interactWithHand(HitResult hitResult, InteractionHand hand) {
        var inputEvent = ClientHooks.onClickInput(1, mc.options.keyUse, hand);
        if (inputEvent.isCanceled()) {
            if (inputEvent.shouldSwingHand()) {
                mc.player.swing(hand);
            }
            return InteractionAttemptResult.STOPPED;
        }

        ItemStack heldStack = mc.player.getItemInHand(hand);
        if (!heldStack.isItemEnabled(mc.level.enabledFeatures())) {
            return InteractionAttemptResult.STOPPED;
        }

        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            if (!mc.level.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                return InteractionAttemptResult.STOPPED;
            }

            InteractionResult result = mc.gameMode.interactAt(mc.player, entity, entityHitResult, hand);
            if (!result.consumesAction()) {
                result = mc.gameMode.interact(mc.player, entity, hand);
            }

            if (result instanceof InteractionResult.Success success) {
                if (success.swingSource() == InteractionResult.SwingSource.CLIENT && inputEvent.shouldSwingHand()) {
                    mc.player.swing(hand);
                }
                return InteractionAttemptResult.SUCCESS;
            }
        } else if (hitResult instanceof BlockHitResult blockHitResult) {
            int stackCount = heldStack.getCount();
            InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, blockHitResult);
            if (result instanceof InteractionResult.Success success) {
                if (success.swingSource() == InteractionResult.SwingSource.CLIENT && inputEvent.shouldSwingHand()) {
                    mc.player.swing(hand);
                    if (!heldStack.isEmpty() && (heldStack.getCount() != stackCount || mc.player.hasInfiniteMaterials())) {
                        mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
                    }
                }
                return InteractionAttemptResult.SUCCESS;
            }

            if (result instanceof InteractionResult.Fail) {
                return InteractionAttemptResult.STOPPED;
            }
        }

        if (heldStack.isEmpty() && (hitResult == null || hitResult.getType() == HitResult.Type.MISS)) {
            CommonHooks.onEmptyClick(mc.player, hand);
        }

        InteractionResult result = mc.gameMode.useItem(mc.player, hand);
        if (result instanceof InteractionResult.Success success) {
            if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
                mc.player.swing(hand);
            }
            mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
            return InteractionAttemptResult.SUCCESS;
        }

        return InteractionAttemptResult.PASS;
    }

    private static EntityHitResult findEntityHitResult(
            Level level,
            Entity originEntity,
            Vec3 start,
            Vec3 end,
            AABB range,
            Predicate<? super Entity> filter,
            double maximumDistanceSqr
    ) {
        double closestDistanceSqr = maximumDistanceSqr;
        Entity closestEntity = null;
        Vec3 closestHitPosition = null;

        for (Entity target : level.getEntities(originEntity, range, filter::test)) {
            AABB targetBox = target.getBoundingBox().inflate(target.getPickRadius());
            Optional<Vec3> hitPosition = targetBox.clip(start, end);
            if (targetBox.contains(start)) {
                if (closestDistanceSqr >= 0.0D) {
                    closestEntity = target;
                    closestHitPosition = hitPosition.orElse(start);
                    closestDistanceSqr = 0.0D;
                }
            } else if (hitPosition.isPresent()) {
                Vec3 currentHitPosition = hitPosition.get();
                double currentDistanceSqr = start.distanceToSqr(currentHitPosition);
                if (currentDistanceSqr < closestDistanceSqr || closestDistanceSqr == 0.0D) {
                    if (target.getRootVehicle() == originEntity.getRootVehicle() && !target.canRiderInteract()) {
                        if (closestDistanceSqr == 0.0D) {
                            closestEntity = target;
                            closestHitPosition = currentHitPosition;
                        }
                    } else {
                        closestEntity = target;
                        closestHitPosition = currentHitPosition;
                        closestDistanceSqr = currentDistanceSqr;
                    }
                }
            }
        }

        return closestEntity == null ? null : new EntityHitResult(closestEntity, closestHitPosition);
    }

    private static HitResult filterHitResult(HitResult hitResult, Vec3 start, double interactionRange) {
        if (hitResult.getLocation().closerThan(start, interactionRange)) {
            return hitResult;
        }

        return createMissHitResult(hitResult.getLocation(), start);
    }

    private static BlockHitResult createMissHitResult(Vec3 location, Vec3 start) {
        Direction direction = Direction.getApproximateNearest(
                location.x - start.x,
                location.y - start.y,
                location.z - start.z
        );
        return BlockHitResult.miss(location, direction, BlockPos.containing(location));
    }

    private static boolean isInteractionReady() {
        return mc.player != null && mc.level != null && mc.gameMode != null;
    }

    private static boolean canStartInteraction() {
        return isInteractionReady() && !mc.gameMode.isDestroying() && !mc.player.isHandsBusy();
    }

    private enum InteractionAttemptResult {
        SUCCESS,
        PASS,
        STOPPED
    }
}
