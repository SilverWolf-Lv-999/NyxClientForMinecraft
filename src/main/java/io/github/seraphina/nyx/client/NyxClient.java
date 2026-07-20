package io.github.seraphina.nyx.client;

import io.github.seraphina.nyx.client.alt.AltManager;
import io.github.seraphina.nyx.client.events.api.EventManager;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.SetScreenEvent;
import io.github.seraphina.nyx.client.manager.*;
import io.github.seraphina.nyx.client.music.NeteaseMusicLocalService;
import io.github.seraphina.nyx.client.ui.mainui.MainUI;
import io.github.seraphina.nyx.client.utility.SeraNative;
import io.github.seraphina.nyx.client.utility.StringUtility;
import io.github.seraphina.nyx.client.utility.render.Shaders;
import io.github.seraphina.nyx.client.via.NyxViaForge;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11C;

public class NyxClient {
    public static final NyxClient INSTANCE = new NyxClient();
    public static final String CLIENT_NAME = "Nyx";
    public static final String VERSION = "1.0-Alpha";
    public static final Logger LOGGER = LogManager.getLogger(CLIENT_NAME);

    public void init() {
        LOGGER.info("Initializing NyxClient");
        LOGGER.info("Sera native acceleration hints {}", SeraNative.loadStatus());
        LOGGER.info("Native high-performance GPU request status {}", SeraNative.requestNativeHighPerformanceGpu());
        LOGGER.info("Windows high-performance GPU preference status {}", SeraNative.ensureHighPerformanceGpuPreference());
        NyxViaForge.init();
        logActiveOpenGlGpu();
        System.out.print(StringUtility.readStringInProject(NyxClient.class, "assets/nyxclient/nyx.txt"));
        System.out.println();
        Shaders.init();
        ModuleManager.init();
        CommandManager.init();
        ConfigManager.init();
        FriendManager.init();
        AltManager.init();
        HUDManager.load();
        EventManager.register(this);
        EventManager.register(KeyManager.INSTANCE);
        CommandManager.init();
        RotationManager.INSTANCE.getClass();
        FontManager.init();
        NeteaseMusicLocalService.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            NeteaseMusicLocalService.stop();
            Shaders.close();
            LOGGER.info("Shutting down NyxClient");
        }));
    }

    @EventTarget
    public static void setScreen(SetScreenEvent event) {
        if (event.getScreen() instanceof TitleScreen || event.getScreen() instanceof JoinMultiplayerScreen)
            event.setScreen(new MainUI());
    }

    private static void logActiveOpenGlGpu() {
        try {
            LOGGER.info(
                    "Nyx active OpenGL GPU renderer: {} ({})",
                    GL11C.glGetString(GL11C.GL_RENDERER),
                    GL11C.glGetString(GL11C.GL_VENDOR)
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("Nyx cannot query active OpenGL GPU renderer yet", exception);
        }
    }
}
