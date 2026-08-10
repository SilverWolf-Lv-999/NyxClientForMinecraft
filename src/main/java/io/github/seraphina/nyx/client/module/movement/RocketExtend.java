package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.LevelUpdateEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(name = "nyxclient.module.rocketextend.name", description = "nyxclient.module.rocketextend.description", category = Category.MOVEMENT)
public class RocketExtend extends Module {
    public static final RocketExtend INSTANCE = new RocketExtend();

    public final DoubleValue time = ValueBuild.doubleSetting("time", 10.0D, 0.0D, 50.0D, 0.1D, this);

    private final Queue<ServerboundPongPacket> queuedPongs = new ConcurrentLinkedQueue<>();

    private volatile boolean extendingFirework;
    private volatile FireworkRocketEntity firework;
    private volatile long extensionStartedAt;

    @Override
    public void onEnable() {
        clearState();
    }

    @Override
    public void onDisable() {
        finishExtension();
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!extendingFirework) {
            return;
        }

        if (isNull()
                || !mc.player.isFallFlying()
                || mc.player.onGround()
                || firework == null
                || !firework.isAlive()
                || System.currentTimeMillis() - extensionStartedAt >= maximumExtensionMillis()) {
            finishExtension();
        }
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (extendingFirework
                && mc.player != null
                && mc.player.isFallFlying()
                && event.getPacket() instanceof ServerboundPongPacket packet) {
            queuedPongs.add(packet);
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket && extendingFirework) {
            finishExtension();
            return;
        }

        if (!(event.getPacket() instanceof ClientboundRemoveEntitiesPacket packet)
                || mc.player == null
                || mc.level == null
                || !mc.player.isFallFlying()) {
            return;
        }

        IntArrayList remainingIds = new IntArrayList(packet.getEntityIds().size());
        boolean retainedFirework = false;
        for (int entityId : packet.getEntityIds()) {
            if (shouldRetain(entityId)) {
                retainedFirework = true;
                continue;
            }
            remainingIds.add(entityId);
        }

        if (!retainedFirework) {
            return;
        }

        if (remainingIds.isEmpty()) {
            event.setCancelled(true);
        } else {
            event.setPacket(new ClientboundRemoveEntitiesPacket(remainingIds));
        }
    }

    @EventTarget
    public void onLevelUpdate(LevelUpdateEvent event) {
        finishExtension();
    }

    private boolean shouldRetain(int entityId) {
        FireworkRocketEntity trackedFirework = firework;
        if (extendingFirework && trackedFirework != null && trackedFirework.getId() == entityId) {
            return true;
        }

        if (extendingFirework) {
            return false;
        }

        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof FireworkRocketEntity rocket) || rocket.getOwner() != mc.player) {
            return false;
        }

        extendingFirework = true;
        firework = rocket;
        extensionStartedAt = System.currentTimeMillis();
        return true;
    }

    private long maximumExtensionMillis() {
        return (long) (time.getValue() * 1000.0D);
    }

    private void finishExtension() {
        FireworkRocketEntity trackedFirework = firework;
        clearState();

        if (mc.level != null && trackedFirework != null) {
            mc.level.removeEntity(trackedFirework.getId(), Entity.RemovalReason.DISCARDED);
        }

        flushQueuedPongs();
    }

    private void clearState() {
        extendingFirework = false;
        firework = null;
        extensionStartedAt = 0L;
    }

    private void flushQueuedPongs() {
        if (mc.player == null || mc.player.connection == null) {
            queuedPongs.clear();
            return;
        }

        ServerboundPongPacket packet;
        while ((packet = queuedPongs.poll()) != null) {
            mc.player.connection.send(packet);
        }
    }
}
