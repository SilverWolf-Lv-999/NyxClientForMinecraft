package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

@ModuleInfo(name = "nyxclient.module.nodownload.name", description = "nyxclient.module.nodownload.description", category = Category.OTHER)
public class NoDownload extends Module {
    public static final NoDownload INSTANCE = new NoDownload();

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundResourcePackPushPacket packet)) {
            return;
        }

        Connection connection = event.getConnection();
        if (connection == null) {
            return;
        }

        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.DOWNLOADED));
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        event.setCancelled(true);
    }
}
