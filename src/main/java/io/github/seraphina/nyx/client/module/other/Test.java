package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.test.name", description = "nyxclient.module.test.description", category = Category.OTHER)
public class Test extends Module {
    public static final Test INSTANCE = new Test();

    public Test() {

    }
}
