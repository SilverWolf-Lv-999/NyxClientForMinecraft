package io.github.seraphina.nyx.client.utility.player;

import io.github.seraphina.nyx.client.mixins.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;

public final class InventoryUtility {
    public static final int NOT_FOUND = Inventory.NOT_FOUND_INDEX;
    public static final int HOTBAR_START = 0;
    public static final int HOTBAR_END = Inventory.SELECTION_SIZE;
    public static final int MAIN_INVENTORY_START = Inventory.SELECTION_SIZE;
    public static final int MAIN_INVENTORY_END = Inventory.INVENTORY_SIZE;
    public static final int ARMOR_FEET_SLOT = EquipmentSlot.FEET.getIndex(Inventory.INVENTORY_SIZE);
    public static final int ARMOR_LEGS_SLOT = EquipmentSlot.LEGS.getIndex(Inventory.INVENTORY_SIZE);
    public static final int ARMOR_CHEST_SLOT = EquipmentSlot.CHEST.getIndex(Inventory.INVENTORY_SIZE);
    public static final int ARMOR_HEAD_SLOT = EquipmentSlot.HEAD.getIndex(Inventory.INVENTORY_SIZE);
    public static final int OFFHAND_SLOT = Inventory.SLOT_OFFHAND;
    public static final int BODY_ARMOR_SLOT = Inventory.SLOT_BODY_ARMOR;
    public static final int SADDLE_SLOT = Inventory.SLOT_SADDLE;
    public static final int LEFT_BUTTON = 0;
    public static final int RIGHT_BUTTON = 1;

    private static final Minecraft MC = Minecraft.getInstance();

    private InventoryUtility() {
    }

    public static boolean isOpenInventory() {
        return MC.screen instanceof InventoryScreen || MC.screen instanceof CreativeModeInventoryScreen;
    }

    public static boolean isOpenContainerScreen() {
        return MC.screen instanceof AbstractContainerScreen<?>;
    }

    public static boolean hasContainerOpen() {
        LocalPlayer player = MC.player;
        return player != null && player.hasContainerOpen();
    }

    public static boolean canSimulateInventoryAction() {
        return MC.player != null && MC.gameMode != null;
    }

    public static boolean openInventory() {
        if (MC.player == null || MC.gameMode == null) {
            return false;
        }

        if (isOpenInventory()) {
            return true;
        }

        if (MC.gameMode.isServerControlledInventory()) {
            MC.player.sendOpenInventory();
        } else {
            MC.getTutorial().onOpenInventory();
            MC.setScreen(new InventoryScreen(MC.player));
        }

        return true;
    }

    public static boolean closeInventoryScreen() {
        if (MC.screen == null) {
            return true;
        }

        if (MC.player != null && MC.screen instanceof AbstractContainerScreen<?>) {
            MC.player.closeContainer();
        } else {
            MC.setScreen(null);
        }

        return true;
    }

    public static Inventory inventory() {
        return MC.player == null ? null : MC.player.getInventory();
    }

    public static AbstractContainerMenu currentMenu() {
        return MC.player == null ? null : MC.player.containerMenu;
    }

    public static AbstractContainerMenu playerInventoryMenu() {
        return MC.player == null ? null : MC.player.inventoryMenu;
    }

    public static ItemStack getStack(int inventorySlot) {
        Inventory inventory = inventory();
        return inventory != null && isValidInventorySlot(inventorySlot) ? inventory.getItem(inventorySlot) : ItemStack.EMPTY;
    }

    public static ItemStack getMenuStack(int menuSlot) {
        Slot slot = getMenuSlot(menuSlot);
        return slot == null ? ItemStack.EMPTY : slot.getItem();
    }

    public static ItemStack getHandStack(InteractionHand hand) {
        Objects.requireNonNull(hand, "hand");
        return MC.player == null ? ItemStack.EMPTY : MC.player.getItemInHand(hand);
    }

    public static ItemStack getMainHandStack() {
        return getHandStack(InteractionHand.MAIN_HAND);
    }

    public static ItemStack getOffhandStack() {
        return getStack(OFFHAND_SLOT);
    }

