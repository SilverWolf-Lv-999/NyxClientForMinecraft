package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;

@ModuleInfo(name = "nyxclient.module.blur.name", description = "nyxclient.module.blur.description", category = Category.VISUAL)
public class Blur extends Module {
    public static final Blur INSTANCE = new Blur();

    public final DoubleValue power = ValueBuild.doubleSetting("power", 1.0D, 0.1D, 5.0D, 0.1D, this);
}
