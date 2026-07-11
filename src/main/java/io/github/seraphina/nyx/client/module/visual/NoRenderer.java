package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;

@ModuleInfo(name = "nyxclient.module.norenderer.name", description = "nyxclient.module.norenderer.description", category = Category.VISUAL)
public class NoRenderer extends Module {
    public static final NoRenderer INSTANCE = new NoRenderer();

    public final BoolValue nohurtcamera = ValueBuild.boolSetting("nohurtcamera", false, this);

    public final BoolValue noview = ValueBuild.boolSetting("noview", false, this);

    public final BoolValue noparticles = ValueBuild.boolSetting("noparticles", false, this);

    public final BoolValue totemAnimation = ValueBuild.boolSetting("totem animation", false, this);

    public final BoolValue deathEntity = ValueBuild.boolSetting("death entity", false, this);

    public final BoolValue pumpkin = ValueBuild.boolSetting("pumpkin", false, this);
}
