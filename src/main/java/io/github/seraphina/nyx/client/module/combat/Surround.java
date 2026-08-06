package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.player.PacketMine;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.utility.player.PacketUtility;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ModuleInfo(name = "nyxclient.module.surround.name", description = "nyxclient.module.surround.description", category = Category.COMBAT)
public final class Surround extends Module {
    public static final Surround INSTANCE = new Surround();

    public final EnumValue<Page> page = ValueBuild.enumSetting("page", Page.General, this);

    public final IntValue placeDelay = ValueBuild.intSetting(
            "place delay",
            50,
            0,
            500,
            10,
            () -> page.is(Page.General),
            this
    );
    public final IntValue blocksPerTick = ValueBuild.intSetting(
            "blocks per tick",
            1,
            1,
            8,
            1,
            () -> page.is(Page.General),
            this
    );
    public final BoolValue packetPlace = ValueBuild.boolSetting(
            "packet place",
            true,
            () -> page.is(Page.General),
            this
    );
    public final BoolValue breakCrystals = ValueBuild.boolSetting(
            "break crystals",
            true,
            () -> page.is(Page.General),
            this
    );
    public final BoolValue usingPause = ValueBuild.boolSetting(
            "using pause",
            true,
            () -> page.is(Page.General) && breakCrystals.getValue(),
            this
    );
    public final BoolValue center = ValueBuild.boolSetting(
            "center",
            true,
            () -> page.is(Page.General),
            this
    );
    public final BoolValue extend = ValueBuild.boolSetting(
            "extend",
            true,
            () -> page.is(Page.General),
            this
    );
    public final BoolValue onlySelf = ValueBuild.boolSetting(
            "only self",
            false,
            () -> page.is(Page.General) && extend.getValue(),
            this
    );
    public final BoolValue inventorySwap = ValueBuild.boolSetting(
            "inventory swap",
            true,
            () -> page.is(Page.General),
            this
    );
    public final BoolValue enderChest = ValueBuild.boolSetting(
            "ender chest",
            true,
            () -> page.is(Page.General),
            this
    );

    public final BoolValue rotate = ValueBuild.boolSetting(
            "rotate",
            true,
            () -> page.is(Page.Rotate),
            this
    );
    public final BoolValue yawStep = ValueBuild.boolSetting(
            "yaw step",
            false,
            () -> page.is(Page.Rotate) && rotate.getValue(),
            this
    );
    public final DoubleValue steps = ValueBuild.doubleSetting(
            "steps",
            0.05D,
            0.0D,
            1.0D,
            0.01D,
            () -> page.is(Page.Rotate) && rotate.getValue() && yawStep.getValue(),
            this
    );
    public final BoolValue onlyLooking = ValueBuild.boolSetting(
            "only looking",
            true,
            () -> page.is(Page.Rotate) && rotate.getValue() && yawStep.getValue(),
            this
    );
    public final DoubleValue fov = ValueBuild.doubleSetting(
            "fov",
            5.0D,
            0.0D,
            30.0D,
            0.5D,
            () -> page.is(Page.Rotate) && rotate.getValue() && yawStep.getValue() && onlyLooking.getValue(),
            this
    );

    public final BoolValue detectMining = ValueBuild.boolSetting(
            "detect mining",
            false,
            () -> page.is(Page.Check),
            this
    );
    public final BoolValue inAir = ValueBuild.boolSetting(
            "in air",
            true,
            () -> page.is(Page.Check),
            this
    );
    public final BoolValue moveDisable = ValueBuild.boolSetting(
            "move disable",
            true,
            () -> page.is(Page.Check),
            this
    );
    public final BoolValue jumpDisable = ValueBuild.boolSetting(
            "jump disable",
            true,
            () -> page.is(Page.Check),
            this
    );

    private long lastPlaceTime;
    private double startX;
    private double startY;
    private double startZ;
    private boolean shouldCenter;

    private Surround() {
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) {
            return;
        }

