package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.KeyPressEvent;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

@ModuleInfo(name = "nyxclient.module.autoelytra.name", description = "nyxclient.module.autoelytra.description", category = Category.PLAYER)
public class AutoElytra extends Module {
    public static final AutoElytra INSTANCE = new AutoElytra();

    private static final long DOUBLE_JUMP_WINDOW_MS = 300L;
    private static final int EQUIP_CONFIRM_TIMEOUT_TICKS = 40;
    private static final int START_FALL_FLYING_TIMEOUT_TICKS = 40;
    private static final int RESTORE_SELECTED_SLOT_DELAY_TICKS = 2;

    private long lastJumpPressTime;
    private ActiveElytra activeElytra;

    @Override
    public void onDisable() {
        lastJumpPressTime = 0L;
        activeElytra = null;
    }

    @EventTarget
    public void onKeyPress(KeyPressEvent event) {
        if (event.getAction() != GLFW_PRESS || !canRun() || !mc.options.keyJump.matches(event.getKeyEvent())) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastJumpPressTime > DOUBLE_JUMP_WINDOW_MS) {
            lastJumpPressTime = now;
            return;
        }

        lastJumpPressTime = 0L;
        queueElytraFromHotbar();
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (activeElytra == null) {
            return;
        }

        switch (activeElytra.stage) {
            case WAIT_EQUIP -> event.setJump(false);
            case GROUND_JUMP_RELEASE -> {
                event.setJump(false);
                activeElytra.stage = Stage.GROUND_JUMP_PRESS;
            }
            case GROUND_JUMP_PRESS -> {
                event.setJump(true);
                activeElytra.stage = Stage.WAIT_AIRBORNE;
            }
            case GLIDE_RELEASE -> {
                event.setJump(false);
                activeElytra.stage = Stage.GLIDE_PRESS;
            }
            case GLIDE_PRESS -> {
                event.setJump(true);
                activeElytra.stage = Stage.WAIT_GLIDE;
            }
        }
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (activeElytra == null) {
            return;
        }

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            activeElytra = null;
            return;
        }

        if (mc.gameMode.getPlayerMode() != GameType.SURVIVAL) {
            cancelActiveElytra();
            return;
        }

        if (!mc.player.onGround()) {
            activeElytra.hasBeenAirborne = true;
        }

        if (mc.player.isFallFlying()) {
            activeElytra.hasBeenAirborne = true;
            activeElytra.stage = Stage.FLIGHT;
            restoreSelectedSlotAfterDelay();
            return;
        }

        if (activeElytra.confirmedEquipped && activeElytra.hasBeenAirborne && mc.player.onGround()) {
            restoreSelectedSlot();
            restoreChestSlot();
            return;
        }

        switch (activeElytra.stage) {
            case USE_ELYTRA -> useQueuedElytra();
            case WAIT_EQUIP -> {
                if (waitForElytraEquip()) {
                    activeElytra.stage = mc.player.onGround() ? Stage.GROUND_JUMP_RELEASE : Stage.GLIDE_RELEASE;
                }
            }
            case WAIT_AIRBORNE -> waitForAirborne();
            case WAIT_GLIDE -> retryGlideInput();
            case FLIGHT -> restoreSelectedSlotAfterDelay();
        }
    }

    private boolean canRun() {
        return activeElytra == null
                && mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && mc.gameMode.getPlayerMode() == GameType.SURVIVAL
                && !isWearingElytra();
    }

    private boolean isWearingElytra() {
        assert mc.player != null;
        ItemStack chestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        return chestStack.is(Items.ELYTRA);
    }

    private void queueElytraFromHotbar() {
        if (mc.player == null) return;
        int elytraSlot = InventoryUtility.findHotbarSlot(Items.ELYTRA);
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(elytraSlot) || !Inventory.isHotbarSlot(selectedSlot)) {
            return;
        }

        ItemStack originalChestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST).copy();
        activeElytra = new ActiveElytra(elytraSlot, selectedSlot, originalChestStack, !mc.player.onGround());
    }

    private void useQueuedElytra() {
        if (mc.screen != null || activeElytra == null) {
            cancelActiveElytra();
            return;
        }

        if (!InventoryUtility.selectHotbarSlot(activeElytra.elytraSlot)) {
            cancelActiveElytra();
            return;
        }

        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        activeElytra.stage = Stage.WAIT_EQUIP;
        if (isWearingElytra()) {
            activeElytra.confirmedEquipped = true;
            activeElytra.stage = mc.player.onGround() ? Stage.GROUND_JUMP_RELEASE : Stage.GLIDE_RELEASE;
        }
    }

    private boolean waitForElytraEquip() {
        if (isWearingElytra()) {
            activeElytra.confirmedEquipped = true;
            return true;
        }

        if (!activeElytra.confirmedEquipped && activeElytra.equipConfirmTicks > 0) {
            activeElytra.equipConfirmTicks--;
            if (mc.player != null && !mc.player.onGround()) {
                activeElytra.hasBeenAirborne = true;
            }
            return false;
        }

        cancelActiveElytra();
        return false;
    }

    private void waitForAirborne() {
        if (mc.player == null || activeElytra == null) {
            return;
        }

        if (mc.player.onGround()) {
            if (!tickStartFallFlyingTimeout()) {
                cancelActiveElytra();
            }
            return;
        }

        activeElytra.hasBeenAirborne = true;
        activeElytra.stage = Stage.GLIDE_RELEASE;
    }

    private void retryGlideInput() {
        if (mc.player == null || activeElytra == null) {
            return;
        }

        if (!isWearingElytra() || mc.player.onGround()) {
            if (!tickStartFallFlyingTimeout()) {
                cancelActiveElytra();
            }
            return;
        }

        if (tickStartFallFlyingTimeout()) {
            activeElytra.stage = Stage.GLIDE_RELEASE;
        } else {
            cancelActiveElytra();
        }
    }

    private boolean tickStartFallFlyingTimeout() {
        return activeElytra != null && activeElytra.startFallFlyingTicks-- > 0;
    }

    private void cancelActiveElytra() {
        restoreSelectedSlot();
        activeElytra = null;
    }

    private void restoreSelectedSlotAfterDelay() {
        if (activeElytra == null || activeElytra.selectedSlotRestored) {
            return;
        }

        if (activeElytra.restoreSelectedSlotDelayTicks > 0) {
            activeElytra.restoreSelectedSlotDelayTicks--;
            return;
        }

        restoreSelectedSlot();
    }

    private void restoreSelectedSlot() {
        if (activeElytra == null || activeElytra.selectedSlotRestored) {
            return;
        }

        InventoryUtility.selectHotbarSlot(activeElytra.originalSelectedSlot);
        activeElytra.selectedSlotRestored = true;
    }

    private void restoreChestSlot() {
        if (mc.screen != null || activeElytra == null) {
            return;
        }

        int targetSlot = findRestoreTargetSlot();
        if (targetSlot == InventoryUtility.NOT_FOUND) {
            return;
        }

        if (InventoryUtility.swapInventorySlots(InventoryUtility.ARMOR_CHEST_SLOT, targetSlot)) {
            activeElytra = null;
        }
    }

    private int findRestoreTargetSlot() {
        if (isUsableRestoreSlot(activeElytra.elytraSlot)) {
            return activeElytra.elytraSlot;
        }

        if (!activeElytra.originalChestStack.isEmpty()) {
            int originalSlot = InventoryUtility.findSlot(this::isOriginalChestStack);
            if (originalSlot != InventoryUtility.NOT_FOUND && originalSlot != InventoryUtility.ARMOR_CHEST_SLOT) {
                return originalSlot;
            }
        }

        int emptySlot = InventoryUtility.findEmptySlot();
        return emptySlot == InventoryUtility.ARMOR_CHEST_SLOT ? InventoryUtility.NOT_FOUND : emptySlot;
    }

    private boolean isUsableRestoreSlot(int inventorySlot) {
        if (!InventoryUtility.isValidInventorySlot(inventorySlot) || inventorySlot == InventoryUtility.ARMOR_CHEST_SLOT) {
            return false;
        }

        ItemStack stack = InventoryUtility.getStack(inventorySlot);
        return activeElytra.originalChestStack.isEmpty() ? stack.isEmpty() : isOriginalChestStack(stack);
    }

    private boolean isOriginalChestStack(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, activeElytra.originalChestStack);
    }

    private static final class ActiveElytra {
        private final int elytraSlot;
        private final int originalSelectedSlot;
        private final ItemStack originalChestStack;
        private Stage stage = Stage.USE_ELYTRA;
        private boolean hasBeenAirborne;
        private boolean confirmedEquipped;
        private boolean selectedSlotRestored;
        private int equipConfirmTicks = EQUIP_CONFIRM_TIMEOUT_TICKS;
        private int startFallFlyingTicks = START_FALL_FLYING_TIMEOUT_TICKS;
        private int restoreSelectedSlotDelayTicks = RESTORE_SELECTED_SLOT_DELAY_TICKS;

        private ActiveElytra(int elytraSlot, int originalSelectedSlot, ItemStack originalChestStack, boolean hasBeenAirborne) {
            this.elytraSlot = elytraSlot;
            this.originalSelectedSlot = originalSelectedSlot;
            this.originalChestStack = originalChestStack;
            this.hasBeenAirborne = hasBeenAirborne;
        }
    }

    private enum Stage {
        USE_ELYTRA,
        WAIT_EQUIP,
        GROUND_JUMP_RELEASE,
        GROUND_JUMP_PRESS,
        WAIT_AIRBORNE,
        GLIDE_RELEASE,
        GLIDE_PRESS,
        WAIT_GLIDE,
        FLIGHT
    }
}
