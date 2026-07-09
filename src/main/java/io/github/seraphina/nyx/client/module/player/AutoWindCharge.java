package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.ClickEvent;
import io.github.seraphina.nyx.client.events.impl.MousePressEvent;
import io.github.seraphina.nyx.client.events.impl.StartUseItemEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector2f;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

@ModuleInfo(name = "nyxclient.module.autowindcharge.name", description = "nyxclient.module.autowindcharge.description", category = Category.PLAYER)
public class AutoWindCharge extends Module {
    public static final AutoWindCharge INSTANCE = new AutoWindCharge();

    private static final long DOUBLE_RIGHT_CLICK_WINDOW_MS = 300L;
    private static final int DOUBLE_RIGHT_CLICK_WINDOW_TICKS = 6;
    private static final int RESTORE_SLOT_DELAY_TICKS = 1;
    private static final float UP_PITCH = -90.0F;

    public final BoolValue pearlUp = ValueBuild.boolSetting("pearlup", false, this);
    public final IntValue pearlUpDelay = ValueBuild.intSetting("pearlupdelay", 5, 1, 10, 1, () -> pearlUp.getValue(), this);

    private boolean waitForUseRelease;
    private long lastRightClickPressTime;
    private boolean pendingRightClick;
    private boolean pendingRightClickDoubleClick;
    private int pendingWindChargeTicks;
    private ActivePearlUp activePearlUp;
    private QueuedUse queuedUse;
    private PendingRestore pendingRestore;

    @Override
    public void onDisable() {
        waitForUseRelease = false;
        lastRightClickPressTime = 0L;
        pendingRightClick = false;
        pendingRightClickDoubleClick = false;
        pendingWindChargeTicks = 0;
        restorePendingSlotNow();
        restoreQueuedUseSlotNow();
        restoreActivePearlUpSlotNow();
        activePearlUp = null;
        queuedUse = null;
        pendingRestore = null;
    }

    @EventTarget
    public void onPreTick(TickEvent.Pre event) {
        if (mc.player == null || !mc.options.keyUse.isDown()) {
            waitForUseRelease = false;
        }

        if (activePearlUp != null) {
            if (canRun()) {
                rotateUp();
            } else {
                queueRestoreSelectedSlot(activePearlUp.originalSlot, InventoryUtility.getSelectedHotbarSlot());
                activePearlUp = null;
            }
        }

        if (queuedUse != null && queuedUse.rotateUp) {
            if (canRun()) {
                rotateUp();
            } else {
                finishQueuedUse(queuedUse, false);
            }
        }
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        tickPendingRestore();
        tickActivePearlUp();
        tickPendingWindCharge();
        tickQueuedUse();
    }

    @EventTarget
    public void onClick(ClickEvent event) {
        if (queuedUse == null || queuedUse.stage != QueuedUseStage.USE) {
            return;
        }

        event.setCancelled(true);
        drainUseClicks();

        QueuedUse currentUse = queuedUse;
        if (!canRun()
                || InventoryUtility.getSelectedHotbarSlot() != currentUse.hotbarSlot
                || !canUseHotbarItem(currentUse.hotbarSlot, currentUse.item)) {
            finishQueuedUse(currentUse, false);
            return;
        }

        if (currentUse.rotateUp) {
            rotateUp();
        }

        finishQueuedUse(currentUse, useSelectedItem());
    }

    @EventTarget
    public void onMousePress(MousePressEvent event) {
        if (event.getButton() != GLFW_MOUSE_BUTTON_RIGHT || event.getAction() != GLFW_PRESS || mc.screen != null) {
            return;
        }

        long now = System.currentTimeMillis();
        pendingRightClick = true;
        pendingRightClickDoubleClick = lastRightClickPressTime > 0L && now - lastRightClickPressTime <= DOUBLE_RIGHT_CLICK_WINDOW_MS;
        lastRightClickPressTime = pendingRightClickDoubleClick ? 0L : now;
    }

    @EventTarget
    public void onStartUseItem(StartUseItemEvent event) {
        if (activePearlUp != null || queuedUse != null || pendingRestore != null || waitForUseRelease) {
            consumeRightClickDoubleClick();
            event.setCancelled(true);
            return;
        }

        boolean rightClick = pendingRightClick;
        boolean doubleRightClick = consumeRightClickDoubleClick();

        if (!canUseWindCharge()) {
            return;
        }

        int windChargeSlot = InventoryUtility.findHotbarSlot(Items.WIND_CHARGE);
        if (windChargeSlot == InventoryUtility.NOT_FOUND) {
            return;
        }

        if (tryStartPearlUp(rightClick, doubleRightClick, windChargeSlot, event)) {
            return;
        }

        if (pendingWindChargeTicks > 0) {
            event.setCancelled(true);
            return;
        }

        queueUse(QueuedUseType.WIND_CHARGE, windChargeSlot, Items.WIND_CHARGE, false, InventoryUtility.getSelectedHotbarSlot(), true);
        event.setCancelled(true);
    }

