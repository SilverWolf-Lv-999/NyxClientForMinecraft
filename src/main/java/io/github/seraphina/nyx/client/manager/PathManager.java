package io.github.seraphina.nyx.client.manager;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PathManager {
    public static final Path CLIENT_PATH = FMLPaths.GAMEDIR.get().resolve("nyxclient");
    public static final Path CONFIG_PATH = CLIENT_PATH.resolve("config");
    public static final Path LOG_PATH = CLIENT_PATH.resolve("logs");
    public static final Path CAGE_PATH = CLIENT_PATH.resolve("cages");
    public static final Path HUD_PATH = CLIENT_PATH.resolve("gui");
    public static final Path BACK_GROUND_PATH = HUD_PATH.resolve("background");
    public static final Path FRIEND = CLIENT_PATH.resolve("friend");

    public static final String CLIENT = CLIENT_PATH.toString();
    public static final String CONFIG = CONFIG_PATH.toString();
    public static final String LOG = LOG_PATH.toString();
    public static final String CAGE = CAGE_PATH.toString();
    public static final String HUD = HUD_PATH.toString();
    public static final String BACKGROUND = BACK_GROUND_PATH.toString();
    public static final String friend = FRIEND.toString();

    private PathManager() {
    }

    public static void init() throws IOException {
        Files.createDirectories(CLIENT_PATH);
        Files.createDirectories(CONFIG_PATH);
        Files.createDirectories(LOG_PATH);
        Files.createDirectories(CAGE_PATH);
        Files.createDirectories(HUD_PATH);
        Files.createDirectories(BACK_GROUND_PATH);
        Files.createDirectories(FRIEND);
    }
}
