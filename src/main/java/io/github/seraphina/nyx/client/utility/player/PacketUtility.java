package io.github.seraphina.nyx.client.utility.player;

import io.github.seraphina.nyx.client.mixins.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.BlockHitResult;

public final class PacketUtility {
    private static final Minecraft MC = Minecraft.getInstance();

    private PacketUtility() {
    }

    public static boolean useHotbarItemOnBlock(int hotbarSlot, BlockHitResult hitResult) {
        if (MC.player == null
                || MC.player.connection == null
                || MC.gameMode == null
                || MC.level == null
                || hitResult == null
                || !Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(selectedSlot)) {
            return false;
        }

        boolean spoofedSlot = selectedSlot != hotbarSlot;
        if (spoofedSlot) {
            MC.player.connection.send(new ServerboundSetCarriedItemPacket(hotbarSlot));
        }

        try {
            ((MultiPlayerGameModeAccessor) MC.gameMode).nyx$startPrediction(
                    MC.level,
                    sequence -> new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, sequence)
            );
        } finally {
            if (spoofedSlot) {
                MC.player.connection.send(new ServerboundSetCarriedItemPacket(selectedSlot));
            }
        }

        MC.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        return true;
    }

    public static boolean attack(Entity entity, boolean secondaryUseActive) {
        if (MC.player == null || MC.player.connection == null || entity == null) {
            return false;
        }

        MC.player.connection.send(ServerboundInteractPacket.createAttackPacket(entity, secondaryUseActive));
        MC.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        return true;
    }
}
