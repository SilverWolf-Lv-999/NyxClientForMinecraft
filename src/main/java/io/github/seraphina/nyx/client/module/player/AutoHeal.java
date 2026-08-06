package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
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

    public final BoolValue goldHead = ValueBuild.boolSetting("goldhead", true,()-> mode.getValue() == Mode.NORMAL, this);
    public final BoolValue goldApple = ValueBuild.boolSetting("goldapple", true, ()-> mode.getValue() == Mode.NORMAL,this);
    public final BoolValue triggerWithBlocks = ValueBuild.boolSetting("triggerwithblocks", true, ()-> mode.getValue() == Mode.NORMAL,this);

    private ActiveUse activeUse;
    private VanillaUse vanillaUse;
    private boolean waitForUseRelease;

    public AutoHeal() {
    }

    @Override
    public void onDisable() {
        restoreVanillaUse();
        restoreActiveSlot();
    }

    @EventTarget
    public void onPreTick(TickEvent.Pre event) {
        if (!canRun()) {
            restoreVanillaUse();
            restoreActiveSlot();
            return;
        }

        if (mode.getValue() == Mode.VANILLA) {
            restoreActiveSlot();
            handleVanillaUse();
            return;
        }

        restoreVanillaUse();

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
            if (!canRun() || (vanillaUse != null && !mc.options.keyUse.isDown())) {
                restoreVanillaUse();
            }
            return;
        }

        restoreVanillaUse();

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
        if (vanillaUse != null || !mc.options.keyUse.isDown()) {
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

        if (InventoryUtility.swapInventorySlotWithHotbar(appleSlot, selectedSlot)) {
            vanillaUse = new VanillaUse(appleSlot, selectedSlot);
        }
    }

    private void restoreVanillaUse() {
        if (vanillaUse == null) {
            return;
        }

        VanillaUse activeVanillaUse = vanillaUse;
        vanillaUse = null;
        InventoryUtility.swapInventorySlotWithHotbar(activeVanillaUse.appleSlot, activeVanillaUse.hotbarSlot);
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

    private record VanillaUse(int appleSlot, int hotbarSlot) {
    }

    public enum Mode {
        NORMAL, VANILLA
    }

    private enum UseType {
        GOLD_HEAD,
        GOLD_APPLE
    }
}
