package io.github.seraphina.nyx.client.module.combat;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import io.github.seraphina.nyx.client.value.impl.KeyBindValue;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(name = "nyxclient.module.keypearl.name", description = "nyxclient.module.keypearl.description", category = Category.COMBAT)
public class KeyPearl extends Module {
    public static final KeyPearl INSTANCE = new KeyPearl();

    private static final int MOUSE_BIND_OFFSET = -2;

    public final KeyBindValue activateKey = ValueBuild.keybindSetting("activate key", GLFW.GLFW_KEY_UNKNOWN, this);
    public final IntValue delay = ValueBuild.intSetting("delay", 0, 0, 20, 1, this);
    public final BoolValue switchBack = ValueBuild.boolSetting("switch back", true, this);
    public final IntValue switchDelay = ValueBuild.intSetting("switch delay", 0, 0, 20, 1, this);
    public final BoolValue swingHand = ValueBuild.boolSetting("swing hand", true, this);

    private boolean active;
    private boolean hasActivated;
    private boolean activateKeyWasDown;
    private int delayTicks;
    private int previousSlot = InventoryUtility.NOT_FOUND;
    private int switchDelayTicks;

    @Override
    public void onEnable() {
        resetState();
        activateKeyWasDown = false;
    }

    @Override
    public void onDisable() {
        restorePreviousSlot();
        resetState();
        activateKeyWasDown = false;
    }

    @EventTarget
    public void onPreTick(TickEvent.Pre event) {
        if (!canRun()) {
            return;
        }

        boolean activateKeyDown = isActivateKeyDown();
        if (!activateKeyDown) {
            activateKeyWasDown = false;
        } else if (!activateKeyWasDown && !active) {
            activateKeyWasDown = true;
            active = true;
        }

        if (!active) {
            return;
        }

        if (previousSlot == InventoryUtility.NOT_FOUND) {
            previousSlot = InventoryUtility.getSelectedHotbarSlot();
            if (!Inventory.isHotbarSlot(previousSlot)) {
                resetState();
                return;
            }
        }

        if (!hasActivated) {
            usePearl();
        }

        if (!hasActivated) {
            return;
        }

        if (switchBack.getValue()) {
            restoreAfterDelay();
        } else {
            resetState();
        }
    }

    private void usePearl() {
        int pearlSlot = InventoryUtility.findHotbarSlot(Items.ENDER_PEARL);
        if (!Inventory.isHotbarSlot(pearlSlot)) {
            resetState();
            return;
        }

        ItemStack pearlStack = InventoryUtility.getStack(pearlSlot);
        if (pearlStack.isEmpty() || !pearlStack.is(Items.ENDER_PEARL) || !pearlStack.isItemEnabled(mc.level.enabledFeatures())) {
            resetState();
            return;
        }

        if (!InventoryUtility.selectHotbarSlot(pearlSlot, true)) {
            resetState();
            return;
        }

        if (delayTicks < delay.getValue()) {
            delayTicks++;
            return;
        }

        InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (result.consumesAction()) {
            if (swingHand.getValue()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else if (mc.player.connection != null) {
                mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
            mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
        }
        hasActivated = true;
    }

    private void restoreAfterDelay() {
        if (switchDelayTicks < switchDelay.getValue()) {
            switchDelayTicks++;
            return;
        }

        restorePreviousSlot();
        resetState();
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator();
    }

    private boolean isActivateKeyDown() {
        int key = activateKey.getValue();
        if (key == GLFW.GLFW_KEY_UNKNOWN || mc.getWindow() == null) {
            return false;
        }

        if (key <= MOUSE_BIND_OFFSET) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), MOUSE_BIND_OFFSET - key) == GLFW.GLFW_PRESS;
        }

        return InputConstants.isKeyDown(mc.getWindow(), key);
    }

    private void restorePreviousSlot() {
        if (Inventory.isHotbarSlot(previousSlot)) {
            InventoryUtility.selectHotbarSlot(previousSlot, true);
        }
    }

    private void resetState() {
        active = false;
        hasActivated = false;
        delayTicks = 0;
        previousSlot = InventoryUtility.NOT_FOUND;
        switchDelayTicks = 0;
    }
}
