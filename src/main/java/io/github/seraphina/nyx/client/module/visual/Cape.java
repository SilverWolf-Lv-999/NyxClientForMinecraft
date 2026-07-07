package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.cape.name", description = "nyxclient.module.cape.description", category = Category.VISUAL)
public class Cape extends Module {
    public static final Cape INSTANCE = new Cape();
}
