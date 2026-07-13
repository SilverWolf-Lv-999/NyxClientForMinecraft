package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;

@ModuleInfo(name = "nyxclient.module.antieffects.name", description = "nyxclient.module.antieffects.description", category = Category.PLAYER)
public class AntiEffects extends Module {
    public static final AntiEffects INSTANCE = new AntiEffects();

    public final BoolValue levitation = ValueBuild.boolSetting("levitation", true, this);
    public final BoolValue slowFalling = ValueBuild.boolSetting("slow falling", true, this);
}
