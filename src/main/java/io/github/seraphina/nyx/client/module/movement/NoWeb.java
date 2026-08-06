package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;

@ModuleInfo(name = "nyxclient.module.noweb.name", description = "nyxclient.module.noweb.description", category = Category.MOVEMENT)
public class NoWeb extends Module {
    public static final NoWeb INSTANCE = new NoWeb();

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.VANILLA, this);

    public enum Mode {
        VANILLA,
        GRIM
    }
}
