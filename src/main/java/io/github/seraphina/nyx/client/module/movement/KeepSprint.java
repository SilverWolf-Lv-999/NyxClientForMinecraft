package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;

@ModuleInfo(name = "nyxclient.module.keepsprint.name", description = "nyxclient.module.keepsprint.description", category = Category.MOVEMENT)
public final class KeepSprint extends Module {
    public static final KeepSprint INSTANCE = new KeepSprint();

    public final DoubleValue motion = ValueBuild.doubleSetting("motion", 1.0D, 0.0D, 1.0D, 0.1D, this);
}
