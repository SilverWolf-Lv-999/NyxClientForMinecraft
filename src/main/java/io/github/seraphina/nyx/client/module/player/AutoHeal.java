package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

@ModuleInfo(name = "nyxclient.module.autoheal.name", description = "nyxclient.module.autoheal.description", category = Category.PLAYER)
public class AutoHeal extends Module {
    public static final AutoHeal INSTANCE = new AutoHeal();

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("Mode", Mode.NORMAL, this);

    public final BoolValue noStartOnUse = ValueBuild.boolSetting("No start on use", false, ()-> mode.getValue() == Mode.VANILLA, this);

    public final BoolValue onUseStopModule = ValueBuild.boolSetting("On use stop module", false, ()-> mode.getValue() == Mode.VANILLA, this);
    public final BoolValue openInv = ValueBuild.boolSetting("Open Inv", false, ()-> mode.getValue() == Mode.VANILLA, this);
    public final BoolValue noMove = ValueBuild.boolSetting("No move", false, ()-> mode.getValue() == Mode.VANILLA, this);
    public final BoolValue afterOffHand = ValueBuild.boolSetting("afterOffHand", false, ()-> mode.getValue() == Mode.VANILLA, this);
    public final IntValue noMoveDelay = ValueBuild.intSetting("No move delay", 2, 1, 20, 1, noMove::getValue, this);

    public final BoolValue goldHead = ValueBuild.boolSetting("goldhead", true,()-> mode.getValue() == Mode.NORMAL, this);
    public final BoolValue goldApple = ValueBuild.boolSetting("goldapple", true, ()-> mode.getValue() == Mode.NORMAL,this);
    public final BoolValue triggerWithBlocks = ValueBuild.boolSetting("triggerwithblocks", true, ()-> mode.getValue() == Mode.NORMAL,this);

    private ActiveUse activeUse;
    private VanillaUse vanillaUse;
    private boolean waitForUseRelease;
    private int noMoveTicks;

    public AutoHeal() {
    }

    @Override
    public void onDisable() {
        restoreVanillaUseImmediately();
        restoreActiveSlot();
        noMoveTicks = 0;
    }

    @EventTarget
    public void onPreTick(TickEvent.Pre event) {
        if (!canRun()) {
            restoreVanillaUseImmediately();
            restoreActiveSlot();
            noMoveTicks = 0;
            return;
        }

        if (mode.getValue() == Mode.VANILLA) {
            restoreActiveSlot();
            handleVanillaUse();
            return;
        }

        restoreVanillaUseImmediately();

        boolean comboDown = isUseComboDown();
        if (!mc.options.keyUse.isDown()) {
            waitForUseRelease = false;
        }

        if (!comboDown || activeUse != null || waitForUseRelease) {
            return;
        }

        if (!triggerWithBlocks.getValue() && isHoldingBlock()) {
            return;
        }

        if (goldHead.getValue()) {
            int slot = InventoryUtility.findHotbarSlot(this::isGoldHead);
            if (slot != InventoryUtility.NOT_FOUND) {
                beginUse(UseType.GOLD_HEAD, slot);
                return;
            }
        }

        if (goldApple.getValue()) {
            int slot = InventoryUtility.findHotbarSlot(this::isGoldApple);
            if (slot != InventoryUtility.NOT_FOUND) {
                beginUse(UseType.GOLD_APPLE, slot);
            }
        }
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (mode.getValue() == Mode.VANILLA) {
            restoreActiveSlot();
            if (!canRun()) {
                restoreVanillaUseImmediately();
            } else if (vanillaUse != null
                    && vanillaUse.phase == VanillaUsePhase.ACTIVE
                    && !mc.options.keyUse.isDown()) {
                beginVanillaUseRestore();
            }
            return;
        }

        restoreVanillaUseImmediately();

        if (activeUse == null) {
            return;
        }

        if (!canRun()) {
            restoreActiveSlot();
            return;
        }

        if (activeUse.type == UseType.GOLD_APPLE) {
            if (!mc.options.keyUse.isDown()) {
                restoreActiveSlot();
            } else if (InventoryUtility.getSelectedHotbarSlot() != activeUse.hotbarSlot) {
                clearActiveUse();
            }
            return;
        }

        if (goldHeadWasConsumed()) {
            restoreActiveSlot(true);
        } else if (!isUseComboDown() && !mc.player.isUsingItem()) {
            restoreActiveSlot();
        } else if (InventoryUtility.getSelectedHotbarSlot() != activeUse.hotbarSlot) {
            clearActiveUse();
        }
    }

    @EventTarget(4)
    public void onMoveInput(MoveInputEvent event) {
        if (noMoveTicks <= 0 || mode.getValue() != Mode.VANILLA || !noMove.getValue()) {
            noMoveTicks = 0;
            return;
        }

        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
        event.setSprint(false);
        noMoveTicks--;
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator();
    }

