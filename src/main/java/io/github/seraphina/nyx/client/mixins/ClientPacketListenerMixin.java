package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.RespawnEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void onHandleRespawnReturn(ClientboundRespawnPacket packet, CallbackInfo info) {
        EventBus.INSTANCE.post(new RespawnEvent());
    }
}
