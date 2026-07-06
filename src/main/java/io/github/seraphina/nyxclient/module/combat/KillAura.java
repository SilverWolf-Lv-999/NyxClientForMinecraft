package io.github.seraphina.nyxclient.module.combat;

import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;


@ModuleInfo(name = "nyxclient.module.killaura.name", description = "nyxclient.module.killaura.description", category = Category.PLAYER)
public class KillAura extends Module {
    public static final KillAura INSTANCE = new KillAura();
}
