package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

@ModuleInfo(name = "nyxclient.module.vclip.name", description = "nyxclient.module.vclip.description", category = Category.MOVEMENT)
public final class VClip extends Module {
    public static final VClip INSTANCE = new VClip();

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.JUMP, this);

    private VClip() {
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (mc.player == null || mc.player.connection == null) {
            setEnabled(false);
            return;
        }

        setEnabled(false);

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        boolean onGround = mc.player.onGround();

        switch (mode.getValue()) {
            case GLITCH -> {
                double roundedY = Math.round(y);
                sendPosition(x, roundedY, z, onGround);
                roundedY -= 0.005D;
                mc.player.setPos(x, roundedY, z);
                sendPosition(x, roundedY, z, onGround);
                roundedY -= 1.5D;
                mc.player.setPos(x, roundedY, z);
                sendPosition(x, roundedY, z, onGround);
            }
            case TELEPORT -> {
                mc.player.setPos(x, y + 3.0D, z);
                sendPosition(x, y + 3.0D, z, true);
            }
            case JUMP -> {
                sendPosition(x, y + 0.4199999868869781D, z, false);
                sendPosition(x, y + 0.7531999805212017D, z, false);
                mc.player.setPos(x, y + 1.0D, z);
                sendPosition(x, y + 1.0D, z, true);
            }
        }
    }

    private void sendPosition(double x, double y, double z, boolean onGround) {
        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                x,
                y,
                z,
                onGround,
                mc.player.horizontalCollision
        ));
    }

    public enum Mode {
        GLITCH,
        TELEPORT,
        JUMP
    }
}
