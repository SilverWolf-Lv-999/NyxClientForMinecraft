package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.mixins.MultiPlayerGameModeAccessor;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

@ModuleInfo(name = "nyxclient.module.scaffold.name", description = "nyxclient.module.scaffold.description", category = Category.MOVEMENT)
public class Scaffold extends Module {
    public static final Scaffold INSTANCE = new Scaffold();

    private static final double BLOCK_REACH_EPSILON = 1.0E-6D;
    private static final double TOWER_JUMP_MOTION = 0.42D;

    public final IntValue delay = ValueBuild.intSetting("delay", 0, 0, 200, 10, this);
    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.NORMAL, this);
    public final IntValue tellyTick = ValueBuild.intSetting(
            "telly tick",
            1,
            1,
            5,
            1,
            () -> mode.is(Mode.TELLY_BRIDGE),
            this
    );
    public final EnumValue<RotationMode> rotationMode = ValueBuild.enumSetting(
            "rotation mode",
            RotationMode.NORMAL,
            this
    );
    public final DoubleValue shrink = ValueBuild.doubleSetting(
            "shrink",
            0.1D,
            0.0D,
            0.45D,
            0.01D,
            () -> rotationMode.is(RotationMode.NEAREST) || rotationMode.is(RotationMode.HYPIXEL),
            this
    );
    public final DoubleValue rotationSpeed = ValueBuild.doubleSetting(
            "rotation speed",
            180.0D,
            0.0D,
            180.0D,
            5.0D,
            this
    );
    public final EnumValue<TowerMode> towerMode = ValueBuild.enumSetting("tower mode", TowerMode.NONE, this);
    public final BoolValue downwards = ValueBuild.boolSetting("downwards", false, this);
    public final BoolValue autoJump = ValueBuild.boolSetting("auto jump", false, this);
    public final BoolValue sprint = ValueBuild.boolSetting("sprint", false, this);
    public final BoolValue rayCast = ValueBuild.boolSetting("ray cast", false, this);
    public final BoolValue maxStack = ValueBuild.boolSetting("max stack", false, this);
    public final BoolValue itemSpoof = ValueBuild.boolSetting("item spoof", false, this);
    public final BoolValue noSwing = ValueBuild.boolSetting("no swing", false, this);
    public final BoolValue movementFix = ValueBuild.boolSetting("movement fix", false, this);

    private int originalHotbarSlot = InventoryUtility.NOT_FOUND;
    private double keepYLevel;
    private int airTicks;
    private long nextPlaceTime;
    private PlaceInfo placeInfo;
    private Vector2f placeRotations;

    @Override
    public void onEnable() {
        originalHotbarSlot = InventoryUtility.getSelectedHotbarSlot();
        keepYLevel = 0.0D;
        airTicks = 0;
        nextPlaceTime = 0L;
        placeInfo = null;
        placeRotations = null;
    }

    @Override
    public void onDisable() {
        restoreOriginalHotbarSlot();
        keepYLevel = 0.0D;
        airTicks = 0;
        nextPlaceTime = 0L;
        placeInfo = null;
        placeRotations = null;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        boolean moving = event.getForward() != 0.0F || event.getStrafe() != 0.0F;
        if (autoJump.getValue() && moving && player.onGround()) {
            event.setJump(true);
        }

        if (downwards.getValue() && mc.options.keyShift.isDown()) {
            event.setSneak(false);
        }

        if (sprint.getValue() && moving && !event.isSneak()) {
            event.setSprint(true);
        }

        if (!movementFix.getValue() || placeRotations == null || !moving) {
            return;
        }

        MovementInput corrected = correctedMovementInput(
                event.getForward(),
                event.getStrafe(),
                player.getYRot(),
                placeRotations.x
        );
        event.setForward(corrected.forward());
        event.setStrafe(corrected.strafe());
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canRun()) {
            return;
        }

        if (mc.player.onGround()) {
            airTicks = 0;
            keepYLevel = Math.floor(mc.player.getY() - 1.0D);
        } else {
            airTicks++;
        }

        if (towerMode.is(TowerMode.VANILLA) && mc.options.keyJump.isDown()) {
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x, TOWER_JUMP_MOTION, velocity.z);
        }

        int blockSlot = findBlockSlot();
        if (!Inventory.isHotbarSlot(blockSlot)) {
            setEnabled(false);
            return;
        }

        BlockPos targetPos = BlockPos.containing(
                mc.player.getX(),
                getYLevel() - (isDownwards() ? 1.0D : 0.0D),
                mc.player.getZ()
        );
        placeInfo = findPlaceInfo(targetPos);
        placeRotations = placeInfo == null ? null : calculateRotations(placeInfo);

        if (placeInfo == null || placeRotations == null || !canPlaceThisTick()) {
            return;
        }

        RotationManager.INSTANCE.setRotations(placeRotations, getRotationSpeed(), Priority.High);

        if (rayCast.getValue() && !passesRayCast(placeInfo)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextPlaceTime) {
            return;
        }

        if (placeBlock(blockSlot, placeInfo)) {
            nextPlaceTime = now + delay.getValue();
        }
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && !mc.player.isSpectator()
                && !mc.player.getAbilities().flying
                && !mc.player.isFallFlying()
                && !mc.player.isPassenger()
                && !mc.player.isInWater()
                && !mc.player.isInLava();
    }

    private int findBlockSlot() {
        if (mc.player == null) {
            return InventoryUtility.NOT_FOUND;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!maxStack.getValue() && Inventory.isHotbarSlot(selectedSlot)) {
            ItemStack selectedStack = InventoryUtility.getStack(selectedSlot);
            if (selectedStack.getItem() instanceof BlockItem) {
                return selectedSlot;
            }
        }

        int bestSlot = InventoryUtility.NOT_FOUND;
        int bestCount = -1;

        for (int slot = InventoryUtility.HOTBAR_START; slot < InventoryUtility.HOTBAR_END; slot++) {
            ItemStack stack = InventoryUtility.getStack(slot);
            if (!(stack.getItem() instanceof BlockItem)) {
                continue;
            }

            if (!maxStack.getValue()) {
                return slot;
            }

            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private boolean canPlaceThisTick() {
        return mode.is(Mode.NORMAL)
                || airTicks >= tellyTick.getValue()
                || !isMoving();
    }

    private boolean isMoving() {
        return mc.player != null
                && (mc.player.xxa != 0.0F
                || mc.player.zza != 0.0F
                || mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown());
    }

    private PlaceInfo findPlaceInfo(BlockPos targetPos) {
        if (mc.level == null || !mc.level.getBlockState(targetPos).canBeReplaced()) {
            return null;
        }

        double reach = mc.player.blockInteractionRange();
        double reachSqr = reach * reach + BLOCK_REACH_EPSILON;
        Vec3 eyePos = mc.player.getEyePosition();
        PlaceInfo best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                for (int xSign : x == 0 ? new int[]{1} : new int[]{-1, 1}) {
                    for (int zSign : z == 0 ? new int[]{1} : new int[]{-1, 1}) {
                        BlockPos candidate = targetPos.offset(x * xSign, 0, z * zSign);
                        if (!mc.level.getBlockState(candidate).canBeReplaced()) {
                            continue;
                        }

                        for (Direction direction : Direction.values()) {
                            if (!isDownwards() && direction == Direction.UP) {
                                continue;
                            }

                            BlockPos clickedBlock = candidate.relative(direction);
                            BlockState clickedState = mc.level.getBlockState(clickedBlock);
                            if (clickedState.canBeReplaced() || !clickedState.getFluidState().isEmpty()) {
                                continue;
                            }

                            Direction facing = direction.getOpposite();
                            Vec3 hitVec = hitVec(clickedBlock, facing);
                            double distance = eyePos.distanceToSqr(hitVec);
                            if (distance > reachSqr || distance >= bestDistance) {
                                continue;
                            }

                            bestDistance = distance;
                            best = new PlaceInfo(clickedBlock, facing, hitVec);
                        }
                    }
                }
            }
        }

        return best;
    }

    private Vector2f calculateRotations(PlaceInfo info) {
        return switch (rotationMode.getValue()) {
            case NORMAL -> RotationUtility.calculate(info.clickedBlock());
            case FACING -> RotationUtility.calculate(info.clickedBlock(), info.facing());
            case HIT_VEC -> RotationUtility.calculate(info.hitVec());
            case NEAREST, HYPIXEL -> RotationUtility.calculate(nearestHitVec(info));
        };
    }

    private Vec3 nearestHitVec(PlaceInfo info) {
        Vec3 center = Vec3.atCenterOf(info.clickedBlock());
        Vec3 eye = mc.player.getEyePosition();
        double extent = Math.max(0.05D, 0.5D - shrink.getValue());
        double x = center.x;
        double y = center.y;
        double z = center.z;

        if (info.facing().getAxis() == Direction.Axis.X) {
            x += info.facing().getStepX() * 0.5D;
            y = Mth.clamp(eye.y, center.y - extent, center.y + extent);
            z = Mth.clamp(eye.z, center.z - extent, center.z + extent);
        } else if (info.facing().getAxis() == Direction.Axis.Y) {
            y += info.facing().getStepY() * 0.5D;
            x = Mth.clamp(eye.x, center.x - extent, center.x + extent);
            z = Mth.clamp(eye.z, center.z - extent, center.z + extent);
        } else {
            z += info.facing().getStepZ() * 0.5D;
            x = Mth.clamp(eye.x, center.x - extent, center.x + extent);
            y = Mth.clamp(eye.y, center.y - extent, center.y + extent);
        }

        return new Vec3(x, y, z);
    }

    private boolean passesRayCast(PlaceInfo info) {
        HitResult result = mc.level.clip(new ClipContext(
                mc.player.getEyePosition(),
                info.hitVec(),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        return result instanceof BlockHitResult blockHitResult
                && blockHitResult.getBlockPos().equals(info.clickedBlock())
                && blockHitResult.getDirection() == info.facing();
    }

    private boolean placeBlock(int blockSlot, PlaceInfo info) {
        BlockHitResult hitResult = new BlockHitResult(
                info.hitVec(),
                info.facing(),
                info.clickedBlock(),
                false
        );

        boolean placed;
        if (itemSpoof.getValue()) {
            placed = placeWithItemSpoof(blockSlot, hitResult);
        } else {
            if (!InventoryUtility.selectHotbarSlot(blockSlot, true)) {
                return false;
            }

            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            placed = result.consumesAction();
        }

        if (placed) {
            swingHand();
        }
        return placed;
    }

    private boolean placeWithItemSpoof(int blockSlot, BlockHitResult hitResult) {
        if (mc.player.connection == null || !Inventory.isHotbarSlot(blockSlot)) {
            return false;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(selectedSlot)) {
            return false;
        }

        boolean changedSlot = selectedSlot != blockSlot;
        if (changedSlot) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(blockSlot));
        }

        try {
            ((MultiPlayerGameModeAccessor) mc.gameMode).nyx$startPrediction(
                    mc.level,
                    sequence -> new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, sequence)
            );
        } finally {
            if (changedSlot) {
                mc.player.connection.send(new ServerboundSetCarriedItemPacket(selectedSlot));
            }
        }

        return true;
    }

    private void swingHand() {
        if (noSwing.getValue()) {
            mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        } else {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void restoreOriginalHotbarSlot() {
        if (Inventory.isHotbarSlot(originalHotbarSlot) && mc.player != null) {
            InventoryUtility.selectHotbarSlot(originalHotbarSlot, true);
        }
        originalHotbarSlot = InventoryUtility.NOT_FOUND;
    }

    private double getYLevel() {
        double playerY = mc.player.getY();
        if (!autoJump.getValue()) {
            return playerY - 1.0D;
        }

        double currentYLevel = playerY - 1.0D;
        return currentYLevel >= keepYLevel
                && Math.abs(currentYLevel - keepYLevel) <= 3.0D
                && !mc.options.keyJump.isDown()
                ? keepYLevel
                : currentYLevel;
    }

    private double getRotationSpeed() {
        if (!rotationMode.is(RotationMode.HYPIXEL)) {
            return rotationSpeed.getValue();
        }

        if (mc.options.keyJump.isDown() && !isMoving()) {
            return rotationSpeed.getValue();
        }
        if (mc.player.getDeltaMovement().y <= 0.0D || !canPlaceThisTick()) {
            return rotationSpeed.getValue();
        }
        return airTicks == tellyTick.getValue() ? 120.0D : 35.0D;
    }

    private boolean isDownwards() {
        return downwards.getValue() && mc.options.keyShift.isDown();
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

    private Vec3 hitVec(BlockPos blockPos, Direction facing) {
        return Vec3.atCenterOf(blockPos).add(
                facing.getStepX() * 0.5D,
                facing.getStepY() * 0.5D,
                facing.getStepZ() * 0.5D
        );
    }

    private record PlaceInfo(BlockPos clickedBlock, Direction facing, Vec3 hitVec) {
    }

    private record MovementInput(float forward, float strafe) {
    }

    private record HorizontalVector(double x, double z) {
        private double lengthSqr() {
            return x * x + z * z;
        }
    }

    public enum Mode {
        NORMAL,
        TELLY_BRIDGE
    }

    public enum RotationMode {
        NORMAL,
        FACING,
        HIT_VEC,
        NEAREST,
        HYPIXEL
    }

    public enum TowerMode {
        NONE,
        VANILLA
    }
}
