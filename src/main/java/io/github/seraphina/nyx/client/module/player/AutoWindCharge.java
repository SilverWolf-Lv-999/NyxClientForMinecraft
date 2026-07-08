package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.StartUseItemEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(name = "nyxclient.module.autowindcharge.name", description = "nyxclient.module.autowindcharge.description", category = Category.PLAYER)
public class AutoWindCharge extends Module {
    public static final AutoWindCharge INSTANCE = new AutoWindCharge();

    private boolean waitForUseRelease;

    @Override
    public void onDisable() {
        waitForUseRelease = false;
    }

    @EventTarget
    public void onPreTick(TickEvent.Pre event) {
        if (mc.player == null || !mc.options.keyUse.isDown()) {
            waitForUseRelease = false;
        }
    }

    @EventTarget
    public void onStartUseItem(StartUseItemEvent event) {
        if (!canUseWindCharge()) {
            return;
        }

        int windChargeSlot = InventoryUtility.findHotbarSlot(Items.WIND_CHARGE);
        if (windChargeSlot == InventoryUtility.NOT_FOUND) {
            return;
        }

        if (useWindCharge(windChargeSlot)) {
            waitForUseRelease = mc.options.keyUse.isDown();
            event.setCancelled(true);
        }
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

    private boolean useWindCharge(int windChargeSlot) {
        int previousSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(previousSlot) || !Inventory.isHotbarSlot(windChargeSlot)) {
            return false;
        }

        ItemStack windChargeStack = InventoryUtility.getStack(windChargeSlot);
        if (windChargeStack.isEmpty()
                || !windChargeStack.isItemEnabled(mc.level.enabledFeatures())
                || mc.player.getCooldowns().isOnCooldown(windChargeStack)) {
            return false;
        }

        boolean changedSlot = previousSlot != windChargeSlot;
        if (changedSlot && !InventoryUtility.selectHotbarSlot(windChargeSlot)) {
            return false;
        }

        try {
            InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            if (result instanceof InteractionResult.Success success) {
                if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }

                mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
                return true;
            }

            return false;
        } finally {
            if (changedSlot) {
                InventoryUtility.selectHotbarSlot(previousSlot);
            }
        }
    }

    public enum ModeType {
        NORMAL, GRIM
    }
}
