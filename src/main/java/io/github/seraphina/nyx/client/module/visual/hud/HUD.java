package io.github.seraphina.nyx.client.module.visual.hud;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.manager.HUDManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.visual.hud.component.*;
import io.github.seraphina.nyx.client.module.visual.hud.component.text.BiomeView;
import io.github.seraphina.nyx.client.module.visual.hud.component.text.LevelTypeView;
import io.github.seraphina.nyx.client.module.visual.hud.component.text.PlayerPosView;
import io.github.seraphina.nyx.client.module.visual.hud.component.text.TPSView;
import io.github.seraphina.nyx.client.ui.UIComponent;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.LinkedHashSet;
import java.util.Set;

@ModuleInfo(name = "nyxclient.module.hud.name", description = "nyxclient.module.hud.description", category = Category.VISUAL)
public class HUD extends Module {
    public static final Set<UIComponent<?>> components = new LinkedHashSet<>();
    public static final HUD INSTANCE = new HUD();

    public final BoolValue watermark = ValueBuild.boolSetting("watermaker", true, this);
    public final BoolValue notification = ValueBuild.boolSetting("notification", true, this);
    public final BoolValue tps = ValueBuild.boolSetting("tps", true, this);
    public final BoolValue levelType = ValueBuild.boolSetting("level type", true, this);
    public final BoolValue playerPos = ValueBuild.boolSetting("player pos", true, this);
    public final BoolValue biome = ValueBuild.boolSetting("biome", true, this);
    public final BoolValue inventory = ValueBuild.boolSetting("inventory", false, this);
    public final BoolValue lyric = ValueBuild.boolSetting("lyric", false, this);

    public HUD() {
        components.add(new WatermarkComponent());
        components.add(new TPSView());
        components.add(new LevelTypeView());
        components.add(new PlayerPosView());
        components.add(new BiomeView());
        components.add(new InventoryComponent());
        components.add(new MapComponent());
        components.add(new NotificationComponent());
        components.add(new KeyStrokesComponent());
        components.add(new LyricComponent());
    }

    @EventTarget(4)
    public void onRender2D(Render2DEvent.HUD event) {
        if (mc.screen instanceof ChatScreen) {
            return;
        }

        HUDManager.render(event.getGuiGraphics(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}
