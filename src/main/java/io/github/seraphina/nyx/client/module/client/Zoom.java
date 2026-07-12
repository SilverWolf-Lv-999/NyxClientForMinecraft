package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.zoom.name", description = "nyxclient.module.zoom.description", category = Category.CLIENT)
public class Zoom extends Module {
    public static final Zoom INSTANCE = new Zoom();
}
