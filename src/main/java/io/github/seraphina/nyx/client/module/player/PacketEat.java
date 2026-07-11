package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

@ModuleInfo(name = "nyxclient.module.packeteat.name", description = "nyxclient.module.packeteat.description", category = Category.PLAYER)
public class PacketEat extends Module {
    public static final PacketEat INSTANCE = new PacketEat();

    private ItemStack item = ItemStack.EMPTY;

    @Override
    public void onDisable() {
        item = ItemStack.EMPTY;
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            item = ItemStack.EMPTY;
            return;
        }

        if (mc.player.isUsingItem()) {
            item = mc.player.getUseItem();
        }
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundPlayerActionPacket packet)
                || packet.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            return;
        }

        FoodProperties foodProperties = item.get(DataComponents.FOOD);
        if (foodProperties != null && foodProperties.canAlwaysEat()) {
            event.setCancelled(true);
        }
    }
}