    public boolean shouldPauseModulesForGoldenAppleUse() {
        return isEnabled()
                && mode.getValue() == Mode.VANILLA
                && onUseStopModule.getValue()
                && vanillaUse != null
                && mc.options.keyUse.isDown()
                && isGoldApple(InventoryUtility.getStack(vanillaUse.hotbarSlot));
    }

    private boolean isUseComboDown() {
        return mc.options.keyShift.isDown() && mc.options.keyUse.isDown();
    }

    private boolean isHoldingBlock() {
        return InventoryUtility.getSelectedStack().getItem() instanceof BlockItem;
    }

    private void handleVanillaUse() {
        if (vanillaUse != null) {
            continueVanillaUse();
            return;
        }

        if (!mc.options.keyUse.isDown()) {
            return;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(selectedSlot)) {
            return;
        }

        ItemStack selectedStack = InventoryUtility.getStack(selectedSlot);
        if (isGoldApple(selectedStack)
                || (noStartOnUse.getValue() && selectedStack.getUseDuration(mc.player) > 0)) {
            return;
        }

        int appleSlot = InventoryUtility.findInventorySlot(this::isGoldApple);
        if (appleSlot == InventoryUtility.NOT_FOUND || appleSlot == selectedSlot) {
            return;
        }

        boolean useOpenInventoryPackets = openInv.getValue();
        boolean useOffhandIntermediate = afterOffHand.getValue();
        if (!useOffhandIntermediate) {
            if (swapVanillaUseSlotWithHotbar(appleSlot, selectedSlot, useOpenInventoryPackets)) {
                vanillaUse = new VanillaUse(
                        appleSlot,
                        selectedSlot,
                        useOpenInventoryPackets,
                        false,
                        VanillaUsePhase.ACTIVE
                );
            }
            return;
        }

        if (swapVanillaUseSlotWithOffhand(appleSlot, useOpenInventoryPackets)) {
            vanillaUse = new VanillaUse(
                    appleSlot,
                    selectedSlot,
                    useOpenInventoryPackets,
                    true,
                    VanillaUsePhase.MOVING_TO_MAINHAND
            );
        }
    }

    private void continueVanillaUse() {
        VanillaUse activeVanillaUse = vanillaUse;
        if (activeVanillaUse.phase == VanillaUsePhase.MOVING_TO_MAINHAND) {
            if (!mc.options.keyUse.isDown()) {
                if (swapVanillaUseSlotWithOffhand(
                        activeVanillaUse.appleSlot,
                        activeVanillaUse.useOpenInventoryPackets
                )) {
                    vanillaUse = null;
                }
                return;
            }

            if (swapVanillaUseSlotWithOffhand(
                    activeVanillaUse.hotbarSlot,
                    activeVanillaUse.useOpenInventoryPackets
            )) {
                vanillaUse = activeVanillaUse.withPhase(VanillaUsePhase.ACTIVE);
            }
            return;
        }

        if (activeVanillaUse.phase == VanillaUsePhase.RESTORING_TO_INVENTORY
                && swapVanillaUseSlotWithOffhand(
                        activeVanillaUse.appleSlot,
                        activeVanillaUse.useOpenInventoryPackets
                )) {
            vanillaUse = null;
        }
    }

    private void beginVanillaUseRestore() {
        if (vanillaUse == null) {
            return;
        }

        VanillaUse activeVanillaUse = vanillaUse;
        if (!activeVanillaUse.useOffhandIntermediate) {
            if (swapVanillaUseSlotWithHotbar(
                    activeVanillaUse.appleSlot,
                    activeVanillaUse.hotbarSlot,
                    activeVanillaUse.useOpenInventoryPackets
            )) {
                vanillaUse = null;
            }
            return;
        }

        if (swapVanillaUseSlotWithOffhand(
                activeVanillaUse.hotbarSlot,
                activeVanillaUse.useOpenInventoryPackets
        )) {
            vanillaUse = activeVanillaUse.withPhase(VanillaUsePhase.RESTORING_TO_INVENTORY);
        }
    }

    private void restoreVanillaUseImmediately() {
        if (vanillaUse == null) {
            return;
        }

        VanillaUse activeVanillaUse = vanillaUse;
        vanillaUse = null;
        if (!activeVanillaUse.useOffhandIntermediate) {
            swapVanillaUseSlotWithHotbar(
                    activeVanillaUse.appleSlot,
                    activeVanillaUse.hotbarSlot,
                    activeVanillaUse.useOpenInventoryPackets
            );
            return;
        }

        if (activeVanillaUse.phase != VanillaUsePhase.MOVING_TO_MAINHAND) {
            swapVanillaUseSlotWithOffhand(
                    activeVanillaUse.hotbarSlot,
                    activeVanillaUse.useOpenInventoryPackets
            );
        }
        swapVanillaUseSlotWithOffhand(
                activeVanillaUse.appleSlot,
                activeVanillaUse.useOpenInventoryPackets
        );
    }

