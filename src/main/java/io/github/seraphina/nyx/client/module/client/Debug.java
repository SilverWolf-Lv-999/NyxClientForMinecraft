package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.debug.name", description = "nyxclient.module.debug.description", category = Category.CLIENT)
public class Debug extends Module {
    public static final Debug INSTANCE = new Debug();
}