        startX = mc.player.getX();
        startY = mc.player.getY();
        startZ = mc.player.getZ();
        lastPlaceTime = 0L;
        shouldCenter = true;
    }

    @Override
    public void onDisable() {
        lastPlaceTime = 0L;
        shouldCenter = false;
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canRun()) {
            return;
        }

        centerPlayer();
        updateStartPosition();
        if (shouldDisableForMovement() || !inAir.getValue() && !mc.player.onGround()) {
            return;
        }
        if (usingPause.getValue() && mc.player.isUsingItem()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastPlaceTime < placeDelay.getValue()) {
            return;
        }

        int originalHotbarSlot = InventoryUtility.getSelectedHotbarSlot();
        BlockSelection selection = selectBlock();
        if (selection == null) {
            setEnabled(false);
            return;
        }

        try {
            List<BlockPos> targets = collectTargets();
            if (breakCrystals.getValue()) {
                breakCrystals(targets);
            }

            int placed = 0;
            for (BlockPos target : targets) {
                if (placed >= blocksPerTick.getValue()) {
                    break;
                }

                Placement placement = findPlacement(target);
                if (placement != null && placeBlock(selection.hotbarSlot(), placement)) {
                    placed++;
                    lastPlaceTime = now;
                }
            }
        } finally {
            restoreInventorySwap(selection);
            restoreSelectedHotbarSlot(originalHotbarSlot);
        }
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.player.connection != null
                && mc.screen == null
                && !mc.player.isSpectator()
                && !mc.player.isPassenger()
                && !mc.player.getAbilities().flying
                && !mc.player.isFallFlying();
    }

    private void centerPlayer() {
        if (!shouldCenter || !center.getValue()) {
            return;
        }

        BlockPos playerPos = mc.player.blockPosition();
        double targetX = playerPos.getX() + 0.5D;
        double targetZ = playerPos.getZ() + 0.5D;
        double deltaX = targetX - mc.player.getX();
        double deltaZ = targetZ - mc.player.getZ();

        if (Math.abs(deltaX) <= 0.2D && Math.abs(deltaZ) <= 0.2D) {
            if (mc.player.onGround() || MovingUtility.isMoving()) {
                Vec3 velocity = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(0.0D, velocity.y, 0.0D);
                shouldCenter = false;
            }
            return;
        }

        double distance = Math.hypot(deltaX, deltaZ);
        if (distance <= 1.0E-6D) {
            shouldCenter = false;
            return;
        }

        double speed = Math.min(0.2873D, distance);
        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(deltaX / distance * speed, velocity.y, deltaZ / distance * speed);
    }

    private void updateStartPosition() {
        if (!MovingUtility.isMoving() && !mc.options.keyJump.isDown()) {
            startX = mc.player.getX();
            startY = mc.player.getY();
            startZ = mc.player.getZ();
        }
    }

    private boolean shouldDisableForMovement() {
        double moved = Math.sqrt(mc.player.distanceToSqr(startX, startY, startZ));
        boolean movedTooFar = moveDisable.getValue() && moved > 1.0D;
        boolean jumpedTooHigh = jumpDisable.getValue() && Math.abs(startY - mc.player.getY()) > 0.5D;
        if (!movedTooFar && !jumpedTooHigh) {
            return false;
        }

        setEnabled(false);
        return true;
    }

    private BlockSelection selectBlock() {
        int hotbarSlot = findHotbarBlockSlot();
        if (Inventory.isHotbarSlot(hotbarSlot)) {
            return new BlockSelection(hotbarSlot, InventoryUtility.NOT_FOUND);
        }

        if (!inventorySwap.getValue() || InventoryUtility.hasCarriedStack()) {
            return null;
        }

        int inventorySlot = findInventoryBlockSlot();
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!InventoryUtility.isMainInventorySlot(inventorySlot) || !Inventory.isHotbarSlot(selectedSlot)) {
            return null;
        }
        if (!InventoryUtility.moveInventorySlotToHotbar(inventorySlot, selectedSlot)) {
            return null;
        }

        return new BlockSelection(selectedSlot, inventorySlot);
    }

    private int findHotbarBlockSlot() {
        int obsidianSlot = InventoryUtility.findHotbarSlot(Items.OBSIDIAN);
        if (Inventory.isHotbarSlot(obsidianSlot) || !enderChest.getValue()) {
            return obsidianSlot;
        }

        return InventoryUtility.findHotbarSlot(Items.ENDER_CHEST);
    }

    private int findInventoryBlockSlot() {
        int obsidianSlot = InventoryUtility.findInventorySlot(Items.OBSIDIAN);
        if (obsidianSlot != InventoryUtility.NOT_FOUND || !enderChest.getValue()) {
            return obsidianSlot;
        }

        return InventoryUtility.findInventorySlot(Items.ENDER_CHEST);
    }

    private List<BlockPos> collectTargets() {
        Set<BlockPos> targets = new LinkedHashSet<>();
        BlockPos center = mc.player.blockPosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            targets.add(center.relative(direction));
        }

        if (!extend.getValue()) {
            return new ArrayList<>(targets);
        }

        for (BlockPos target : new ArrayList<>(targets)) {
            if (!shouldExtendAt(target)) {
                continue;
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos second = target.relative(direction);
                targets.add(second);
                if (shouldExtendAt(second)) {
                    for (Direction thirdDirection : Direction.Plane.HORIZONTAL) {
                        targets.add(second.relative(thirdDirection));
                    }
                }
            }
        }

        return new ArrayList<>(targets);
    }

    private boolean shouldExtendAt(BlockPos pos) {
        return intersectsSelf(pos) || !onlySelf.getValue() && intersectsOtherPlayer(pos);
    }

    private boolean intersectsSelf(BlockPos pos) {
        return mc.player.getBoundingBox().intersects(new AABB(pos));
    }

    private boolean intersectsOtherPlayer(BlockPos pos) {
        AABB box = new AABB(pos);
        for (Player player : mc.level.players()) {
            if (player != mc.player && player.isAlive() && player.getBoundingBox().intersects(box)) {
                return true;
            }
        }
        return false;
    }

    private Placement findPlacement(BlockPos target) {
        if (isMining(target) || !canPlace(target)) {
            return null;
        }

        Placement directPlacement = findClickFace(target);
        if (directPlacement != null) {
            return directPlacement;
        }

        for (Direction direction : Direction.values()) {
            BlockPos helper = target.relative(direction);
            if (isMining(helper) || !canPlace(helper)) {
                continue;
            }

            Placement helperPlacement = findClickFace(helper);
            if (helperPlacement != null) {
                return helperPlacement;
            }
        }
        return null;
    }

    private boolean canPlace(BlockPos pos) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) {
            return false;
        }

        for (Entity entity : mc.level.getEntities(null, new AABB(pos))) {
            if (entity == mc.player
                    || !entity.isAlive()
                    || entity instanceof ItemEntity
                    || entity instanceof ExperienceOrb
                    || entity instanceof Projectile) {
                continue;
            }
            if (entity instanceof EndCrystal && breakCrystals.getValue()) {
                continue;
            }
            return false;
        }
        return true;
    }

    private Placement findClickFace(BlockPos target) {
        double reach = mc.player.blockInteractionRange();
        double reachSqr = reach * reach + 1.0E-6D;
        Vec3 eyePos = mc.player.getEyePosition();
        Placement best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos clickedBlock = target.relative(direction);
            BlockState clickedState = mc.level.getBlockState(clickedBlock);
            if (clickedState.canBeReplaced() || !clickedState.getFluidState().isEmpty()) {
                continue;
            }

            Direction face = direction.getOpposite();
            Vec3 hitVec = Vec3.atCenterOf(clickedBlock).add(
                    face.getStepX() * 0.5D,
                    face.getStepY() * 0.5D,
                    face.getStepZ() * 0.5D
            );
            double distance = eyePos.distanceToSqr(hitVec);
            if (distance <= reachSqr && distance < bestDistance) {
                bestDistance = distance;
                best = new Placement(clickedBlock, face, hitVec);
            }
        }

        return best;
    }

    private boolean placeBlock(int hotbarSlot, Placement placement) {
        if (!Inventory.isHotbarSlot(hotbarSlot) || !isSurroundBlock(InventoryUtility.getStack(hotbarSlot).getItem())) {
            return false;
        }

        Vector2f rotations = getPlacementRotations(placement.hitVec());
        if (rotate.getValue() && rotations == null) {
            return false;
        }

        BlockHitResult hitResult = new BlockHitResult(
                placement.hitVec(),
                placement.face(),
                placement.clickedBlock(),
                false
        );
        if (rotations != null) {
            sendRotation(rotations);
        }

        if (packetPlace.getValue()) {
            return PacketUtility.useHotbarItemOnBlock(hotbarSlot, hitResult);
        }

        if (!InventoryUtility.selectHotbarSlot(hotbarSlot, true)) {
            return false;
        }

        InteractionResult result = useItemOnWithRotations(rotations, hitResult);
        if (result.consumesAction()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        return result.consumesAction();
    }

    private Vector2f getPlacementRotations(Vec3 hitVec) {
        if (!rotate.getValue()) {
            return null;
        }

        Vector2f targetRotations = RotationUtility.calculate(hitVec);
        if (!yawStep.getValue()) {
            return targetRotations;
        }

        RotationManager.INSTANCE.setRotations(targetRotations, steps.getValue(), Priority.High);
        Vector2f steppedRotations = new Vector2f(RotationManager.INSTANCE.getRotation());
        if (onlyLooking.getValue() && !isWithinFov(targetRotations, steppedRotations)) {
            return null;
        }
        return steppedRotations;
    }

    private boolean isWithinFov(Vector2f target, Vector2f current) {
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(target.x - current.x)) <= fov.getValue();
    }

    private InteractionResult useItemOnWithRotations(Vector2f rotations, BlockHitResult hitResult) {
        if (rotations == null) {
            return mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        }

        float previousYaw = mc.player.getYRot();
        float previousPitch = mc.player.getXRot();
        mc.player.setYRot(rotations.x);
        mc.player.setXRot(rotations.y);
        try {
            return mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        } finally {
            mc.player.setYRot(previousYaw);
            mc.player.setXRot(previousPitch);
        }
    }

    private void breakCrystals(List<BlockPos> targets) {
        Set<Integer> attacked = new LinkedHashSet<>();
        for (BlockPos target : targets) {
            for (Entity entity : mc.level.getEntities(null, new AABB(target))) {
                if (entity instanceof EndCrystal crystal && crystal.isAlive() && attacked.add(crystal.getId())) {
                    attackCrystal(crystal);
                }
            }
        }
    }

    private void attackCrystal(EndCrystal crystal) {
        Vector2f rotations = rotate.getValue() ? RotationUtility.calculate(crystal) : null;
        if (rotations != null) {
            sendRotation(rotations);
        }
        PacketUtility.attack(crystal, false);
    }

    private void sendRotation(Vector2f rotations) {
        if (rotations == null || mc.player.connection == null) {
            return;
        }

        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                rotations.x,
                rotations.y,
                mc.player.onGround(),
                mc.player.horizontalCollision
        ));
    }

    private boolean isMining(BlockPos pos) {
        return detectMining.getValue()
                && PacketMine.INSTANCE.isEnabled()
                && (pos.equals(PacketMine.targetPos) || pos.equals(PacketMine.secondPos));
    }

    private boolean isSurroundBlock(Item item) {
        return item == Items.OBSIDIAN || enderChest.getValue() && item == Items.ENDER_CHEST;
    }

    private void restoreInventorySwap(BlockSelection selection) {
        if (selection.inventorySlot() != InventoryUtility.NOT_FOUND) {
            InventoryUtility.moveInventorySlotToHotbar(selection.inventorySlot(), selection.hotbarSlot());
        }
    }

    private void restoreSelectedHotbarSlot(int originalHotbarSlot) {
        if (Inventory.isHotbarSlot(originalHotbarSlot)
                && InventoryUtility.getSelectedHotbarSlot() != originalHotbarSlot) {
            InventoryUtility.selectHotbarSlot(originalHotbarSlot, true);
        }
    }

    private record BlockSelection(int hotbarSlot, int inventorySlot) {
    }

    private record Placement(BlockPos clickedBlock, Direction face, Vec3 hitVec) {
    }

    public enum Page {
        General,
        Rotate,
        Check
    }
}
