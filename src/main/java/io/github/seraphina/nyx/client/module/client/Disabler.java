package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.LevelUpdateEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(name = "nyxclient.module.disabler.name", description = "nyxclient.module.disabler.description", category = Category.CLIENT)
public class Disabler extends Module {
    public static final Disabler INSTANCE = new Disabler();

    private static final long PACKET_DELAY_MS = 2_000L;
    private static final long SETBACK_WAIT_MS = 5_000L;

    private final Queue<DelayedPacket> packets = new ConcurrentLinkedQueue<>();

    private long waitingUntil;
    private boolean releasing;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        flushPackets();
        resetState();
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (releasing || isWaiting()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundPlayerCommandPacket commandPacket
                && commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
            event.setCancelled(true);
            return;
        }

        if (packet instanceof ServerboundKeepAlivePacket || packet instanceof ServerboundPongPacket) {
            packets.add(new DelayedPacket(packet, System.currentTimeMillis()));
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            waitingUntil = System.currentTimeMillis() + SETBACK_WAIT_MS;
            flushPackets();
        }
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (isNull() || mc.player.connection == null) {
            resetState();
            return;
        }

        releaseDuePackets();
    }

    @EventTarget
    public void onLevelUpdate(LevelUpdateEvent event) {
        resetState();
    }

    private boolean isWaiting() {
        return System.currentTimeMillis() < waitingUntil;
    }

    private void releaseDuePackets() {
        if (mc.player == null || mc.player.connection == null) {
            packets.clear();
            return;
        }

        long now = System.currentTimeMillis();
        releasing = true;
        try {
            DelayedPacket delayedPacket;
            while ((delayedPacket = packets.peek()) != null
                    && now - delayedPacket.queuedAt() >= PACKET_DELAY_MS) {
                packets.poll();
                mc.player.connection.send(delayedPacket.packet());
            }
        } finally {
            releasing = false;
        }
    }

    private void flushPackets() {
        if (mc.player == null || mc.player.connection == null) {
            packets.clear();
            return;
        }

        releasing = true;
        try {
            DelayedPacket delayedPacket;
            while ((delayedPacket = packets.poll()) != null) {
                mc.player.connection.send(delayedPacket.packet());
            }
        } finally {
            releasing = false;
        }
    }

    private void resetState() {
        packets.clear();
        waitingUntil = 0L;
        releasing = false;
    }

    private record DelayedPacket(Packet<?> packet, long queuedAt) {
    }
}
