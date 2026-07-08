package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.KeyPressEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
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

    private long lastJumpPressTime;

    @Override
    public void onDisable() {
        lastJumpPressTime = 0L;
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
    }
}
