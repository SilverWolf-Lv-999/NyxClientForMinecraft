package io.github.seraphina.nyx.client.module.movement;

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
import io.github.seraphina.nyx.client.utility.player.PlayerUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashSet;
import java.util.Set;

@ModuleInfo(name = "nyxclient.module.flatten.name", description = "nyxclient.module.flatten.description", category = Category.MOVEMENT)
public final class Flatten extends Module {
    public static final Flatten INSTANCE = new Flatten();

    private static final double PLACE_RANGE = 5.0D;

    public final BoolValue rotate = ValueBuild.boolSetting("rotate", true, this);
    public final BoolValue detectMining = ValueBuild.boolSetting("detect mining", true, this);
    public final BoolValue inventorySwap = ValueBuild.boolSetting("inventory swap", true, this);
    public final BoolValue usingPause = ValueBuild.boolSetting("using pause", true, this);
    public final BoolValue cover = ValueBuild.boolSetting("cover", false, this);
    public final IntValue blocksPerTick = ValueBuild.intSetting("blocks per tick", 2, 1, 8, 1, this);
    public final IntValue delay = ValueBuild.intSetting("delay", 100, 0, 1000, 10, this);

    private long lastPlaceTime;

    private Flatten() {
    }

    @Override
    public void onEnable() {
        lastPlaceTime = 0L;
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canRun()
                || Blink.INSTANCE.isEnabled()
                || inventorySwap.getValue() && !InventoryUtility.isOpenInventory()
                || usingPause.getValue() && mc.player.isUsingItem()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastPlaceTime < delay.getValue() || !PlayerUtility.isInsideBlock()) {
            return;
        }

        Set<BlockPos> targets = collectTargets();
        if (targets.stream().noneMatch(this::canPlace)) {
            return;
        }

        int originalHotbarSlot = InventoryUtility.getSelectedHotbarSlot();
        BlockSelection selection = selectObsidian();
        if (selection == null) {
            return;
        }

        try {
            breakCrystals(targets);

            int placed = 0;
            for (BlockPos target : targets) {
                if (placed >= blocksPerTick.getValue() || !canPlace(target)) {
                    continue;
                }

                BlockPlacementUtility.Placement placement = BlockPlacementUtility.findPlacement(target, PLACE_RANGE);
                if (placement != null && BlockPlacementUtility.placeFromHotbar(
                        selection.hotbarSlot(),
                        placement,
                        rotate.getValue(),
                        false
                )) {
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
                && mc.player.onGround()
                && !mc.player.isSpectator()
                && !mc.player.isPassenger()
                && !mc.player.getAbilities().flying;
    }

    private Set<BlockPos> collectTargets() {
        Set<BlockPos> targets = new LinkedHashSet<>();
        for (double xOffset : new double[]{-0.5D, 0.5D}) {
            for (double zOffset : new double[]{-0.5D, 0.5D}) {
                targets.add(BlockPos.containing(
                        mc.player.getX() + xOffset,
                        mc.player.getY() + 0.5D,
                        mc.player.getZ() + zOffset
                ).below());
            }
        }
        return targets;
    }

    private boolean canPlace(BlockPos pos) {
        return !isMining(pos)
                && (!cover.getValue() || !mc.level.getBlockState(pos.above()).isAir())
                && BlockPlacementUtility.canPlace(pos, true)
                && BlockPlacementUtility.findPlacement(pos, PLACE_RANGE) != null;
    }

    private boolean isMining(BlockPos pos) {
        return detectMining.getValue()
                && PacketMine.INSTANCE.isEnabled()
                && (pos.equals(PacketMine.targetPos) || pos.equals(PacketMine.secondPos));
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
            for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(target))) {
                if (crystal.isAlive() && attackedEntityIds.add(crystal.getId())) {
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
