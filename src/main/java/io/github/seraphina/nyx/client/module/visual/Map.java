package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.map.name", description = "nyxclient.module.map.description", category = Category.VISUAL)
public class Map extends Module {
    public static final Map INSTANCE = new Map();
}
