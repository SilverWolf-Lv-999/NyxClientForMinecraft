package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

@ModuleInfo(name = "nyxclient.module.antihunger.name", description = "nyxclient.module.antihunger.description", category = Category.PLAYER)
public final class AntiHunger extends Module {
    public static final AntiHunger INSTANCE = new AntiHunger();

    public final BoolValue cancelGround = ValueBuild.boolSetting("cancel ground", true, this);
    public final BoolValue cancelSprint = ValueBuild.boolSetting("cancel sprint", true, this);

    private AntiHunger() {
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (cancelGround.getValue() && packet instanceof ServerboundMovePlayerPacket movePacket && movePacket.isOnGround()) {
            event.setPacket(withoutGround(movePacket));
            return;
        }

        if (cancelSprint.getValue()
                && packet instanceof ServerboundPlayerCommandPacket commandPacket
                && commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
            event.setCancelled(true);
        }
    }

    private ServerboundMovePlayerPacket withoutGround(ServerboundMovePlayerPacket movePacket) {
        if (movePacket.hasPosition() && movePacket.hasRotation()) {
            return new ServerboundMovePlayerPacket.PosRot(
                    movePacket.getX(0.0D),
                    movePacket.getY(0.0D),
                    movePacket.getZ(0.0D),
                    movePacket.getYRot(0.0F),
                    movePacket.getXRot(0.0F),
                    false,
                    movePacket.horizontalCollision()
            );
        }

        if (movePacket.hasPosition()) {
            return new ServerboundMovePlayerPacket.Pos(
                    movePacket.getX(0.0D),
                    movePacket.getY(0.0D),
                    movePacket.getZ(0.0D),
                    false,
                    movePacket.horizontalCollision()
            );
        }

        if (movePacket.hasRotation()) {
            return new ServerboundMovePlayerPacket.Rot(
                    movePacket.getYRot(0.0F),
                    movePacket.getXRot(0.0F),
                    false,
                    movePacket.horizontalCollision()
            );
        }

        return new ServerboundMovePlayerPacket.StatusOnly(false, movePacket.horizontalCollision());
    }
}
