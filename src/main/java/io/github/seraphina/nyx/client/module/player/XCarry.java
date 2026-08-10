package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.LevelUpdateEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

@ModuleInfo(name = "nyxclient.module.xcarry.name", description = "nyxclient.module.xcarry.description", category = Category.PLAYER)
public class XCarry extends Module {
    public static final XCarry INSTANCE = new XCarry();

    private InventoryScreen inventoryScreen;

    @Override
    public void onEnable() {
        inventoryScreen = null;
    }

    @Override
    public void onDisable() {
        inventoryScreen = null;
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (mc.screen instanceof InventoryScreen screen) {
            inventoryScreen = screen;
        } else if (mc.screen != null) {
            inventoryScreen = null;
        }
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (inventoryScreen != null && event.getPacket() instanceof ServerboundContainerClosePacket) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onLevelUpdate(LevelUpdateEvent event) {
        inventoryScreen = null;
    }
}