    public static boolean hasCarriedStack() {
        AbstractContainerMenu menu = currentMenu();
        return menu != null && !menu.getCarried().isEmpty();
    }

    public static ItemStack getSelectedStack() {
        Inventory inventory = inventory();
        return inventory == null ? ItemStack.EMPTY : inventory.getSelectedItem();
    }

    public static int getSelectedHotbarSlot() {
        Inventory inventory = inventory();
        return inventory == null ? NOT_FOUND : inventory.getSelectedSlot();
    }

    public static boolean selectHotbarSlot(int hotbarSlot) {
        Inventory inventory = inventory();
        if (inventory == null || !Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        inventory.setSelectedSlot(hotbarSlot);
        return true;
    }

    public static boolean selectHotbarSlot(int hotbarSlot, boolean syncImmediately) {
        if (!selectHotbarSlot(hotbarSlot)) {
            return false;
        }

        return !syncImmediately || syncSelectedSlot();
    }

    public static boolean syncSelectedSlot() {
        if (MC.player == null || MC.gameMode == null) {
            return false;
        }

        ((MultiPlayerGameModeAccessor) MC.gameMode).nyx$ensureHasSentCarriedItem();
        return true;
    }

    public static boolean useHotbarItem(int hotbarSlot) {
        return useHotbarItem(hotbarSlot, InteractionHand.MAIN_HAND);
    }

    public static boolean useHotbarItem(int hotbarSlot, InteractionHand hand) {
        Objects.requireNonNull(hand, "hand");
        if (MC.player == null || MC.gameMode == null || !Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        int previousSlot = getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(previousSlot)) {
            return false;
        }

        boolean changedSlot = previousSlot != hotbarSlot;
        if (changedSlot && !selectHotbarSlot(hotbarSlot, true)) {
            return false;
        }

        try {
            MC.gameMode.useItem(MC.player, hand);
        } finally {
            if (changedSlot) {
                selectHotbarSlot(previousSlot, true);
            }
        }

        return true;
    }

    public static boolean isValidInventorySlot(int inventorySlot) {
        Inventory inventory = inventory();
        return inventory != null && inventorySlot >= 0 && inventorySlot < inventory.getContainerSize();
    }

    public static boolean isHotbarSlot(int inventorySlot) {
        return Inventory.isHotbarSlot(inventorySlot);
    }

    public static boolean isMainInventorySlot(int inventorySlot) {
        return inventorySlot >= MAIN_INVENTORY_START && inventorySlot < MAIN_INVENTORY_END;
    }

    public static boolean isArmorSlot(int inventorySlot) {
        return inventorySlot == ARMOR_HEAD_SLOT
            || inventorySlot == ARMOR_CHEST_SLOT
            || inventorySlot == ARMOR_LEGS_SLOT
            || inventorySlot == ARMOR_FEET_SLOT;
    }

    public static boolean isEquipmentSlot(int inventorySlot) {
        return isArmorSlot(inventorySlot)
            || inventorySlot == OFFHAND_SLOT
            || inventorySlot == BODY_ARMOR_SLOT
            || inventorySlot == SADDLE_SLOT;
    }

    public static boolean isEmpty(int inventorySlot) {
        return getStack(inventorySlot).isEmpty();
    }

    public static boolean hasEmptySlot() {
        return findEmptySlot() != NOT_FOUND;
    }

    public static boolean hasEmptyHotbarSlot() {
        return findEmptyHotbarSlot() != NOT_FOUND;
    }

    public static boolean isInventoryFull() {
        Inventory inventory = inventory();
        return inventory != null && inventory.getFreeSlot() == NOT_FOUND;
    }

    public static int findEmptySlot() {
        Inventory inventory = inventory();
        return inventory == null ? NOT_FOUND : inventory.getFreeSlot();
    }

    public static int findEmptyHotbarSlot() {
        Inventory inventory = inventory();
        if (inventory == null) {
            return NOT_FOUND;
        }

        for (int slot = HOTBAR_START; slot < HOTBAR_END; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }

        return NOT_FOUND;
    }

    public static int findSlot(Item item) {
        Objects.requireNonNull(item, "item");
        return findSlot(stack -> stack.is(item));
    }

    public static int findInventorySlot(Item item) {
        Objects.requireNonNull(item, "item");
        return findInventorySlot(stack -> stack.is(item));
    }

    public static int findInventorySlot(Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        Inventory inventory = inventory();
        if (inventory == null) {
            return NOT_FOUND;
        }

        for (int slot = HOTBAR_START; slot < MAIN_INVENTORY_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return slot;
            }
        }

        return NOT_FOUND;
    }

