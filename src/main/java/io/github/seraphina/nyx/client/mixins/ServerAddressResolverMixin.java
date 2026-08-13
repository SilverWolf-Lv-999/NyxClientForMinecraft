package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.other.AntiWifiChange;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;

@Mixin(ServerAddressResolver.class)
public interface ServerAddressResolverMixin {
    @Inject(method = "lambda$static$0", at = @At("HEAD"), cancellable = true)
    private static void nyx$resolveWithoutDnsCache(ServerAddress address, CallbackInfoReturnable<Optional<ResolvedServerAddress>> info) {
        for (String numericAddress : AntiWifiChange.resolveHostnameWithoutCache(address.getHost())) {
            try {
                InetAddress inetAddress = InetAddress.getByName(numericAddress);
                info.setReturnValue(Optional.of(ResolvedServerAddress.from(new InetSocketAddress(inetAddress, address.getPort()))));
                return;
            } catch (UnknownHostException ignored) {
                // Continue to a second address from the native DNS response.
            }
        }
    }
}
