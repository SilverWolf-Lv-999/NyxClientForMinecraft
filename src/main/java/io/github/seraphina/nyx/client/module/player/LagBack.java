package io.github.seraphina.nyx.client.module.player;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.events.impl.RespawnEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "nyxclient.module.lagback.name", description = "nyxclient.module.lagback.description", category = Category.PLAYER)
public class LagBack extends Module {
    public static final LagBack INSTANCE = new LagBack();

    public final IntValue minMs = ValueBuild.intSetting("min ms", 120, 10, 1000, 1, this);
    public final IntValue maxMs = ValueBuild.intSetting("max ms", 200, 10, 1000, 1, this);
    public final BoolValue fill = ValueBuild.boolSetting("fill", true, this);
    public final BoolValue outline = ValueBuild.boolSetting("outline", true, this);
    public final IntValue fillAlpha = ValueBuild.intSetting("fill alpha", 40, 0, 255, 5, () -> fill.getValue(), this);
    public final IntValue outlineAlpha = ValueBuild.intSetting("outline alpha", 220, 0, 255, 5, () -> outline.getValue(), this);
    public final ColorValue color = ValueBuild.colorSetting("color", new Color(84, 170, 255), false, this);

    private final Queue<DelayedPacket> packets = new ConcurrentLinkedQueue<>();

    private Vec3 serverPosition;
    private boolean releasing;

    @Override
    public void onEnable() {
        packets.clear();

        if (isNull() || mc.player.connection == null) {
            setEnabled(false);
            return;
        }

        updateServerPositionFromPlayer();
    }

    @Override
    public void onDisable() {
        flushPackets();
        packets.clear();
        serverPosition = null;
        releasing = false;
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (releasing || !(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) {
            return;
        }

        packets.add(new DelayedPacket(packet, System.currentTimeMillis() + randomDelayMs()));
        event.setCancelled(true);
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (isNull() || mc.player.connection == null) {
            packets.clear();
            setEnabled(false);
            return;
        }

        releaseDuePackets();
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            packets.clear();
            updateServerPositionFromPlayer();
            setEnabled(false);
        }
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        packets.clear();
        updateServerPositionFromPlayer();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (isNull() || serverPosition == null) {
            return;
        }

        boolean renderFill = fill.getValue() && fillAlpha.getValue() > 0;
        boolean renderOutline = outline.getValue() && outlineAlpha.getValue() > 0;
        if (!renderFill && !renderOutline) {
            return;
        }

        AABB box = serverBox();
        int rgb = Render3DUtility.rgb(color.getValue().getRed(), color.getValue().getGreen(), color.getValue().getBlue());
        PoseStack poseStack = event.getPoseStack();
        if (renderFill) {
            Render3DUtility.renderFilledBoxNoDepth(poseStack, box, Render3DUtility.withAlpha(rgb, fillAlpha.getValue()));
        }
        if (renderOutline) {
            Render3DUtility.renderOutlineBoxNoDepth(poseStack, box, Render3DUtility.withAlpha(rgb, outlineAlpha.getValue()));
        }
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
            while ((delayedPacket = packets.peek()) != null && delayedPacket.releaseAt() <= now) {
                packets.poll();
                sendDelayedPacket(delayedPacket.packet());
            }
        } finally {
            releasing = false;
        }
    }

    private void flushPackets() {
        if (mc.player == null || mc.player.connection == null || packets.isEmpty()) {
            packets.clear();
            return;
        }

        releasing = true;
        try {
            DelayedPacket delayedPacket;
            while ((delayedPacket = packets.poll()) != null) {
                sendDelayedPacket(delayedPacket.packet());
            }
        } finally {
            releasing = false;
        }
    }

    private void sendDelayedPacket(Packet<?> packet) {
        updateServerPosition(packet);
        mc.player.connection.send(packet);
    }

    private void updateServerPosition(Packet<?> packet) {
        if (!(packet instanceof ServerboundMovePlayerPacket movePacket) || mc.player == null) {
            return;
        }

        Vec3 fallback = serverPosition != null ? serverPosition : mc.player.position();
        serverPosition = new Vec3(
                movePacket.getX(fallback.x),
                movePacket.getY(fallback.y),
                movePacket.getZ(fallback.z)
        );
    }

    private void updateServerPositionFromPlayer() {
        if (mc.player != null) {
            serverPosition = mc.player.position();
        } else {
            serverPosition = null;
        }
    }

    private AABB serverBox() {
        AABB box = mc.player.getBoundingBox();
        return box.move(
                serverPosition.x - mc.player.getX(),
                serverPosition.y - mc.player.getY(),
                serverPosition.z - mc.player.getZ()
        );
    }

    private long randomDelayMs() {
        int min = Math.min(minMs.getValue(), maxMs.getValue());
        int max = Math.max(minMs.getValue(), maxMs.getValue());
        return ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
    }

    private record DelayedPacket(Packet<?> packet, long releaseAt) {
    }
}