    public static int findSlot(ItemStack target) {
        Objects.requireNonNull(target, "target");
        if (target.isEmpty()) {
            return NOT_FOUND;
        }

        return findSlot(stack -> ItemStack.isSameItemSameComponents(stack, target));
    }

    public static int findSlot(Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        Inventory inventory = inventory();
        if (inventory == null) {
            return NOT_FOUND;
        }

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return slot;
            }
        }

        return NOT_FOUND;
    }

    public static int findHotbarSlot(Item item) {
        Objects.requireNonNull(item, "item");
        return findHotbarSlot(stack -> stack.is(item));
    }

    public static int findHotbarSlot(Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        Inventory inventory = inventory();
        if (inventory == null) {
            return NOT_FOUND;
        }

        for (int slot = HOTBAR_START; slot < HOTBAR_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return slot;
            }
        }

        return NOT_FOUND;
    }

    public static int findBestSlot(Predicate<ItemStack> predicate) {
        int selectedSlot = getSelectedHotbarSlot();
        if (selectedSlot != NOT_FOUND) {
            ItemStack selectedStack = getStack(selectedSlot);
            if (!selectedStack.isEmpty() && predicate.test(selectedStack)) {
                return selectedSlot;
            }
        }

        int hotbarSlot = findHotbarSlot(predicate);
        return hotbarSlot != NOT_FOUND ? hotbarSlot : findSlot(predicate);
    }

    public static int count(Item item) {
        Objects.requireNonNull(item, "item");
        return count(stack -> stack.is(item));
    }

    public static int count(Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        Inventory inventory = inventory();
        if (inventory == null) {
            return 0;
        }

        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    public static boolean clickSlot(int menuSlot, int button, ClickType clickType) {
        Objects.requireNonNull(clickType, "clickType");
        if (!canSimulateInventoryAction()) {
            return false;
        }

        AbstractContainerMenu menu = MC.player.containerMenu;
        if (menuSlot != AbstractContainerMenu.SLOT_CLICKED_OUTSIDE && !menu.isValidSlotIndex(menuSlot)) {
            return false;
        }

        MC.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, button, clickType, MC.player);
        return true;
    }

    public static boolean leftClickSlot(int menuSlot) {
        return clickSlot(menuSlot, LEFT_BUTTON, ClickType.PICKUP);
    }

    public static boolean rightClickSlot(int menuSlot) {
        return clickSlot(menuSlot, RIGHT_BUTTON, ClickType.PICKUP);
    }

    public static boolean shiftClickSlot(int menuSlot) {
        return clickSlot(menuSlot, LEFT_BUTTON, ClickType.QUICK_MOVE);
    }

    public static boolean swapSlotWithHotbar(int menuSlot, int hotbarSlot) {
        if (!Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        return clickSlot(menuSlot, hotbarSlot, ClickType.SWAP);
    }

    public static boolean swapSlotWithOffhand(int menuSlot) {
        return clickSlot(menuSlot, OFFHAND_SLOT, ClickType.SWAP);
    }

    public static boolean dropSlot(int menuSlot) {
        return dropSlot(menuSlot, false);
    }

    public static boolean dropSlot(int menuSlot, boolean entireStack) {
        return clickSlot(menuSlot, entireStack ? RIGHT_BUTTON : LEFT_BUTTON, ClickType.THROW);
    }

    public static boolean cloneSlot(int menuSlot) {
        return clickSlot(menuSlot, LEFT_BUTTON, ClickType.CLONE);
    }

    public static boolean pickupAllMatching(int menuSlot) {
        return clickSlot(menuSlot, LEFT_BUTTON, ClickType.PICKUP_ALL);
    }

    public static boolean clickOutside() {
        return clickSlot(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, LEFT_BUTTON, ClickType.PICKUP);
    }

    public static boolean dropCarriedStack() {
        return clickSlot(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, LEFT_BUTTON, ClickType.PICKUP);
    }

    public static boolean dropCarriedItem() {
        return clickSlot(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, RIGHT_BUTTON, ClickType.PICKUP);
    }

    public static boolean leftClickInventorySlot(int inventorySlot) {
        return clickInventorySlot(inventorySlot, LEFT_BUTTON, ClickType.PICKUP);
    }

    public static boolean rightClickInventorySlot(int inventorySlot) {
        return clickInventorySlot(inventorySlot, RIGHT_BUTTON, ClickType.PICKUP);
    }

    public static boolean shiftClickInventorySlot(int inventorySlot) {
        return clickInventorySlot(inventorySlot, LEFT_BUTTON, ClickType.QUICK_MOVE);
    }

    public static boolean swapInventorySlotWithHotbar(int inventorySlot, int hotbarSlot) {
        if (!Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        return clickInventorySlot(inventorySlot, hotbarSlot, ClickType.SWAP);
    }

    public static boolean swapInventorySlotWithOffhand(int inventorySlot) {
        return clickInventorySlot(inventorySlot, OFFHAND_SLOT, ClickType.SWAP);
    }

    public static boolean dropInventorySlot(int inventorySlot) {
        return dropInventorySlot(inventorySlot, false);
    }

    public static boolean dropInventorySlot(int inventorySlot, boolean entireStack) {
        return clickInventorySlot(inventorySlot, entireStack ? RIGHT_BUTTON : LEFT_BUTTON, ClickType.THROW);
    }

    public static boolean dropSelected(boolean entireStack) {
        return MC.player != null && MC.player.drop(entireStack);
    }

    public static boolean swapInventorySlots(int firstInventorySlot, int secondInventorySlot) {
        if (firstInventorySlot == secondInventorySlot) {
            return isValidInventorySlot(firstInventorySlot);
        }

        int firstMenuSlot = inventoryToMenuSlot(firstInventorySlot);
        int secondMenuSlot = inventoryToMenuSlot(secondInventorySlot);
        return swapMenuSlots(firstMenuSlot, secondMenuSlot);
    }

    public static boolean swapMenuSlots(int firstMenuSlot, int secondMenuSlot) {
        AbstractContainerMenu menu = currentMenu();
        if (menu == null
            || !menu.getCarried().isEmpty()
            || !menu.isValidSlotIndex(firstMenuSlot)
            || !menu.isValidSlotIndex(secondMenuSlot)) {
            return false;
        }

        return leftClickSlot(firstMenuSlot)
            && leftClickSlot(secondMenuSlot)
            && leftClickSlot(firstMenuSlot);
    }

    public static boolean moveInventorySlotToEmptySlot(int sourceInventorySlot, int targetInventorySlot) {
        if (!isValidInventorySlot(sourceInventorySlot)
            || !isValidInventorySlot(targetInventorySlot)
            || !getStack(targetInventorySlot).isEmpty()) {
            return false;
        }

        return swapInventorySlots(sourceInventorySlot, targetInventorySlot);
    }

    public static boolean moveInventorySlotToHotbar(int inventorySlot, int hotbarSlot) {
        return swapInventorySlotWithHotbar(inventorySlot, hotbarSlot);
    }

    public static boolean moveInventorySlotToOffhand(int inventorySlot) {
        if (inventorySlot == OFFHAND_SLOT) {
            return isValidInventorySlot(inventorySlot);
        }

        return isValidInventorySlot(inventorySlot) && swapInventorySlotWithOffhand(inventorySlot);
    }

    public static boolean equipFromInventorySlot(int inventorySlot) {
        if (MC.player == null || !isValidInventorySlot(inventorySlot)) {
            return false;
        }

        ItemStack stack = getStack(inventorySlot);
        if (stack.isEmpty()) {
            return false;
        }

        int targetInventorySlot = equipmentSlotToInventorySlot(MC.player.getEquipmentSlotForItem(stack));
        if (targetInventorySlot == NOT_FOUND || targetInventorySlot == inventorySlot) {
            return false;
        }

        return swapInventorySlots(inventorySlot, targetInventorySlot);
    }

    public static boolean clickInventorySlot(int inventorySlot, int button, ClickType clickType) {
        int menuSlot = inventoryToMenuSlot(inventorySlot);
        return menuSlot != NOT_FOUND && clickSlot(menuSlot, button, clickType);
    }

    public static int inventoryToMenuSlot(int inventorySlot) {
        return inventoryToMenuSlot(currentMenu(), inventorySlot);
    }

    public static int inventoryToPlayerMenuSlot(int inventorySlot) {
        return inventoryToMenuSlot(playerInventoryMenu(), inventorySlot);
    }

    public static int inventoryToMenuSlot(AbstractContainerMenu menu, int inventorySlot) {
        if (MC.player == null || menu == null || !isValidInventorySlot(inventorySlot)) {
            return NOT_FOUND;
        }

        OptionalInt slot = menu.findSlot(MC.player.getInventory(), inventorySlot);
        return slot.orElse(NOT_FOUND);
    }

    public static int menuToInventorySlot(int menuSlot) {
        Slot slot = getMenuSlot(menuSlot);
        return slot != null && MC.player != null && slot.container == MC.player.getInventory() ? slot.getContainerSlot() : NOT_FOUND;
    }

    public static Slot getMenuSlot(int menuSlot) {
        AbstractContainerMenu menu = currentMenu();
        return menu != null && menu.isValidSlotIndex(menuSlot) ? menu.getSlot(menuSlot) : null;
    }

    public static boolean creativeSetInventorySlot(int inventorySlot, ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (MC.player == null || MC.gameMode == null || !MC.player.hasInfiniteMaterials()) {
            return false;
        }

        int playerMenuSlot = inventoryToPlayerMenuSlot(inventorySlot);
        return playerMenuSlot != NOT_FOUND && creativeSetPlayerMenuSlot(playerMenuSlot, stack);
    }

    public static boolean creativeSetPlayerMenuSlot(int playerMenuSlot, ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (MC.player == null
            || MC.gameMode == null
            || !MC.player.hasInfiniteMaterials()
            || playerMenuSlot < InventoryMenu.CRAFT_SLOT_START
            || playerMenuSlot > InventoryMenu.SHIELD_SLOT
            || !MC.player.inventoryMenu.isValidSlotIndex(playerMenuSlot)) {
            return false;
        }

        ItemStack copy = stack.copy();
        MC.player.inventoryMenu.getSlot(playerMenuSlot).setByPlayer(copy);
        MC.player.inventoryMenu.broadcastChanges();
        MC.gameMode.handleCreativeModeItemAdd(copy, playerMenuSlot);
        return true;
    }

    public static boolean creativeClearInventorySlot(int inventorySlot) {
        return creativeSetInventorySlot(inventorySlot, ItemStack.EMPTY);
    }

    public static boolean creativeDrop(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (MC.player == null || MC.gameMode == null || !MC.player.hasInfiniteMaterials() || stack.isEmpty()) {
            return false;
        }

        MC.gameMode.handleCreativeModeItemDrop(stack.copy());
        return true;
    }

    public static int equipmentSlotToInventorySlot(EquipmentSlot equipmentSlot) {
        if (equipmentSlot == null) {
            return NOT_FOUND;
        }

        return switch (equipmentSlot) {
            case MAINHAND -> getSelectedHotbarSlot();
            case OFFHAND -> OFFHAND_SLOT;
            case FEET -> ARMOR_FEET_SLOT;
            case LEGS -> ARMOR_LEGS_SLOT;
            case CHEST -> ARMOR_CHEST_SLOT;
            case HEAD -> ARMOR_HEAD_SLOT;
            case BODY -> BODY_ARMOR_SLOT;
            case SADDLE -> SADDLE_SLOT;
        };
    }
}
