package io.github.seraphina.nyx.client.via;

import com.mojang.logging.LogUtils;
import io.github.seraphina.nyx.client.manager.PathManager;
import io.github.seraphina.nyx.client.via.common.ViaForgeCommon;
import io.github.seraphina.nyx.client.via.common.platform.ViaForgePlatform;
import io.github.seraphina.nyx.client.via.common.platform.ViaForgeProtocolBase;
import io.github.seraphina.nyx.client.via.platform.ViaForgeGameProfileFetcher;
import io.github.seraphina.nyx.client.via.platform.ViaForgeProtocol;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.HandlerNames;
import net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.provider.GameProfileFetcher;
import org.slf4j.Logger;

import java.io.File;

public final class NyxViaForge implements ViaForgePlatform {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized;

    public static void init() {
        if (initialized || ViaForgeCommon.getManager() != null) {
            initialized = true;
            return;
        }

        try {
            ViaForgeCommon.init(new NyxViaForge());
            initialized = true;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to initialize Nyx protocol translation", exception);
        }
    }

    @Override
    public int getGameVersion() {
        return SharedConstants.getProtocolVersion();
    }

    @Override
    public boolean isSingleplayer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.isSingleplayer();
    }

    @Override
    public File getLeadingDirectory() {
        return PathManager.CLIENT_PATH.toFile();
    }

    @Override
    public void joinServer(String serverId) throws Throwable {
        Minecraft minecraft = Minecraft.getInstance();

        User session = minecraft.getUser();
        minecraft.services().sessionService().joinServer(session.getProfileId(), session.getAccessToken(), serverId);
    }

    @Override
    public GameProfileFetcher getGameProfileFetcher() {
        return new ViaForgeGameProfileFetcher();
    }

    @Override
    public String getDecodeHandlerName() {
        return HandlerNames.INBOUND_CONFIG;
    }

    @Override
    public ViaForgeProtocolBase<?, ?, ?, ?> getCustomProtocol() {
        return ViaForgeProtocol.INSTANCE;
    }
}
