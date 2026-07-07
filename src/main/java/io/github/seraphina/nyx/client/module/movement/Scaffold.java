package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.scaffold.name", description = "nyxclient.module.scaffold.description", category = Category.MOVEMENT)
public class Scaffold extends Module {
    public static final Scaffold INSTANCE = new Scaffold();

    public Scaffold() {

    }
}
