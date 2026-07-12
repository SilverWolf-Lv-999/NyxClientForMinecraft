package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.ColorValue;

import java.awt.Color;

@ModuleInfo(name = "nyxclient.module.filter.name", description = "nyxclient.module.filter.description", category = Category.VISUAL)
public class Filter extends Module {
    public static final Filter INSTANCE = new Filter();

    public final ColorValue color = ValueBuild.colorSetting("color", new Color(0, 0, 0, 32), this);

    @EventTarget
    public void onRender2D(Render2DEvent.Level event) {
        Color filterColor = color.getValue();
        if (filterColor == null || filterColor.getAlpha() <= 0) {
            return;
        }

        int width = event.getGuiGraphics().guiWidth();
        int height = event.getGuiGraphics().guiHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        Render2DUtility.withGuiGraphics(event.getGuiGraphics(), () ->
            Render2DUtility.drawRect(0.0F, 0.0F, width, height, filterColor.getRGB())
        );
    }
}
