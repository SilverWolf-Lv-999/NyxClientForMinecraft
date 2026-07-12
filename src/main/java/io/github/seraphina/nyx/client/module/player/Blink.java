package io.github.seraphina.nyx.client.module.player;

import com.mojang.authlib.GameProfile;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.RespawnEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import io.github.seraphina.nyx.client.value.impl.StringValue;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "nyxclient.module.blink.name", description = "nyxclient.module.blink.description", category = Category.PLAYER)
public class Blink extends Module {
    public static final Blink INSTANCE = new Blink();

    private static final int BLINK_ENTITY_ID = -66123667;
    private static final int MAX_PROFILE_NAME_LENGTH = 16;

    public final BoolValue pulse = ValueBuild.boolSetting("pulse", false, this);
    public final IntValue minDelay = ValueBuild.intSetting("min delay", 2, 1, 40, 1, () -> pulse.getValue(), this);
    public final IntValue maxDelay = ValueBuild.intSetting("max delay", 2, 1, 40, 1, () -> pulse.getValue(), this);
    public final BoolValue fakePlayer = ValueBuild.boolSetting("fake player", true, this);
    public final StringValue fakePlayerName = ValueBuild.stringSetting("fake player name", "Nyx_Blink", () -> fakePlayer.getValue(), this);
    public final BoolValue copyInventory = ValueBuild.boolSetting("copy inventory", true, () -> fakePlayer.getValue(), this);

    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();

    private RemotePlayer blinkEntity;
    private int nextPulseTick;
    private boolean releasing;

    @Override
    public void onEnable() {
        packets.clear();

        if (isNull() || mc.player.connection == null) {
            setEnabled(false);
            return;
        }

        scheduleNextPulse();
        spawnFakePlayer();
    }

    @Override
    public void onDisable() {
        flushPackets();
        removeFakePlayer();
        nextPulseTick = 0;
        releasing = false;
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (releasing || !shouldBlink(event.getPacket())) {
            return;
        }

        packets.add(event.getPacket());
        event.setCancelled(true);
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (isNull() || mc.player.connection == null) {
            packets.clear();
            setEnabled(false);
            return;
        }

        if (!pulse.getValue()) {
            return;
        }

        if (nextPulseTick <= 0) {
            scheduleNextPulse();
            return;
        }

        if (mc.player.tickCount >= nextPulseTick) {
            flushPackets();
            removeFakePlayer();
            scheduleNextPulse();
            spawnFakePlayer();
        }
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        packets.clear();
        removeFakePlayer();
        scheduleNextPulse();
        spawnFakePlayer();
    }

    private void flushPackets() {
        if (mc.player == null || mc.player.connection == null || packets.isEmpty()) {
            packets.clear();
            return;
        }

        releasing = true;
        try {
            Packet<?> packet;
            while ((packet = packets.poll()) != null) {
                mc.player.connection.send(packet);
            }
        } finally {
            releasing = false;
        }
    }

    private void spawnFakePlayer() {
        if (!fakePlayer.getValue() || mc.player == null || mc.level == null) {
            return;
        }

        removeFakePlayer();

        String name = fakePlayerName();

        UUID uuid = UUID.nameUUIDFromBytes(("blink-player:" + mc.player.getUUID()).getBytes(StandardCharsets.UTF_8));
        blinkEntity = new RemotePlayer(mc.level, new GameProfile(uuid, name));
        blinkEntity.setId(BLINK_ENTITY_ID);
        blinkEntity.copyPosition(mc.player);
        blinkEntity.setYRot(mc.player.getYRot());
        blinkEntity.setXRot(mc.player.getXRot());
        blinkEntity.setYHeadRot(mc.player.getYHeadRot());
        blinkEntity.setYBodyRot(mc.player.yBodyRot);
        blinkEntity.setDeltaMovement(mc.player.getDeltaMovement());
        blinkEntity.setHealth(mc.player.getHealth());
        blinkEntity.setAbsorptionAmount(mc.player.getAbsorptionAmount());
        blinkEntity.setPose(mc.player.getPose());
        blinkEntity.setShiftKeyDown(mc.player.isShiftKeyDown());
        blinkEntity.setSprinting(mc.player.isSprinting());

        if (copyInventory.getValue()) {
            blinkEntity.getInventory().replaceWith(mc.player.getInventory());
        }

        mc.level.addEntity(blinkEntity);
    }

    private String fakePlayerName() {
        String name = fakePlayerName.getValue();
        if (name == null || name.isBlank()) {
            return "Nyx_Blink";
        }

        return name.length() > MAX_PROFILE_NAME_LENGTH ? name.substring(0, MAX_PROFILE_NAME_LENGTH) : name;
    }

    private void removeFakePlayer() {
        if (mc.level != null && blinkEntity != null) {
            mc.level.removeEntity(blinkEntity.getId(), Entity.RemovalReason.DISCARDED);
        }

        blinkEntity = null;
    }

    private void scheduleNextPulse() {
        if (mc.player == null) {
            nextPulseTick = 0;
            return;
        }

        int min = Math.min(minDelay.getValue(), maxDelay.getValue());
        int max = Math.max(minDelay.getValue(), maxDelay.getValue());
        nextPulseTick = mc.player.tickCount + ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private boolean shouldBlink(Packet<?> packet) {
        return packet != null
                && packet.getClass().getPackageName().equals("net.minecraft.network.protocol.game")
                && packet.getClass().getSimpleName().startsWith("Serverbound");
    }
}
