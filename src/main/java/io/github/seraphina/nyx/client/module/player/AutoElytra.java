package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.KeyPressEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
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
    private static final int START_FALL_FLYING_RETRY_TICKS = 5;

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
        equipElytraFromHotbar();
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

        if (!waitForElytraEquip()) {
            return;
        }

        if (!mc.player.onGround()) {
            activeElytra.hasBeenAirborne = true;
            retryStartFallFlying();
            return;
        }

        if (activeElytra.hasBeenAirborne) {
            restoreChestSlot();
        }
    }

    private boolean canRun() {
        return mc.player != null
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

    private void equipElytraFromHotbar() {
        if (mc.player == null) return;
        int elytraSlot = InventoryUtility.findHotbarSlot(Items.ELYTRA);
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(elytraSlot) || !Inventory.isHotbarSlot(selectedSlot)) {
            return;
        }

        ItemStack originalChestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST).copy();
        activeElytra = new ActiveElytra(elytraSlot, originalChestStack, !mc.player.onGround());

        if (elytraSlot != selectedSlot) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(elytraSlot));
        }

        try (BlockStatePredictionHandler prediction = mc.level.getBlockStatePredictionHandler().startPredicting()) {
            mc.player.connection.send(new ServerboundUseItemPacket(
                    InteractionHand.MAIN_HAND,
                    prediction.currentSequence(),
                    mc.player.getYRot(),
                    mc.player.getXRot()
            ));
        }

        if (elytraSlot != selectedSlot) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(selectedSlot));
        }

        sendStartFallFlyingPacket();
    }

    private void retryStartFallFlying() {
        if (mc.player == null || mc.player.isFallFlying() || activeElytra.startFallFlyingRetryTicks <= 0) {
            return;
        }

        activeElytra.startFallFlyingRetryTicks--;
        sendStartFallFlyingPacket();
    }

    private void sendStartFallFlyingPacket() {
        if (mc.player == null || mc.player.connection == null || mc.player.isFallFlying()) {
            return;
        }

        mc.player.connection.send(new ServerboundPlayerCommandPacket(
                mc.player,
                ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
        ));
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

        activeElytra = null;
        return false;
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
        private final ItemStack originalChestStack;
        private boolean hasBeenAirborne;
        private boolean confirmedEquipped;
        private int equipConfirmTicks = EQUIP_CONFIRM_TIMEOUT_TICKS;
        private int startFallFlyingRetryTicks = START_FALL_FLYING_RETRY_TICKS;

        private ActiveElytra(int elytraSlot, ItemStack originalChestStack, boolean hasBeenAirborne) {
            this.elytraSlot = elytraSlot;
            this.originalChestStack = originalChestStack;
            this.hasBeenAirborne = hasBeenAirborne;
        }
    }
}
