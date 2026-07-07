package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.Minecraft;

@ModuleInfo(name = "nyxclient.module.fastplace.name", description = "nyxclient.module.fastplace.description", category = Category.PLAYER)
public class FastPlace extends Module {
    public static final FastPlace INSTANCE = new FastPlace();

    public final IntValue delay = ValueBuild.intSetting("delay", 2, 0, 10, 1, this);

    public FastPlace() {
    }

    @EventTarget
    public void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.rightClickDelay = delay.getValue();
    }
}
