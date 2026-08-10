package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.other.Target;
import io.github.seraphina.nyx.client.module.player.Blink;
import io.github.seraphina.nyx.client.module.player.PacketMine;
import io.github.seraphina.nyx.client.utility.player.BlockPlacementUtility;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.Comparator;

@ModuleInfo(name = "nyxclient.module.autopush.name", description = "nyxclient.module.autopush.description", category = Category.COMBAT)
public final class AutoPush extends Module {
    public static final AutoPush INSTANCE = new AutoPush();

    public final BoolValue torch = ValueBuild.boolSetting("torch", false, this);
    public final BoolValue rotate = ValueBuild.boolSetting("rotate", true, this);
    public final BoolValue packetPlace = ValueBuild.boolSetting("packet place", true, this);
    public final BoolValue usingPause = ValueBuild.boolSetting("using pause", true, this);
    public final BoolValue selfGround = ValueBuild.boolSetting("self ground", true, this);
    public final BoolValue targetGround = ValueBuild.boolSetting("target ground", true, this);
    public final IntValue placeDelay = ValueBuild.intSetting("place delay", 100, 0, 1000, 10, this);
    public final DoubleValue targetRange = ValueBuild.doubleSetting("target range", 5.0D, 0.0D, 12.0D, 0.1D, this);
    public final DoubleValue placeRange = ValueBuild.doubleSetting("place range", 5.0D, 0.0D, 6.0D, 0.1D, this);
    public final BoolValue inventorySwap = ValueBuild.boolSetting("inventory swap", true, this);
    public final BoolValue minePower = ValueBuild.boolSetting("mine power", true, this);
    public final BoolValue autoDisable = ValueBuild.boolSetting("auto disable", false, this);

    private long lastPlaceTime;

    private AutoPush() {
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
                || selfGround.getValue() && !mc.player.onGround()
                || System.currentTimeMillis() - lastPlaceTime < placeDelay.getValue()) {
            return;
        }

        Player target = findTarget();
        if (target == null) {
            return;
        }

