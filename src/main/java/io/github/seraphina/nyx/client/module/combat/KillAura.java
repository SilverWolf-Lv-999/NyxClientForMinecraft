package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;


@ModuleInfo(name = "nyxclient.module.killaura.name", description = "nyxclient.module.killaura.description", category = Category.COMBAT)
public class KillAura extends Module {
    public static final KillAura INSTANCE = new KillAura();
}
