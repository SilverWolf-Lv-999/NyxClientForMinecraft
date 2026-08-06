package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.player.PacketUtility;
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

@ModuleInfo(name = "nyxclient.module.burrow.name", description = "nyxclient.module.burrow.description", category = Category.COMBAT)
public final class Burrow extends Module {
    public static final Burrow INSTANCE = new Burrow();

    private static final double[] PRE_PLACE_OFFSETS = {
            0.4199999868869781D,
            0.7531999805212017D,
            0.9999957640154541D,
            1.1661092609382138D
    };

    public final BoolValue autoDisable = ValueBuild.boolSetting("auto disable", true, this);
    public final IntValue delay = ValueBuild.intSetting(
            "delay",
            500,
            0,
            1000,
            25,
            () -> !autoDisable.getValue(),
            this
    );
    public final BoolValue enderChest = ValueBuild.boolSetting("ender chest", true, this);
    public final BoolValue inventorySwap = ValueBuild.boolSetting("inventory swap", true, this);
    public final BoolValue breakCrystals = ValueBuild.boolSetting("break crystals", true, this);
    public final BoolValue headFill = ValueBuild.boolSetting("head fill", false, this);
    public final BoolValue fillBelow = ValueBuild.boolSetting("fill below", true, this);
    public final BoolValue usingPause = ValueBuild.boolSetting("using pause", false, this);
    public final BoolValue packetPlace = ValueBuild.boolSetting("packet place", true, this);
    public final BoolValue rotate = ValueBuild.boolSetting("rotate", true, this);
    public final IntValue blocksPerTick = ValueBuild.intSetting("blocks per tick", 4, 1, 8, 1, this);
    public final EnumValue<LagMode> lagMode = ValueBuild.enumSetting("lag mode", LagMode.TROLL_HACK, this);
    public final DoubleValue smartHorizontal = ValueBuild.doubleSetting(
            "smart horizontal",
            3.0D,
            0.0D,
            10.0D,
            0.5D,
            () -> lagMode.is(LagMode.SMART),
            this
    );
    public final DoubleValue smartUp = ValueBuild.doubleSetting(
            "smart up",
            3.0D,
            0.0D,
            10.0D,
            0.5D,
            () -> lagMode.is(LagMode.SMART),
            this
    );
    public final DoubleValue smartDown = ValueBuild.doubleSetting(
            "smart down",
            3.0D,
            0.0D,
            10.0D,
            0.5D,
            () -> lagMode.is(LagMode.SMART),
            this
    );
    public final DoubleValue smartDistance = ValueBuild.doubleSetting(
            "smart distance",
            2.0D,
            0.0D,
            10.0D,
            0.5D,
            () -> lagMode.is(LagMode.SMART),
            this
    );

    private long lastPlaceTime;

    private Burrow() {
    }

    @Override
    public void onEnable() {
        lastPlaceTime = 0L;
    }

    @Override
    public void onDisable() {
        lastPlaceTime = 0L;
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canRun()) {
            return;
        }

        if (usingPause.getValue() && mc.player.isUsingItem()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!autoDisable.getValue() && now - lastPlaceTime < delay.getValue()) {
            return;
        }

