package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.silentrotation.name", description = "nyxclient.module.silentrotation.description", category = Category.CLIENT)
public class SilentRotation extends Module {
    public static final SilentRotation INSTANCE = new SilentRotation();
}
