package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.MsgUtility;
import io.github.seraphina.nyx.client.utility.player.TPUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.antilag.name", description = "nyxclient.module.antilag.description", category = Category.PLAYER)
public class AntiLag extends Module {
    public static final AntiLag INSTANCE = new AntiLag();

    public final BoolValue debug = ValueBuild.boolSetting("debug", false, this);
    public final DoubleValue range = ValueBuild.doubleSetting("range", 200.0D, 0.0D, 256.0D, 1.0D, this);
    public final DoubleValue moveDistance = ValueBuild.doubleSetting("move distance", 10.0D, 0.0D, 128.0D, 1.0D, this);

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket packet)
                || mc.player == null
                || !TPUtility.allowSendP()) {
            return;
        }

        Vec3 packetPosition = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(mc.player),
                packet.change(),
                packet.relatives()
        ).position();
        Vec3 playerPosition = mc.player.position();
        if (packetPosition.distanceTo(playerPosition) >= range.getValue()) {
            return;
        }

        event.setCancelled(true);
        mc.getConnection().send(new ServerboundAcceptTeleportationPacket(packet.id()));

        if (debug.getValue()) {
            MsgUtility.info("AntiLag intercepted correction: ", packetPosition);
        }

        TPUtility.doTp(packetPosition, playerPosition);
    }
}
