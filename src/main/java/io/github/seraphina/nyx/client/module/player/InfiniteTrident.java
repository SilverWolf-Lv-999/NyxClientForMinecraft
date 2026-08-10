package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.player.Inventory;

@ModuleInfo(name = "nyxclient.module.infinitetrident.name", description = "nyxclient.module.infinitetrident.description", category = Category.PLAYER)
public class InfiniteTrident extends Module {
    public static final InfiniteTrident INSTANCE = new InfiniteTrident();

    private static final int TRIDENT_MENU_SLOT = 3;

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundPlayerActionPacket packet)
                || packet.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                || mc.player == null
                || mc.gameMode == null) {
            return;
        }

        int hotbarSlot = InventoryUtility.getSelectedHotbarSlot();
        if (Inventory.isHotbarSlot(hotbarSlot)) {
            InventoryUtility.swapSlotWithHotbar(TRIDENT_MENU_SLOT, hotbarSlot);
        }
    }
}
