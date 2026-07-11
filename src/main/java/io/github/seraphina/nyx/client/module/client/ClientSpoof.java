package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.StringValue;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;

@ModuleInfo(name = "nyxclient.module.clientspoof.name", description = "nyxclient.module.clientspoof.description", category = Category.CLIENT)
public class ClientSpoof extends Module {
    public static final ClientSpoof INSTANCE = new ClientSpoof();
    private static final int MAX_BRAND_LENGTH = 32767;

    public final StringValue brand = ValueBuild.stringSetting("brand", "vanilla", this);

    @Override
    public void onEnable() {
        sendBrand(getSpoofedBrand());
    }

    @Override
    public void onDisable() {
        sendBrand(ClientBrandRetriever.getClientModName());
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundCustomPayloadPacket packet
                && packet.payload() instanceof BrandPayload) {
            event.setPacket(new ServerboundCustomPayloadPacket(new BrandPayload(getSpoofedBrand())));
        }
    }

    public String getSpoofedBrand() {
        String value = this.brand.getValue();
        if (value == null) {
            return ClientBrandRetriever.VANILLA_NAME;
        }

        value = value.trim();
        if (value.isEmpty()) {
            return ClientBrandRetriever.VANILLA_NAME;
        }

        if (value.length() > MAX_BRAND_LENGTH) {
            return value.substring(0, MAX_BRAND_LENGTH);
        }

        return value;
    }

    private void sendBrand(String brand) {
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(new BrandPayload(brand)));
        }
    }
}
