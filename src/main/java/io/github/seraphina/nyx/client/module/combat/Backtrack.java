package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.AttackEntityEvent;
import io.github.seraphina.nyx.client.events.impl.LevelUpdateEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.RespawnEvent;
import io.github.seraphina.nyx.client.manager.ModuleManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.player.Blink;
import io.github.seraphina.nyx.client.module.player.LagBack;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Queue;

@ModuleInfo(name = "nyxclient.module.backtrack.name", description = "nyxclient.module.backtrack.description", category = Category.COMBAT)
public class Backtrack extends Module {
    public static final Backtrack INSTANCE = new Backtrack();

    public final DoubleValue searchRange = ValueBuild.doubleSetting("search range", 8.0D, 3.0D, 12.0D, 0.1D, this);
    public final DoubleValue releaseRange = ValueBuild.doubleSetting("release range", 1.0D, 0.2D, 3.0D, 0.1D, this);
    public final IntValue maxPackets = ValueBuild.intSetting("max packets", 70, 10, 150, 1, this);

    private final Queue<Packet<?>> packets = new ArrayDeque<>();

    private LivingEntity target;
    private boolean working;
    private boolean releasing;

    @Override
    public void onEnable() {
        working = false;
        target = null;
        packets.clear();
        releasing = false;
    }

    @Override
    public void onDisable() {
        flushPackets();
        target = null;
        working = false;
        releasing = false;
    }

    @EventTarget
    public void onAttack(AttackEntityEvent event) {
        if (event.getPlayer() != mc.player) {
            return;
        }

        if (hasConflict()) {
            stopWorking(true);
            return;
        }

        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity livingEntity) {
            target = livingEntity;
            working = true;
        } else {
            stopWorking(true);
        }
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (hasConflict() || isNull() || mc.player.connection == null) {
            stopWorking(true);
            return;
        }

        if (!isValidTarget(target)) {
            stopWorking(true);
            return;
        }

        if (mc.player.distanceTo(target) <= releaseRange.getValue()) {
            stopWorking(true);
            return;
        }

        if (target.hurtTime < 5) {
            sendUntilAttack();
        }
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled() || releasing) {
            return;
        }

        if (hasConflict()) {
            stopWorking(true);
            return;
        }

        if (packets.size() > maxPackets.getValue()) {
            sendOnePacket();
        }

        if (!working) {
            flushPackets();
            return;
        }

        Packet<?> packet = event.getPacket();
        if (!shouldBacktrack(packet)) {
            return;
        }

        packets.add(packet);
        event.setCancelled(true);
    }

    @EventTarget
    public void onLevelUpdate(LevelUpdateEvent event) {
        resetState();
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        resetState();
    }

    private boolean hasConflict() {
        return ModuleManager.getModule(Blink.class).map(Module::isEnabled).orElse(false)
                || ModuleManager.getModule(LagBack.class).map(Module::isEnabled).orElse(false);
    }

    private boolean isValidTarget(LivingEntity entity) {
        return mc.player != null
                && mc.level != null
                && entity != null
                && entity.isAlive()
                && mc.level.getEntity(entity.getId()) == entity
                && mc.player.distanceTo(entity) <= searchRange.getValue();
    }

    private boolean shouldBacktrack(Packet<?> packet) {
        return packet != null
                && mc.player != null
                && mc.player.connection != null
                && mc.player.tickCount > 60
                && packet.getClass().getPackageName().equals("net.minecraft.network.protocol.game")
                && packet.getClass().getSimpleName().startsWith("Serverbound");
    }

    private void stopWorking(boolean flush) {
        working = false;
        target = null;

        if (flush) {
            flushPackets();
        }
    }

    private void sendUntilAttack() {
        Packet<?> packet;
        do {
            packet = sendOnePacket();
        } while (packet != null && !(packet instanceof ServerboundInteractPacket));
    }

    private Packet<?> sendOnePacket() {
        Packet<?> packet = packets.poll();
        if (packet == null) {
            return null;
        }

        sendPacket(packet);
        return packet;
    }

    private void flushPackets() {
        Packet<?> packet;
        while ((packet = packets.poll()) != null) {
            sendPacket(packet);
        }
    }

    private void sendPacket(Packet<?> packet) {
        if (mc.player == null || mc.player.connection == null) {
            packets.clear();
            return;
        }

        releasing = true;
        try {
            mc.player.connection.send(packet);
        } finally {
            releasing = false;
        }
    }

    private void resetState() {
        packets.clear();
        target = null;
        working = false;
        releasing = false;
    }
}