        int originalHotbarSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(originalHotbarSlot)) {
            setEnabled(false);
            return;
        }

        BlockSelection selection = selectBlock();
        if (selection == null) {
            setEnabled(false);
            return;
        }

        List<BlockPos> targets = collectTargetPositions();
        List<Placement> placements = collectPlacements(targets);
        if (placements.isEmpty()) {
            restoreInventorySwap(selection);
            return;
        }

        try {
            if (breakCrystals.getValue()) {
                breakCrystals(targets);
            }

            sendPrePlaceMovement();

            int placed = 0;
            for (Placement placement : placements) {
                if (placed >= blocksPerTick.getValue()) {
                    break;
                }

                if (placeBlock(selection.hotbarSlot(), placement)) {
                    placed++;
                }
            }

            if (placed > 0) {
                lastPlaceTime = now;
                sendLagBack();
                if (autoDisable.getValue()) {
                    setEnabled(false);
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
                && mc.player.onGround()
                && !mc.player.isPassenger()
                && !mc.player.getAbilities().flying;
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

    private List<BlockPos> collectTargetPositions() {
        Set<BlockPos> targets = new LinkedHashSet<>();
        addCornerPositions(targets, 0.5D);

        if (fillBelow.getValue()) {
            addCornerPositions(targets, -1.0D);
        }
        if (headFill.getValue()) {
            addCornerPositions(targets, 1.5D);
        }

        return new ArrayList<>(targets);
    }

    private void addCornerPositions(Set<BlockPos> targets, double yOffset) {
        for (double xOffset : new double[]{-0.3D, 0.3D}) {
            for (double zOffset : new double[]{-0.3D, 0.3D}) {
                targets.add(BlockPos.containing(
                        mc.player.getX() + xOffset,
                        mc.player.getY() + yOffset,
                        mc.player.getZ() + zOffset
                ));
            }
        }
    }

    private List<Placement> collectPlacements(List<BlockPos> targets) {
        List<Placement> placements = new ArrayList<>();
        for (BlockPos target : targets) {
            if (!canPlace(target)) {
                continue;
            }

            ClickFace clickFace = findClickFace(target);
            if (clickFace != null) {
                placements.add(new Placement(target, clickFace));
            }
        }
        return placements;
    }

    private boolean canPlace(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.canBeReplaced()) {
            return false;
        }

        for (Entity entity : mc.level.getEntities(null, new AABB(pos))) {
            if (blocksPlacement(entity)) {
                return false;
            }
        }
        return true;
    }

    private boolean blocksPlacement(Entity entity) {
        if (entity == mc.player
                || !entity.isAlive()
                || entity instanceof ItemEntity
                || entity instanceof ExperienceOrb
                || entity instanceof Projectile) {
            return false;
        }

        return !(entity instanceof EndCrystal) || !breakCrystals.getValue();
    }

    private ClickFace findClickFace(BlockPos target) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (neighborState.canBeReplaced() || !neighborState.blocksMotion()) {
                continue;
            }

            Direction face = direction.getOpposite();
            Vec3 hitVec = Vec3.atCenterOf(neighbor).add(
                    face.getStepX() * 0.5D,
                    face.getStepY() * 0.5D,
                    face.getStepZ() * 0.5D
            );
            return new ClickFace(neighbor, face, hitVec);
        }
        return null;
    }

    private void breakCrystals(List<BlockPos> targets) {
        Set<Integer> attackedEntityIds = new LinkedHashSet<>();
        for (BlockPos target : targets) {
            for (Entity entity : mc.level.getEntities(null, new AABB(target))) {
                if (!(entity instanceof EndCrystal crystal)
                        || !crystal.isAlive()
                        || !attackedEntityIds.add(crystal.getId())) {
                    continue;
                }

                attackCrystal(crystal);
            }
        }
    }

    private void attackCrystal(EndCrystal crystal) {
        Vector2f rotations = rotate.getValue() ? RotationUtility.calculate(crystal) : null;
        if (rotations != null) {
            sendRotation(rotations);
        }

        float previousYaw = mc.player.getYRot();
        float previousPitch = mc.player.getXRot();
        if (rotations != null) {
            mc.player.setYRot(rotations.x);
            mc.player.setXRot(rotations.y);
        }

        try {
            mc.gameMode.attack(mc.player, crystal);
            mc.player.swing(InteractionHand.MAIN_HAND);
        } finally {
            mc.player.setYRot(previousYaw);
            mc.player.setXRot(previousPitch);
        }
    }

    private void sendPrePlaceMovement() {
        for (double offset : PRE_PLACE_OFFSETS) {
            sendPosition(mc.player.getX(), mc.player.getY() + offset, mc.player.getZ());
        }
    }

    private boolean placeBlock(int hotbarSlot, Placement placement) {
        if (!Inventory.isHotbarSlot(hotbarSlot) || !isBurrowBlock(InventoryUtility.getStack(hotbarSlot).getItem())) {
            return false;
        }

        BlockHitResult hitResult = new BlockHitResult(
                placement.clickFace().hitVec(),
                placement.clickFace().face(),
                placement.clickFace().blockPos(),
                false
        );
        Vector2f rotations = rotate.getValue() ? RotationUtility.calculate(placement.clickFace().hitVec()) : null;
        if (rotations != null) {
            sendRotation(rotations);
        }

        if (packetPlace.getValue()) {
            return PacketUtility.useHotbarItemOnBlock(hotbarSlot, hitResult);
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(selectedSlot) || !InventoryUtility.selectHotbarSlot(hotbarSlot, true)) {
            return false;
        }

        InteractionResult result = useItemOnWithRotations(rotations, hitResult);
        if (result.consumesAction()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        return result.consumesAction();
    }

    private boolean isBurrowBlock(Item item) {
        return item == Items.OBSIDIAN || enderChest.getValue() && item == Items.ENDER_CHEST;
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

    private void sendLagBack() {
        switch (lagMode.getValue()) {
            case NONE -> {
            }
            case SMART -> sendSmartLagBack();
            case INVALID -> {
                for (int i = 0; i < 20; i++) {
                    sendPosition(mc.player.getX(), mc.player.getY() + 1337.0D, mc.player.getZ());
                }
            }
            case TROLL_HACK -> sendPosition(mc.player.getX(), mc.player.getY() + 2.3400880035762786D, mc.player.getZ());
            case NORMAL -> sendPosition(mc.player.getX(), mc.player.getY() + 1.9D, mc.player.getZ());
            case TO_VOID -> sendPosition(mc.player.getX(), -70.0D, mc.player.getZ());
            case TO_VOID_2 -> sendPosition(mc.player.getX(), -7.0D, mc.player.getZ());
            case FLY -> {
                sendPosition(mc.player.getX(), mc.player.getY() + 1.16610926093821D, mc.player.getZ());
                sendPosition(mc.player.getX(), mc.player.getY() + 1.170005801788139D, mc.player.getZ());
                sendPosition(mc.player.getX(), mc.player.getY() + 1.2426308013947485D, mc.player.getZ());
                sendPosition(mc.player.getX(), mc.player.getY() + 2.3400880035762786D, mc.player.getZ());
                sendPosition(mc.player.getX(), mc.player.getY() + 2.6400880035762786D, mc.player.getZ());
            }
            case GLIDE -> {
                sendPosition(mc.player.getX(), mc.player.getY() + 1.0001D, mc.player.getZ());
                sendPosition(mc.player.getX(), mc.player.getY() + 1.0405D, mc.player.getZ());
                sendPosition(mc.player.getX(), mc.player.getY() + 1.0802D, mc.player.getZ());
                sendPosition(mc.player.getX(), mc.player.getY() + 1.1027D, mc.player.getZ());
            }
            case ROTATION -> {
                mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(-180.0F, -90.0F, false, mc.player.horizontalCollision));
                mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(180.0F, 90.0F, false, mc.player.horizontalCollision));
            }
        }
    }

    private void sendSmartLagBack() {
        BlockPos bestPosition = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        BlockPos origin = mc.player.blockPosition();
        int horizontal = (int) Math.ceil(smartHorizontal.getValue());
        int upward = (int) Math.ceil(smartUp.getValue());
        int downward = (int) Math.ceil(smartDown.getValue());
        double minimumDistanceSqr = smartDistance.getValue() * smartDistance.getValue();

        for (int x = -horizontal; x <= horizontal; x++) {
            for (int z = -horizontal; z <= horizontal; z++) {
                for (int y = -downward; y <= upward; y++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    Vec3 candidatePosition = Vec3.atCenterOf(candidate);
                    double distanceSqr = mc.player.position().distanceToSqr(candidatePosition);
                    if (distanceSqr < minimumDistanceSqr || distanceSqr >= bestDistanceSqr || !canMoveTo(candidate)) {
                        continue;
                    }

                    bestDistanceSqr = distanceSqr;
                    bestPosition = candidate;
                }
            }
        }

        if (bestPosition != null) {
            sendPosition(bestPosition.getX() + 0.5D, bestPosition.getY(), bestPosition.getZ() + 0.5D);
        }
    }

    private boolean canMoveTo(BlockPos pos) {
        return mc.level.getBlockState(pos).isAir()
                && mc.level.getBlockState(pos.above()).isAir()
                && mc.level.noCollision(mc.player, new AABB(pos))
                && mc.level.getEntities(null, new AABB(pos)).isEmpty();
    }

    private void sendRotation(Vector2f rotations) {
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                rotations.x,
                rotations.y,
                mc.player.onGround(),
                mc.player.horizontalCollision
        ));
    }

    private void sendPosition(double x, double y, double z) {
        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                x,
                y,
                z,
                false,
                mc.player.horizontalCollision
        ));
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

    private record ClickFace(BlockPos blockPos, Direction face, Vec3 hitVec) {
    }

    private record Placement(BlockPos target, ClickFace clickFace) {
    }

    public enum LagMode {
        NONE,
        SMART,
        INVALID,
        TROLL_HACK,
        TO_VOID,
        TO_VOID_2,
        NORMAL,
        ROTATION,
        FLY,
        GLIDE
    }
}
