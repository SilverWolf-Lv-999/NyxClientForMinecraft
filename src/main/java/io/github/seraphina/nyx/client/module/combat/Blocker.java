package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.player.Blink;
import io.github.seraphina.nyx.client.module.player.PacketMine;
import io.github.seraphina.nyx.client.utility.player.BlockPlacementUtility;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.player.PacketUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashSet;
import java.util.Set;

@ModuleInfo(name = "nyxclient.module.blocker.name", description = "nyxclient.module.blocker.description", category = Category.COMBAT)
public final class Blocker extends Module {
    public static final Blocker INSTANCE = new Blocker();

    private static final double PLACE_RANGE = 5.0D;

    public final IntValue placeDelay = ValueBuild.intSetting("place delay", 50, 0, 500, 10, this);
    public final IntValue blocksPerTick = ValueBuild.intSetting("blocks per tick", 1, 1, 8, 1, this);
    public final BoolValue rotate = ValueBuild.boolSetting("rotate", true, this);
    public final BoolValue packetPlace = ValueBuild.boolSetting("packet place", true, this);
    public final BoolValue breakCrystals = ValueBuild.boolSetting("break crystals", true, this);
    public final BoolValue inventorySwap = ValueBuild.boolSetting("inventory swap", true, this);
    public final BoolValue face = ValueBuild.boolSetting("face", true, this);
    public final BoolValue faceUp = ValueBuild.boolSetting("face up", false, () -> face.getValue(), this);
    public final BoolValue feet = ValueBuild.boolSetting("feet", true, this);
    public final BoolValue extend = ValueBuild.boolSetting("extend", false, () -> feet.getValue(), this);
    public final BoolValue onlySurround = ValueBuild.boolSetting("only surround", true, () -> feet.getValue(), this);
    public final BoolValue burrow = ValueBuild.boolSetting("burrow", true, this);
    public final BoolValue inAirPause = ValueBuild.boolSetting("in air pause", false, this);
    public final BoolValue detectMining = ValueBuild.boolSetting("detect mining", true, this);
    public final BoolValue usingPause = ValueBuild.boolSetting("using pause", true, this);

    private long lastPlaceTime;

    private Blocker() {
    }

    @Override
    public void onEnable() {
        lastPlaceTime = 0L;
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canRun()
                || Blink.INSTANCE.isEnabled()
                || inAirPause.getValue() && !mc.player.onGround()
                || usingPause.getValue() && mc.player.isUsingItem()
                || System.currentTimeMillis() - lastPlaceTime < placeDelay.getValue()) {
            return;
        }

        Set<BlockPos> targets = collectThreatenedPositions();
        if (targets.isEmpty()) {
            return;
        }

        int originalHotbarSlot = InventoryUtility.getSelectedHotbarSlot();
        BlockSelection selection = selectObsidian();
        if (selection == null) {
            return;
        }

        try {
            if (breakCrystals.getValue()) {
                breakCrystals(targets);
            }

            int placed = 0;
            for (BlockPos target : targets) {
                if (placed >= blocksPerTick.getValue()
                        || !BlockPlacementUtility.canPlace(target, breakCrystals.getValue())) {
                    continue;
                }

                BlockPlacementUtility.Placement placement = BlockPlacementUtility.findPlacement(target, PLACE_RANGE);
                if (placement != null && BlockPlacementUtility.placeFromHotbar(
                        selection.hotbarSlot(),
                        placement,
                        rotate.getValue(),
                        packetPlace.getValue()
                )) {
                    placed++;
                    lastPlaceTime = System.currentTimeMillis();
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
                && !mc.player.isPassenger();
    }

    private Set<BlockPos> collectThreatenedPositions() {
        BlockPos playerPos = mc.player.blockPosition();
        Set<BlockPos> candidates = new LinkedHashSet<>();

        if (burrow.getValue()) {
            addThreatened(candidates, playerPos);
        }
        if (feet.getValue() && (!onlySurround.getValue() || Surround.INSTANCE.isEnabled())) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = playerPos.relative(direction);
                addThreatened(candidates, side);
                if (extend.getValue()) {
                    for (Direction perpendicular : Direction.Plane.HORIZONTAL) {
                        if (perpendicular != direction && perpendicular != direction.getOpposite()) {
                            addThreatened(candidates, side.relative(perpendicular));
                        }
                    }
                }
            }
        }
        if (face.getValue()) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos headSide = playerPos.above().relative(direction);
                addThreatened(candidates, headSide);
                if (faceUp.getValue()) {
                    addThreatened(candidates, headSide.above());
                }
            }
        }

        if (detectMining.getValue()) {
            addMinedPosition(candidates, PacketMine.targetPos);
            addMinedPosition(candidates, PacketMine.secondPos);
        }

        return candidates;
    }

    private void addThreatened(Set<BlockPos> candidates, BlockPos pos) {
        if (isMined(pos) || hasCrystalThreat(pos)) {
            candidates.add(pos);
        }
    }

    private void addMinedPosition(Set<BlockPos> candidates, BlockPos pos) {
        if (pos != null && isNearPlayer(pos)) {
            candidates.add(pos.immutable());
        }
    }

    private boolean isMined(BlockPos pos) {
        return detectMining.getValue() && (pos.equals(PacketMine.targetPos) || pos.equals(PacketMine.secondPos));
    }

    private boolean hasCrystalThreat(BlockPos pos) {
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(pos).inflate(1.5D))) {
            if (crystal.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearPlayer(BlockPos pos) {
        return mc.player.blockPosition().distSqr(pos) <= 16.0D;
    }

    private BlockSelection selectObsidian() {
        int hotbarSlot = InventoryUtility.findHotbarSlot(Items.OBSIDIAN);
        if (Inventory.isHotbarSlot(hotbarSlot)) {
            return new BlockSelection(hotbarSlot, InventoryUtility.NOT_FOUND);
        }

        if (!inventorySwap.getValue() || InventoryUtility.hasCarriedStack()) {
            return null;
        }

        int inventorySlot = InventoryUtility.findInventorySlot(Items.OBSIDIAN);
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!InventoryUtility.isMainInventorySlot(inventorySlot) || !Inventory.isHotbarSlot(selectedSlot)) {
            return null;
        }
        if (!InventoryUtility.moveInventorySlotToHotbar(inventorySlot, selectedSlot)) {
            return null;
        }

        return new BlockSelection(selectedSlot, inventorySlot);
    }

    private void breakCrystals(Set<BlockPos> targets) {
        Set<Integer> attackedEntityIds = new LinkedHashSet<>();
        for (BlockPos target : targets) {
            for (Entity entity : mc.level.getEntities(null, new AABB(target))) {
                if (entity instanceof EndCrystal crystal
                        && crystal.isAlive()
                        && attackedEntityIds.add(crystal.getId())) {
                    PacketUtility.attack(crystal, mc.player.isShiftKeyDown());
                }
            }
        }
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
}
