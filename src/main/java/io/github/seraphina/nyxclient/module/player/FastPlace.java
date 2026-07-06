package io.github.seraphina.nyxclient.module.player;

import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.TickEvent;
import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;
import io.github.seraphina.nyxclient.value.ValueBuild;
import io.github.seraphina.nyxclient.value.impl.IntValue;
import net.minecraft.client.Minecraft;

@ModuleInfo(name = "nyxclient.module.fastplace.name", description = "nyxclient.module.fastplace.description", category = Category.PLAYER)
public class FastPlace extends Module {
    public static final FastPlace INSTANCE = new FastPlace();

    public final IntValue delay = ValueBuild.intSetting("delay", 2, 0, 10, 1, null);

    public FastPlace() {
        this.registerValue(
                delay
        );
    }

    @EventTarget
    public void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.rightClickDelay = delay.getValue();
    }
}
