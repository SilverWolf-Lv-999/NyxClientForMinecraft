package io.github.seraphina.nyxclient.manager;

import net.neoforged.fml.loading.FMLPaths;

public class PathManager {
    public static final String CLIENT = FMLPaths.GAMEDIR.get().toString() + "/nyxclient";
    public static final String CONFIG = CLIENT + "/config";
    public static final String LOG = CLIENT + "/logs";
    public static final String CAGE = CLIENT + "/cages";
    public static final String HUD = CLIENT + "/gui";
}
