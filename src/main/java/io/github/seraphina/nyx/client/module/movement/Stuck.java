package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.ClickEvent;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.TravelEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

@ModuleInfo(name = "nyxclient.module.stuck.name", description = "nyxclient.module.stuck.description", category = Category.MOVEMENT)
public class Stuck extends Module {
    public static final Stuck INSTANCE = new Stuck();

    private static final double RELEASE_OFFSET = 1337.0D;

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.NO_PACKET, this);

    private float lastYaw;
    private float lastPitch;
    private boolean sendingMovePacket;

    public Stuck() {
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            lastYaw = mc.player.getYRot();
            lastPitch = mc.player.getXRot();
        }
    }

    @Override
    public void onDisable() {
        if (!mode.is(Mode.NO_PACKET) || mc.player == null || mc.player.onGround()) {
            return;
        }

        sendMovePacket(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX() + RELEASE_OFFSET,
                mc.player.getY(),
                mc.player.getZ() + RELEASE_OFFSET,
                mc.player.getYRot() + 0.01F,
                mc.player.getXRot(),
                mc.player.onGround(),
                mc.player.horizontalCollision
        ));
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        event.setForward(0.0F);
        event.setStrafe(0.0F);
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (mode.is(Mode.NO_PACKET) && !sendingMovePacket && event.getPacket() instanceof ServerboundMovePlayerPacket) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            setEnabled(false);
            return;
        }

        if (mode.is(Mode.NO_PACKET)
                && mc.player != null
                && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet
                && packet.getId() == mc.player.getId()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onTravel(TravelEvent event) {
        if (mode.is(Mode.CANCEL_MOVE) && mc.player != null && mc.player.positionReminder < 19) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onClick(ClickEvent event) {
        if (!mode.is(Mode.NO_PACKET) || mc.player == null) {
            return;
        }

        if (mc.player.getYRot() != lastYaw || mc.player.getXRot() != lastPitch) {
            sendMovePacket(new ServerboundMovePlayerPacket.Rot(
                    mc.player.getYRot(),
                    mc.player.getXRot(),
                    mc.player.onGround(),
                    mc.player.horizontalCollision
            ));
        }

        lastYaw = mc.player.getYRot();
        lastPitch = mc.player.getXRot();
    }

    private void sendMovePacket(ServerboundMovePlayerPacket packet) {
        if (mc.player == null || mc.player.connection == null) {
            return;
        }

        sendingMovePacket = true;
        try {
            mc.player.connection.send(packet);
        } finally {
            sendingMovePacket = false;
        }
    }

    public enum Mode {
        NO_PACKET("NoPacket"),
        CANCEL_MOVE("CancelMove");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
