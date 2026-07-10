package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.friend.name", description = "nyxclient.module.friend.description", category = Category.CLIENT)
public class Friend extends Module {
    public static final Friend INSTANCE = new Friend();
}