        for (Direction sideFromTarget : Direction.Plane.HORIZONTAL) {
            if (tryPush(target, sideFromTarget)) {
                return;
            }
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

    private Player findTarget() {
        double maxDistanceSqr = targetRange.getValue() * targetRange.getValue();
        return mc.level.players().stream()
                .filter(player -> player != mc.player)
                .filter(player -> !player.isSpectator())
                .filter(Target::isTarget)
                .filter(player -> !targetGround.getValue() || player.onGround())
                .filter(player -> mc.player.distanceToSqr(player) <= maxDistanceSqr)
                .min(Comparator.comparingDouble(mc.player::distanceToSqr))
                .orElse(null);
    }

    private boolean tryPush(Player target, Direction sideFromTarget) {
        BlockPos pistonPos = target.blockPosition().relative(sideFromTarget);
        Direction pistonFacing = sideFromTarget.getOpposite();
        BlockState pistonState = mc.level.getBlockState(pistonPos);
        if (pistonState.is(Blocks.PISTON) || pistonState.is(Blocks.STICKY_PISTON)) {
            if (pistonState.getValue(PistonBaseBlock.FACING) != pistonFacing) {
                return false;
            }
        } else if (!BlockPlacementUtility.canPlace(pistonPos, false)) {
            return false;
        }

        boolean pistonExists = pistonState.is(Blocks.PISTON) || pistonState.is(Blocks.STICKY_PISTON);
        PowerPlacement power = findPowerPlacement(pistonPos, pistonFacing, !pistonExists);
        if (power == null) {
            return false;
        }

        boolean changed = false;
        if (!pistonExists) {
            BlockPlacementUtility.Placement placement = BlockPlacementUtility.findPlacement(pistonPos, placeRange.getValue());
            if (placement == null) {
                return false;
            }

            Vector2f pistonRotations = RotationUtility.calculate(
                    Vec3.atCenterOf(pistonPos.relative(pistonFacing.getOpposite()))
            );
            if (!placeItem(Items.PISTON, placement, pistonRotations)) {
                return false;
            }
            changed = true;
        }

        if (!power.alreadyPowered()) {
            Vector2f powerRotations = rotate.getValue() ? RotationUtility.calculate(power.placement().hitVec()) : null;
            if (!placeItem(power.item(), power.placement(), powerRotations)) {
                return changed;
            }
            changed = true;
        }

        if (changed) {
            lastPlaceTime = System.currentTimeMillis();
            if (minePower.getValue() && PacketMine.INSTANCE.isEnabled() && !power.alreadyPowered()) {
                PacketMine.INSTANCE.mine(power.pos());
            }
            if (autoDisable.getValue()) {
                setEnabled(false);
            }
        }

        return changed;
    }

    private PowerPlacement findPowerPlacement(BlockPos pistonPos, Direction pistonFacing, boolean allowPistonSupport) {
        Item powerItem = torch.getValue() ? Items.REDSTONE_TORCH : Items.REDSTONE_BLOCK;
        for (Direction direction : Direction.values()) {
            if (direction == pistonFacing || torch.getValue() && direction == Direction.UP) {
                continue;
            }

            BlockPos powerPos = pistonPos.relative(direction);
            BlockState powerState = mc.level.getBlockState(powerPos);
            if (powerState.is(Blocks.REDSTONE_BLOCK) || powerState.is(Blocks.REDSTONE_TORCH)) {
                return new PowerPlacement(powerItem, powerPos, null, true);
            }
            if (!BlockPlacementUtility.canPlace(powerPos, false)) {
                continue;
            }

            BlockPlacementUtility.Placement placement = BlockPlacementUtility.findPlacement(powerPos, placeRange.getValue());
            if (placement == null && allowPistonSupport) {
                placement = findPistonSupportedPlacement(pistonPos, direction);
            }
            if (placement != null) {
                return new PowerPlacement(powerItem, powerPos, placement, false);
            }
        }

        return null;
    }

    private BlockPlacementUtility.Placement findPistonSupportedPlacement(BlockPos pistonPos, Direction face) {
        Vec3 hitVec = Vec3.atCenterOf(pistonPos).add(
                face.getStepX() * 0.5D,
                face.getStepY() * 0.5D,
                face.getStepZ() * 0.5D
        );
        if (mc.player.getEyePosition().distanceTo(hitVec) > placeRange.getValue()) {
            return null;
        }

        return new BlockPlacementUtility.Placement(pistonPos, face, hitVec);
    }

    private boolean placeItem(Item item, BlockPlacementUtility.Placement placement, Vector2f rotations) {
        int originalHotbarSlot = InventoryUtility.getSelectedHotbarSlot();
        BlockSelection selection = selectItem(item);
        if (selection == null) {
            return false;
        }

        try {
            return BlockPlacementUtility.placeFromHotbar(
                    selection.hotbarSlot(),
                    placement,
                    rotations,
                    packetPlace.getValue()
            );
        } finally {
            restoreInventorySwap(selection);
            restoreSelectedHotbarSlot(originalHotbarSlot);
        }
    }

    private BlockSelection selectItem(Item item) {
        int hotbarSlot = InventoryUtility.findHotbarSlot(item);
        if (Inventory.isHotbarSlot(hotbarSlot)) {
            return new BlockSelection(hotbarSlot, InventoryUtility.NOT_FOUND);
        }

        if (!inventorySwap.getValue() || InventoryUtility.hasCarriedStack()) {
            return null;
        }

        int inventorySlot = InventoryUtility.findInventorySlot(item);
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!InventoryUtility.isMainInventorySlot(inventorySlot) || !Inventory.isHotbarSlot(selectedSlot)) {
            return null;
        }
        if (!InventoryUtility.moveInventorySlotToHotbar(inventorySlot, selectedSlot)) {
            return null;
        }

        return new BlockSelection(selectedSlot, inventorySlot);
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

    private record PowerPlacement(
            Item item,
            BlockPos pos,
            BlockPlacementUtility.Placement placement,
            boolean alreadyPowered
    ) {
    }
}
