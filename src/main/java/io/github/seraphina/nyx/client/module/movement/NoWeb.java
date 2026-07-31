package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.noweb.name", description = "nyxclient.module.noweb.description", category = Category.MOVEMENT)
public class NoWeb extends Module {
    public static final NoWeb INSTANCE = new NoWeb();
}
