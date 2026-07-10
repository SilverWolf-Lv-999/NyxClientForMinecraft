package io.github.seraphina.nyx.client.module.visual.hud;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.manager.HUDManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.visual.hud.component.NotificationComponent;
import io.github.seraphina.nyx.client.module.visual.hud.component.WatermarkComponent;
import io.github.seraphina.nyx.client.ui.UIComponent;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.HashSet;
import java.util.Set;

@ModuleInfo(name = "nyxclient.module.hud.name", description = "nyxclient.module.hud.description", category = Category.VISUAL)
public class HUD extends Module {
    public static final Set<UIComponent<?>> components = new HashSet<>();
    public static final HUD INSTANCE = new HUD();

    public final BoolValue watermark = ValueBuild.boolSetting("watermaker", true, this);
    public final BoolValue notification = ValueBuild.boolSetting("notification", true, this);

    public HUD() {
        components.add(new WatermarkComponent());
        components.add(new NotificationComponent());
    }

    @EventTarget
    public void onRender2D(Render2DEvent.HUD event) {
        if (mc.screen instanceof ChatScreen) {
            return;
        }

        HUDManager.render(event.getGuiGraphics(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}
