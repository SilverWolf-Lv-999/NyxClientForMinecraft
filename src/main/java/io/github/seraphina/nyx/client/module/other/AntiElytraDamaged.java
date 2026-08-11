package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.item.Items;

@ModuleInfo(name = "nyxclient.module.antielytradamaged.name", description = "nyxclient.module.antielytradamaged.description", category = Category.OTHER)
public class AntiElytraDamaged extends Module {
    public static final AntiElytraDamaged INSTANCE = new AntiElytraDamaged();

    public final BoolValue antiStop = ValueBuild.boolValue("Anti Stop", true, this);
    public final IntValue keepElytraTicks = ValueBuild.intSetting("keep elytra ticks", 2, 1, 19, 1, this);

    private int elytraTicks;

    @Override
    public void onDisable() {
        elytraTicks = 0;
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canCycleElytra()) {
            elytraTicks = 0;
            return;
        }

        if (++elytraTicks < keepElytraTicks.getValue()) {
            return;
        }

        elytraTicks = 0;
        int emptySlot = InventoryUtility.findEmptySlot();
        if (emptySlot != InventoryUtility.NOT_FOUND && emptySlot != InventoryUtility.ARMOR_CHEST_SLOT
                && InventoryUtility.swapInventorySlots(InventoryUtility.ARMOR_CHEST_SLOT, emptySlot)) {
            InventoryUtility.swapInventorySlots(InventoryUtility.ARMOR_CHEST_SLOT, emptySlot);
        }
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!antiStop.getValue()
                || mc.player == null
                || !mc.player.isFallFlying()
                || !(event.getPacket() instanceof ClientboundSetEntityDataPacket packet)
                || packet.id() != mc.player.getId()) {
            return;
        }

        boolean stopsFallFlying = packet.packedItems().stream().anyMatch(dataValue ->
                dataValue.id() == 0
                        && dataValue.value() instanceof Byte flags
                        && (flags & (1 << 7)) == 0
        );
        if (stopsFallFlying) {
            event.setCancelled(true);
        }
    }

    private boolean canCycleElytra() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.player.connection != null
                && !InventoryUtility.hasContainerOpen()
                && !InventoryUtility.hasCarriedStack()
                && mc.player.isFallFlying()
                && InventoryUtility.getStack(InventoryUtility.ARMOR_CHEST_SLOT).is(Items.ELYTRA);
    }
}
