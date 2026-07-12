package io.github.seraphina.nyx.client.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.module.client.NetworkOptimization;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(method = "channelActive", at = @At("TAIL"))
    private void nyx$onChannelActive(ChannelHandlerContext context, CallbackInfo info) {
        NetworkOptimization.INSTANCE.registerConnection((Connection) (Object) this);
    }

    @Inject(method = "channelInactive", at = @At("TAIL"))
    private void nyx$onChannelInactive(ChannelHandlerContext context, CallbackInfo info) {
        NetworkOptimization.INSTANCE.unregisterConnection((Connection) (Object) this);
    }

    @WrapOperation(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"))
    private void onReceivePacket(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        PacketEvent.Receive event = EventBus.INSTANCE.post(new PacketEvent.Receive(packet, (Connection) (Object) this));
        if (!event.isCancelled()) {
            original.call(event.getPacket(), listener);
        }
    }

    @WrapOperation(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"))
    private void onSendPacket(Connection instance, Packet<?> packet, ChannelFutureListener listener, boolean flush, Operation<Void> original) {
        PacketEvent.Send event = EventBus.INSTANCE.post(new PacketEvent.Send(packet));
        if (!event.isCancelled()) {
            boolean deferredFlush = NetworkOptimization.INSTANCE.shouldDeferFlush(event.getPacket(), listener, flush);
            original.call(instance, event.getPacket(), listener, flush && !deferredFlush);
        }
    }
}
