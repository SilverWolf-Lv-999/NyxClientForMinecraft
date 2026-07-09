package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
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
    private PendingRestore pendingRestore;

    @Override
    public void onDisable() {
        waitForUseRelease = false;
        lastRightClickPressTime = 0L;
        pendingRightClick = false;
        pendingRightClickDoubleClick = false;
        pendingWindChargeTicks = 0;
        restorePendingSlotNow();
        restoreActivePearlUpSlotNow();
        activePearlUp = null;
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
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        tickPendingRestore();
        tickActivePearlUp();
        tickPendingWindCharge();
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
        if (activePearlUp != null || pendingRestore != null || waitForUseRelease) {
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

        pendingWindChargeTicks = 1;
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
        activePearlUp = new ActivePearlUp(originalSlot, pearlSlot, windChargeSlot, pearlUpDelay.getValue());
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
        if (windChargeSlot != InventoryUtility.NOT_FOUND && useHotbarItem(windChargeSlot, Items.WIND_CHARGE, false)) {
            waitForUseRelease = mc.options.keyUse.isDown();
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

        if (!activePearlUp.pearlThrown) {
            if (useHotbarItem(activePearlUp.pearlSlot, Items.ENDER_PEARL, true, activePearlUp.originalSlot, false)) {
                activePearlUp.pearlThrown = true;
                waitForUseRelease = mc.options.keyUse.isDown();
            } else {
                queueRestoreSelectedSlot(activePearlUp.originalSlot, InventoryUtility.getSelectedHotbarSlot());
                activePearlUp = null;
            }
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

        if (windChargeSlot != InventoryUtility.NOT_FOUND) {
            if (useHotbarItem(windChargeSlot, Items.WIND_CHARGE, true, activePearlUp.originalSlot, true)) {
                waitForUseRelease = mc.options.keyUse.isDown();
            }
        } else {
            queueRestoreSelectedSlot(activePearlUp.originalSlot, InventoryUtility.getSelectedHotbarSlot());
        }

        activePearlUp = null;
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

    private boolean useHotbarItem(int hotbarSlot, Item item, boolean rotateUp) {
        return useHotbarItem(hotbarSlot, item, rotateUp, InventoryUtility.getSelectedHotbarSlot(), true);
    }

    private boolean useHotbarItem(int hotbarSlot, Item item, boolean rotateUp, int restoreSlot, boolean restoreOnSuccess) {
        int previousSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(previousSlot) || !canUseHotbarItem(hotbarSlot, item)) {
            return false;
        }

        boolean changedSlot = previousSlot != hotbarSlot;
        if (changedSlot && !InventoryUtility.selectHotbarSlot(hotbarSlot, true)) {
            return false;
        }

        boolean usedSuccessfully = false;
        try {
            if (rotateUp) {
                rotateUp();
            }

            InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            if (result instanceof InteractionResult.Success useSuccess) {
                if (useSuccess.swingSource() == InteractionResult.SwingSource.CLIENT) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }

                mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
                usedSuccessfully = true;
                return true;
            }

            return false;
        } finally {
            if (changedSlot && (restoreOnSuccess || !usedSuccessfully)) {
                int targetRestoreSlot = Inventory.isHotbarSlot(restoreSlot) ? restoreSlot : previousSlot;
                queueRestoreSelectedSlot(targetRestoreSlot, hotbarSlot);
            }
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

    private void restoreSelectedSlot(PendingRestore restore) {
        if (restore == null
                || !Inventory.isHotbarSlot(restore.restoreSlot)
                || InventoryUtility.getSelectedHotbarSlot() != restore.temporarySlot) {
            return;
        }

        InventoryUtility.selectHotbarSlot(restore.restoreSlot, true);
    }

    private void rotateUp() {
        RotationManager.INSTANCE.setRotations(new Vector2f(mc.player.getYRot(), UP_PITCH), 180.0D, Priority.Highest);
    }

    private static final class ActivePearlUp {
        private final int originalSlot;
        private final int pearlSlot;
        private final int windChargeSlot;
        private int windChargeDelayTicks;
        private boolean pearlThrown;

        private ActivePearlUp(int originalSlot, int pearlSlot, int windChargeSlot, int windChargeDelayTicks) {
            this.originalSlot = originalSlot;
            this.pearlSlot = pearlSlot;
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

    public enum ModeType {
        NORMAL, GRIM
    }
}
