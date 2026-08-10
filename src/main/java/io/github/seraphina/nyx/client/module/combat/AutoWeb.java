package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.other.Target;
import io.github.seraphina.nyx.client.module.player.Blink;
import io.github.seraphina.nyx.client.utility.player.BlockPlacementUtility;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

@ModuleInfo(name = "nyxclient.module.autoweb.name", description = "nyxclient.module.autoweb.description", category = Category.COMBAT)
public final class AutoWeb extends Module {
    public static final AutoWeb INSTANCE = new AutoWeb();

    private static final double[] OFFSET_MULTIPLIERS = {0.0D, 1.0D, -1.0D};

    public final IntValue placeDelay = ValueBuild.intSetting("place delay", 50, 0, 500, 10, this);
    public final IntValue blocksPerTick = ValueBuild.intSetting("blocks per tick", 2, 1, 10, 1, this);
    public final IntValue predictTicks = ValueBuild.intSetting("predict ticks", 2, 0, 50, 1, this);
    public final IntValue maxWebs = ValueBuild.intSetting("max webs", 2, 1, 8, 1, this);
    public final DoubleValue offset = ValueBuild.doubleSetting("offset", 0.25D, 0.0D, 0.3D, 0.01D, this);
    public final DoubleValue placeRange = ValueBuild.doubleSetting("place range", 5.0D, 0.0D, 6.0D, 0.1D, this);
    public final DoubleValue targetRange = ValueBuild.doubleSetting("target range", 8.0D, 0.0D, 12.0D, 0.1D, this);
    public final BoolValue feet = ValueBuild.boolSetting("feet", true, this);
    public final BoolValue feetExtend = ValueBuild.boolSetting("feet extend", true, () -> feet.getValue(), this);
    public final BoolValue face = ValueBuild.boolSetting("face", true, this);
    public final BoolValue down = ValueBuild.boolSetting("down", true, this);
    public final BoolValue inventorySwap = ValueBuild.boolSetting("inventory swap", true, this);
    public final BoolValue usingPause = ValueBuild.boolSetting("using pause", true, this);
    public final BoolValue rotate = ValueBuild.boolSetting("rotate", true, this);
    public final BoolValue packetPlace = ValueBuild.boolSetting("packet place", true, this);

    private long lastPlaceTime;

    private AutoWeb() {
    }

    @Override
    public void onEnable() {
        lastPlaceTime = 0L;
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canRun()
                || Blink.INSTANCE.isEnabled()
                || usingPause.getValue() && mc.player.isUsingItem()
                || System.currentTimeMillis() - lastPlaceTime < placeDelay.getValue()) {
            return;
        }

        int originalHotbarSlot = InventoryUtility.getSelectedHotbarSlot();
        BlockSelection selection = selectWeb();
        if (selection == null) {
            return;
        }

        try {
            int placed = 0;
            Set<BlockPos> attempted = new LinkedHashSet<>();
            double targetRangeSqr = targetRange.getValue() * targetRange.getValue();

            for (Player target : mc.level.players()) {
                if (target == mc.player
                        || target.isSpectator()
                        || !Target.isTarget(target)
                        || mc.player.distanceToSqr(target) > targetRangeSqr) {
                    continue;
                }

                int webs = countWebs(target);
                for (BlockPos targetPos : collectTargetPositions(target)) {
                    if (placed >= blocksPerTick.getValue() || webs >= maxWebs.getValue()) {
                        break;
                    }

                    if (!attempted.add(targetPos)
                            || !new AABB(targetPos).intersects(target.getBoundingBox())
                            || !BlockPlacementUtility.canPlace(targetPos, false)) {
                        continue;
                    }

                    BlockPlacementUtility.Placement placement = BlockPlacementUtility.findPlacement(
                            targetPos,
                            placeRange.getValue()
                    );
                    if (placement != null && BlockPlacementUtility.placeFromHotbar(
                            selection.hotbarSlot(),
                            placement,
                            rotate.getValue(),
                            packetPlace.getValue()
                    )) {
                        placed++;
                        webs++;
                        lastPlaceTime = System.currentTimeMillis();
                    }
                }

                if (placed >= blocksPerTick.getValue()) {
                    break;
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

    private BlockSelection selectWeb() {
        int hotbarSlot = InventoryUtility.findHotbarSlot(Items.COBWEB);
        if (Inventory.isHotbarSlot(hotbarSlot)) {
            return new BlockSelection(hotbarSlot, InventoryUtility.NOT_FOUND);
        }

        if (!inventorySwap.getValue() || InventoryUtility.hasCarriedStack()) {
            return null;
        }

        int inventorySlot = InventoryUtility.findInventorySlot(Items.COBWEB);
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!InventoryUtility.isMainInventorySlot(inventorySlot) || !Inventory.isHotbarSlot(selectedSlot)) {
            return null;
        }
        if (!InventoryUtility.moveInventorySlotToHotbar(inventorySlot, selectedSlot)) {
            return null;
        }

        return new BlockSelection(selectedSlot, inventorySlot);
    }

    private Set<BlockPos> collectTargetPositions(Player target) {
        Vec3 predicted = target.position().add(target.getDeltaMovement().scale(predictTicks.getValue()));
        Set<BlockPos> positions = new LinkedHashSet<>();

        if (feet.getValue()) {
            positions.add(BlockPos.containing(predicted));
        }
        if (down.getValue()) {
            positions.add(BlockPos.containing(predicted.x, predicted.y - 0.8D, predicted.z));
        }
        if (feetExtend.getValue()) {
            addOffsetPositions(positions, predicted, 0.0D);
        }
        if (face.getValue()) {
            addOffsetPositions(positions, predicted, 1.1D);
        }

        return positions;
    }

    private void addOffsetPositions(Set<BlockPos> positions, Vec3 targetPos, double yOffset) {
        for (double xMultiplier : OFFSET_MULTIPLIERS) {
            for (double zMultiplier : OFFSET_MULTIPLIERS) {
                positions.add(BlockPos.containing(
                        targetPos.x + xMultiplier * offset.getValue(),
                        targetPos.y + yOffset,
                        targetPos.z + zMultiplier * offset.getValue()
                ));
            }
        }
    }

    private int countWebs(Player target) {
        int webs = 0;
        Vec3 predicted = target.position().add(target.getDeltaMovement().scale(predictTicks.getValue()));
        for (double yOffset : new double[]{-1.0D, 0.0D, 1.0D}) {
            for (double xMultiplier : OFFSET_MULTIPLIERS) {
                for (double zMultiplier : OFFSET_MULTIPLIERS) {
                    BlockPos pos = BlockPos.containing(
                            predicted.x + xMultiplier * offset.getValue(),
                            predicted.y + yOffset,
                            predicted.z + zMultiplier * offset.getValue()
                    );
                    if (mc.level.getBlockState(pos).is(Blocks.COBWEB)
                            && new AABB(pos).intersects(target.getBoundingBox())) {
                        webs++;
                    }
                }
            }
        }
        return webs;
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
