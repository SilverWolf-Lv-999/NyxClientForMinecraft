/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 * Copyright (C) 2021-2026 Florian Reuth <git@florianreuth.de> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.seraphina.nyx.client.mixins.via.connect;

import io.github.seraphina.nyx.client.via.common.ViaForgeCommon;
import io.github.seraphina.nyx.client.via.common.extended.ExtendedServerData;
import io.github.seraphina.nyx.client.via.common.platform.VersionTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.Connection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;

@Mixin(ServerStatusPinger.class)
public class MixinServerStatusPinger {

    @Unique
    private final ThreadLocal<ServerData> nyxVia$serverData = new ThreadLocal<>();

    @Inject(method = "pingServer", at = @At("HEAD"))
    public void trackServerData(ServerData p_105460_, Runnable p_105461_, Runnable p_335024_, EventLoopGroupHolder p_453907_, CallbackInfo ci) {
        nyxVia$serverData.set(p_105460_);
    }

    @Inject(method = "pingServer", at = @At("RETURN"))
    public void clearServerData(ServerData p_105460_, Runnable p_105461_, Runnable p_335024_, EventLoopGroupHolder p_453907_, CallbackInfo ci) {
        nyxVia$serverData.remove();
    }

    @Redirect(method = "pingServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connectToServer(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;"))
    public Connection trackVersion(InetSocketAddress inetSocketAddress, EventLoopGroupHolder eventLoopGroupHolder, LocalSampleLogger localSampleLogger) {
        ServerData serverData = nyxVia$serverData.get();
        nyxVia$serverData.remove();

        ProtocolVersion version = serverData instanceof ExtendedServerData extendedServerData
            ? extendedServerData.viaForge$getVersion()
            : null;
        ViaForgeCommon manager = ViaForgeCommon.getManager();
        if (version == null && manager != null) {
            version = manager.getTargetVersion();
        }
        if (version != null) {
            VersionTracker.storeServerProtocolVersion(inetSocketAddress, version);
        }

        return Connection.connectToServer(inetSocketAddress, eventLoopGroupHolder, localSampleLogger);
    }

}