    private boolean canUseWindCharge() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator()
                && !waitForUseRelease
                && InventoryUtility.getMainHandStack().is(Items.MACE);
    }

    private boolean tryStartPearlUp(boolean rightClick, boolean doubleRightClick, int windChargeSlot, StartUseItemEvent event) {
        if (!pearlUp.getValue() || !rightClick) {
            return false;
        }

        int pearlSlot = InventoryUtility.findHotbarSlot(Items.ENDER_PEARL);
        if (pearlSlot == InventoryUtility.NOT_FOUND || !canUseHotbarItem(pearlSlot, Items.ENDER_PEARL)) {
            return false;
        }

        if (!doubleRightClick) {
            if (pendingWindChargeTicks <= 0) {
                pendingWindChargeTicks = DOUBLE_RIGHT_CLICK_WINDOW_TICKS + 1;
            }
            event.setCancelled(true);
            return true;
        }

        pendingWindChargeTicks = 0;
        int originalSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(originalSlot)) {
            return false;
        }

        rotateUp();
        activePearlUp = new ActivePearlUp(originalSlot, windChargeSlot, pearlUpDelay.getValue());
        if (!queueUse(QueuedUseType.PEARL_UP_PEARL, pearlSlot, Items.ENDER_PEARL, true, originalSlot, false)) {
            activePearlUp = null;
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    private boolean consumeRightClickDoubleClick() {
        boolean doubleRightClick = pendingRightClick && pendingRightClickDoubleClick;
        pendingRightClick = false;
        pendingRightClickDoubleClick = false;
        return doubleRightClick;
    }

    private void tickPendingWindCharge() {
        if (pendingWindChargeTicks <= 0) {
            return;
        }

        if (!canUseWindCharge()) {
            pendingWindChargeTicks = 0;
            return;
        }

        pendingWindChargeTicks--;
        if (pendingWindChargeTicks > 0) {
            return;
        }

        int windChargeSlot = InventoryUtility.findHotbarSlot(Items.WIND_CHARGE);
        if (windChargeSlot != InventoryUtility.NOT_FOUND) {
            queueUse(QueuedUseType.WIND_CHARGE, windChargeSlot, Items.WIND_CHARGE, false, InventoryUtility.getSelectedHotbarSlot(), true);
        }
    }

    private void tickActivePearlUp() {
        if (activePearlUp == null) {
            return;
        }

        if (!canRun()) {
            queueRestoreSelectedSlot(activePearlUp.originalSlot, InventoryUtility.getSelectedHotbarSlot());
            activePearlUp = null;
            return;
        }

        rotateUp();

        if (!activePearlUp.pearlThrown || activePearlUp.windChargeQueued) {
            return;
        }

        activePearlUp.windChargeDelayTicks--;
        if (activePearlUp.windChargeDelayTicks > 0) {
            return;
        }

        int windChargeSlot = activePearlUp.windChargeSlot;
        if (!canUseHotbarItem(windChargeSlot, Items.WIND_CHARGE)) {
            windChargeSlot = InventoryUtility.findHotbarSlot(Items.WIND_CHARGE);
        }

        if (windChargeSlot != InventoryUtility.NOT_FOUND
                && queueUse(QueuedUseType.PEARL_UP_WIND_CHARGE, windChargeSlot, Items.WIND_CHARGE, true, activePearlUp.originalSlot, true)) {
            activePearlUp.windChargeQueued = true;
        } else {
            queueRestoreSelectedSlot(activePearlUp.originalSlot, InventoryUtility.getSelectedHotbarSlot());
            activePearlUp = null;
        }
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator();
    }

    private boolean canUseHotbarItem(int hotbarSlot, Item item) {
        if (mc.player == null || mc.level == null || !Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        ItemStack stack = InventoryUtility.getStack(hotbarSlot);
        return !stack.isEmpty()
                && stack.is(item)
                && stack.isItemEnabled(mc.level.enabledFeatures())
                && !mc.player.getCooldowns().isOnCooldown(stack);
    }

    private boolean queueUse(QueuedUseType type, int hotbarSlot, Item item, boolean rotateUp, int restoreSlot, boolean restoreOnSuccess) {
        if (queuedUse != null || !canUseHotbarItem(hotbarSlot, item)) {
            return false;
        }

        int currentSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(currentSlot)) {
            return false;
        }

        int targetRestoreSlot = Inventory.isHotbarSlot(restoreSlot) ? restoreSlot : currentSlot;
        queuedUse = new QueuedUse(type, hotbarSlot, item, rotateUp, targetRestoreSlot, restoreOnSuccess);
        return true;
    }

    private void tickQueuedUse() {
        if (queuedUse == null || queuedUse.stage != QueuedUseStage.SWITCH_SLOT) {
            return;
        }

        if (!canRun() || !canUseHotbarItem(queuedUse.hotbarSlot, queuedUse.item)) {
            finishQueuedUse(queuedUse, false);
            return;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != queuedUse.hotbarSlot
                && !InventoryUtility.selectHotbarSlot(queuedUse.hotbarSlot)) {
            finishQueuedUse(queuedUse, false);
            return;
        }

        queuedUse.stage = QueuedUseStage.USE;
    }

    private boolean useSelectedItem() {
        InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (result instanceof InteractionResult.Success useSuccess) {
            if (useSuccess.swingSource() == InteractionResult.SwingSource.CLIENT) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }

            mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
            return true;
        }

        return false;
    }

    private void finishQueuedUse(QueuedUse finishedUse, boolean usedSuccessfully) {
        if (queuedUse == finishedUse) {
            queuedUse = null;
        }

        if (usedSuccessfully) {
            waitForUseRelease = mc.options.keyUse.isDown();
        }

        if ((finishedUse.restoreOnSuccess && usedSuccessfully) || !usedSuccessfully) {
            queueRestoreSelectedSlot(finishedUse.restoreSlot, finishedUse.hotbarSlot);
        }

        switch (finishedUse.type) {
            case WIND_CHARGE -> {
            }
            case PEARL_UP_PEARL -> {
                if (activePearlUp == null) {
                    return;
                }

                if (usedSuccessfully) {
                    activePearlUp.pearlThrown = true;
                } else {
                    activePearlUp = null;
                }
            }
            case PEARL_UP_WIND_CHARGE -> activePearlUp = null;
        }
    }

    private void drainUseClicks() {
        while (mc.options.keyUse.consumeClick()) {
        }
    }

    private void tickPendingRestore() {
        if (pendingRestore == null) {
            return;
        }

        if (!canRestoreSelectedSlot()) {
            return;
        }

        if (pendingRestore.delayTicks > 0) {
            pendingRestore.delayTicks--;
            return;
        }

        restoreSelectedSlot(pendingRestore);
        pendingRestore = null;
    }

    private void queueRestoreSelectedSlot(int restoreSlot, int temporarySlot) {
        if (!Inventory.isHotbarSlot(restoreSlot)
                || !Inventory.isHotbarSlot(temporarySlot)
                || restoreSlot == temporarySlot) {
            return;
        }

        pendingRestore = new PendingRestore(restoreSlot, temporarySlot, RESTORE_SLOT_DELAY_TICKS);
    }

    private boolean canRestoreSelectedSlot() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator();
    }

    private void restorePendingSlotNow() {
        if (pendingRestore != null) {
            restoreSelectedSlot(pendingRestore);
        }
    }

    private void restoreActivePearlUpSlotNow() {
        if (activePearlUp != null) {
            restoreSelectedSlot(new PendingRestore(activePearlUp.originalSlot, InventoryUtility.getSelectedHotbarSlot(), 0));
        }
    }

    private void restoreQueuedUseSlotNow() {
        if (queuedUse != null) {
            restoreSelectedSlot(new PendingRestore(queuedUse.restoreSlot, queuedUse.hotbarSlot, 0));
        }
    }

    private void restoreSelectedSlot(PendingRestore restore) {
        if (restore == null
                || !Inventory.isHotbarSlot(restore.restoreSlot)
                || InventoryUtility.getSelectedHotbarSlot() != restore.temporarySlot) {
            return;
        }

        InventoryUtility.selectHotbarSlot(restore.restoreSlot);
    }

    private void rotateUp() {
        RotationManager.INSTANCE.setRotations(new Vector2f(mc.player.getYRot(), UP_PITCH), 180.0D, Priority.Highest);
    }

    private static final class ActivePearlUp {
        private final int originalSlot;
        private final int windChargeSlot;
        private int windChargeDelayTicks;
        private boolean pearlThrown;
        private boolean windChargeQueued;

        private ActivePearlUp(int originalSlot, int windChargeSlot, int windChargeDelayTicks) {
            this.originalSlot = originalSlot;
            this.windChargeSlot = windChargeSlot;
            this.windChargeDelayTicks = windChargeDelayTicks;
        }
    }

    private static final class PendingRestore {
        private final int restoreSlot;
        private final int temporarySlot;
        private int delayTicks;

        private PendingRestore(int restoreSlot, int temporarySlot, int delayTicks) {
            this.restoreSlot = restoreSlot;
            this.temporarySlot = temporarySlot;
            this.delayTicks = delayTicks;
        }
    }

    private static final class QueuedUse {
        private final QueuedUseType type;
        private final int hotbarSlot;
        private final Item item;
        private final boolean rotateUp;
        private final int restoreSlot;
        private final boolean restoreOnSuccess;
        private QueuedUseStage stage = QueuedUseStage.SWITCH_SLOT;

        private QueuedUse(QueuedUseType type, int hotbarSlot, Item item, boolean rotateUp, int restoreSlot, boolean restoreOnSuccess) {
            this.type = type;
            this.hotbarSlot = hotbarSlot;
            this.item = item;
            this.rotateUp = rotateUp;
            this.restoreSlot = restoreSlot;
            this.restoreOnSuccess = restoreOnSuccess;
        }
    }

    private enum QueuedUseType {
        WIND_CHARGE,
        PEARL_UP_PEARL,
        PEARL_UP_WIND_CHARGE
    }

    private enum QueuedUseStage {
        SWITCH_SLOT,
        USE
    }

    public enum ModeType {
        NORMAL, GRIM
    }
}
