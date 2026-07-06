package io.github.seraphina.nyxclient.module.other;

import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.test.name", description = "nyxclient.module.test.description", category = Category.MOVEMENT)
public class Test extends Module {
    public static final Test INSTANCE = new Test();

    public Test() {

    }
}
