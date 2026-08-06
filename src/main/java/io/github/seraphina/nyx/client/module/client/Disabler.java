package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.LevelUpdateEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.player.AutoElytra;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Input;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(name = "nyxclient.module.disabler.name", description = "nyxclient.module.disabler.description", category = Category.CLIENT)
public class Disabler extends Module {
    public static final Disabler INSTANCE = new Disabler();

    private static final long PACKET_DELAY_MS = 2_000L;
    private static final long SETBACK_WAIT_MS = 5_000L;

    private final Queue<DelayedPacket> cubeCraftPackets = new ConcurrentLinkedQueue<>();

    private long cubeCraftWaitingUntil;
    private boolean releasingCubeCraftPackets;

    private int lastGrimSentSlot = -1;
    private boolean releasingGrimPacket;
    private boolean shouldRestoreInput;
    private Input oldInput;

    public final BoolValue cubeCraft = ValueBuild.boolSetting("Cube Craft", false, this);
    public final BoolValue grimAC = ValueBuild.boolSetting("GrimAC", false, this);
    public final BoolValue badPacketsA = ValueBuild.boolSetting("Bad PacketsA", false, grimAC::getValue, this);
    public final BoolValue sprinting = ValueBuild.boolSetting("Sprinting", false, grimAC::getValue, this);
    public final BoolValue input = ValueBuild.boolSetting("Input", false, grimAC::getValue, this);

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        flushCubeCraftPackets();
        resetState();
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (cubeCraft.getValue()) {
            handleCubeCraftPacket(event, packet);
        }

        if (event.isCancelled() || !grimAC.getValue() || releasingGrimPacket || isNull() || mc.player.connection == null) {
            return;
        }

        handleGrimPacket(event, packet);
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (cubeCraft.getValue() && event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            cubeCraftWaitingUntil = System.currentTimeMillis() + SETBACK_WAIT_MS;
            flushCubeCraftPackets();
        }
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (isNull() || mc.player.connection == null) {
            resetState();
            return;
        }

        if (cubeCraft.getValue()) {
            releaseDueCubeCraftPackets();
        } else {
            flushCubeCraftPackets();
        }
    }

    @EventTarget
    public void onLevelUpdate(LevelUpdateEvent event) {
        resetState();
    }

    private void handleCubeCraftPacket(PacketEvent.Send event, Packet<?> packet) {
        if (releasingCubeCraftPackets || isCubeCraftWaiting()) {
            return;
        }

        if (packet instanceof ServerboundPlayerCommandPacket commandPacket
                && commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
            event.setCancelled(true);
            return;
        }

        if (packet instanceof ServerboundKeepAlivePacket || packet instanceof ServerboundPongPacket) {
            cubeCraftPackets.add(new DelayedPacket(packet, System.currentTimeMillis()));
            event.setCancelled(true);
        }
    }

    private void handleGrimPacket(PacketEvent.Send event, Packet<?> packet) {
        if (badPacketsA.getValue() && packet instanceof ServerboundSetCarriedItemPacket setCarriedItemPacket) {
            int slot = setCarriedItemPacket.getSlot();
            if (slot == lastGrimSentSlot && slot != -1) {
                event.setCancelled(true);
            }
            lastGrimSentSlot = slot;
        }

        if (!(packet instanceof ServerboundContainerClickPacket)
                && !(packet instanceof ServerboundContainerClosePacket)) {
            return;
        }

        event.setCancelled(true);

        boolean wasSprinting = sprinting.getValue() && mc.player.isSprinting();
        if (input.getValue() && !AutoElytra.INSTANCE.isHandlingTakeoff()) {
            spoofInput();
        }

        releasingGrimPacket = true;
        try {
            if (wasSprinting) {
                sendGrimSprintingState(false);
            }

            mc.player.connection.send(packet);
        } finally {
            if (wasSprinting) {
                sendGrimSprintingState(true);
            }

            if (shouldRestoreInput) {
                restoreInput();
            }

            releasingGrimPacket = false;
        }
    }

    private boolean isCubeCraftWaiting() {
        return System.currentTimeMillis() < cubeCraftWaitingUntil;
    }

    private void releaseDueCubeCraftPackets() {
        if (mc.player == null || mc.player.connection == null) {
            cubeCraftPackets.clear();
            return;
        }

        long now = System.currentTimeMillis();
        releasingCubeCraftPackets = true;
        try {
            DelayedPacket delayedPacket;
            while ((delayedPacket = cubeCraftPackets.peek()) != null
                    && now - delayedPacket.queuedAt() >= PACKET_DELAY_MS) {
                cubeCraftPackets.poll();
                mc.player.connection.send(delayedPacket.packet());
            }
        } finally {
            releasingCubeCraftPackets = false;
        }
    }

    private void flushCubeCraftPackets() {
        if (mc.player == null || mc.player.connection == null) {
            cubeCraftPackets.clear();
            return;
        }

        releasingCubeCraftPackets = true;
        try {
            DelayedPacket delayedPacket;
            while ((delayedPacket = cubeCraftPackets.poll()) != null) {
                mc.player.connection.send(delayedPacket.packet());
            }
        } finally {
            releasingCubeCraftPackets = false;
        }
    }

    private void sendGrimSprintingState(boolean sprintingState) {
        mc.player.setSprinting(sprintingState);
        mc.player.wasSprinting = sprintingState;
        mc.player.connection.send(new ServerboundPlayerCommandPacket(
                mc.player,
                sprintingState
                        ? ServerboundPlayerCommandPacket.Action.START_SPRINTING
                        : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
        ));
    }

    private void spoofInput() {
        if (shouldRestoreInput) {
            return;
        }

        oldInput = mc.player.input.keyPresses;
        mc.player.input.keyPresses = Input.EMPTY;
        mc.player.connection.send(new ServerboundPlayerInputPacket(Input.EMPTY));
        mc.player.lastSentInput = Input.EMPTY;
        shouldRestoreInput = true;
    }

    private void restoreInput() {
        if (!shouldRestoreInput) {
            return;
        }

        mc.player.input.keyPresses = oldInput;
        oldInput = null;
        shouldRestoreInput = false;
    }

    private void resetState() {
        cubeCraftPackets.clear();
        cubeCraftWaitingUntil = 0L;
        releasingCubeCraftPackets = false;
        lastGrimSentSlot = -1;
        releasingGrimPacket = false;
        restoreInput();
    }

    private record DelayedPacket(Packet<?> packet, long queuedAt) {
    }
}
