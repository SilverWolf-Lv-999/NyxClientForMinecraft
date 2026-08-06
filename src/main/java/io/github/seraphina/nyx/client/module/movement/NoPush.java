package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.nopush.name", description = "nyxclient.module.nopush.description", category = Category.MOVEMENT)
public final class NoPush extends Module {
    public static final NoPush INSTANCE = new NoPush();
}
