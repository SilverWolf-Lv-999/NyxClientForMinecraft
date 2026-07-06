package io.github.seraphina.nyxclient.module.movement;

import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.scaffold.name", description = "nyxclient.module.scaffold.description", category = Category.MOVEMENT)
public class Scaffold extends Module {
    public static final Scaffold INSTANCE = new Scaffold();

    public Scaffold() {

    }
}