    private boolean swapVanillaUseSlotWithHotbar(
            int inventorySlot,
            int hotbarSlot,
            boolean useOpenInventoryPackets
    ) {
        return performVanillaUseSwap(
                useOpenInventoryPackets,
                () -> InventoryUtility.swapInventorySlotWithHotbar(inventorySlot, hotbarSlot)
        );
    }

    private boolean swapVanillaUseSlotWithOffhand(int inventorySlot, boolean useOpenInventoryPackets) {
        return performVanillaUseSwap(
                useOpenInventoryPackets,
                () -> InventoryUtility.swapInventorySlotWithOffhand(inventorySlot)
        );
    }

    private boolean performVanillaUseSwap(boolean useOpenInventoryPackets, VanillaUseSwap swap) {
        boolean swapped;
        if (!useOpenInventoryPackets) {
            swapped = swap.swap();
        } else {
            if (mc.player == null || mc.player.connection == null) {
                return false;
            }

            int containerId = mc.player.containerMenu.containerId;
            mc.player.sendOpenInventory();
            try {
                swapped = swap.swap();
            } finally {
                mc.player.connection.send(new ServerboundContainerClosePacket(containerId));
            }
        }

        if (swapped && noMove.getValue()) {
            noMoveTicks = noMoveDelay.getValue();
        }
        return swapped;
    }

    private void beginUse(UseType type, int hotbarSlot) {
        int previousSlot = InventoryUtility.getSelectedHotbarSlot();
        if (previousSlot == InventoryUtility.NOT_FOUND || !Inventory.isHotbarSlot(hotbarSlot)) {
            return;
        }

        ItemStack stack = InventoryUtility.getStack(hotbarSlot);
        activeUse = new ActiveUse(type, previousSlot, hotbarSlot, stack.getCount(), stack.copy());
        InventoryUtility.selectHotbarSlot(hotbarSlot, true);
    }

    private boolean goldHeadWasConsumed() {
        ItemStack currentStack = InventoryUtility.getStack(activeUse.hotbarSlot);
        return currentStack.isEmpty()
                || !isGoldHead(currentStack)
                || currentStack.getCount() < activeUse.initialCount
                || !ItemStack.isSameItemSameComponents(currentStack, activeUse.initialStack);
    }

    private void restoreActiveSlot() {
        restoreActiveSlot(false);
    }

    private void restoreActiveSlot(boolean waitForUseRelease) {
        if (activeUse == null) {
            return;
        }

        if (Inventory.isHotbarSlot(activeUse.previousSlot)) {
            InventoryUtility.selectHotbarSlot(activeUse.previousSlot, true);
        }
        clearActiveUse();
        this.waitForUseRelease = waitForUseRelease && mc.options.keyUse.isDown();
    }

    private void clearActiveUse() {
        activeUse = null;
    }

    private boolean isGoldApple(ItemStack stack) {
        return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private boolean isGoldHead(ItemStack stack) {
        if (!isHead(stack)) {
            return false;
        }

        String name = stack.getHoverName().getString();
        String normalized = normalizeName(name);
        return normalized.contains("goldhead")
                || normalized.contains("goldenhead")
                || normalized.contains("ghead")
                || name.contains("金头")
                || name.contains("黄金头")
                || name.contains("金头颅")
                || name.contains("黄金头颅");
    }

    private boolean isHead(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.PLAYER_HEAD
                || item == Items.SKELETON_SKULL
                || item == Items.WITHER_SKELETON_SKULL
                || item == Items.ZOMBIE_HEAD
                || item == Items.CREEPER_HEAD
                || item == Items.DRAGON_HEAD
                || item == Items.PIGLIN_HEAD;
    }

    private String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record ActiveUse(UseType type, int previousSlot, int hotbarSlot, int initialCount, ItemStack initialStack) {
    }

    private record VanillaUse(
            int appleSlot,
            int hotbarSlot,
            boolean useOpenInventoryPackets,
            boolean useOffhandIntermediate,
            VanillaUsePhase phase
    ) {
        private VanillaUse withPhase(VanillaUsePhase phase) {
            return new VanillaUse(
                    this.appleSlot,
                    this.hotbarSlot,
                    this.useOpenInventoryPackets,
                    this.useOffhandIntermediate,
                    phase
            );
        }
    }

    @FunctionalInterface
    private interface VanillaUseSwap {
        boolean swap();
    }

    public enum Mode {
        NORMAL, VANILLA
    }

    private enum VanillaUsePhase {
        MOVING_TO_MAINHAND,
        ACTIVE,
        RESTORING_TO_INVENTORY
    }

    private enum UseType {
        GOLD_HEAD,
        GOLD_APPLE
    }
}
