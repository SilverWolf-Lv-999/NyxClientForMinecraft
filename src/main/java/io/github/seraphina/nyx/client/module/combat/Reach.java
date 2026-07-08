package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;

@ModuleInfo(name = "nyxclient.module.reach.name", description = "nyxclient.module.reach.description", category = Category.COMBAT)
public class Reach extends Module {
    public static final Reach INSTANCE = new Reach();

    private static final double DEFAULT_RANGE = 3.0D;

    public final DoubleValue range = ValueBuild.doubleValue("range", DEFAULT_RANGE, 0.1D, 8.0D, 0.1D, this);

    public double getEntityRange(double original) {
        return isEnabled() ? range.getValue() : original;
    }
}
