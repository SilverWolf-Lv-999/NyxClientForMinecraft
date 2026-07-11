package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.awt.Color;

@ModuleInfo(name = "nyxclient.module.hurtmaker.name", description = "nyxclient.module.hurtmaker.description", category = Category.VISUAL)
public class HurtMaker extends Module {
    public static final HurtMaker INSTANCE = new HurtMaker();

    private static final float VANILLA_HURT_TICKS = 10.0F;
    private static final int TRANSPARENT = 0x00000000;

    public final ColorValue hurtColor = ValueBuild.colorSetting("hurt color", new Color(255, 42, 50), false, this);
    public final IntValue glowWidth = ValueBuild.intSetting("glow width", 46, 8, 160, 2, this);
    public final IntValue glowAlpha = ValueBuild.intSetting("glow alpha", 170, 0, 255, 5, this);

    @EventTarget
    public void onRender2D(Render2DEvent.HUD event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.hurtTime <= 0 || glowAlpha.getValue() <= 0) {
            return;
        }

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float progress = clamp((player.hurtTime - partialTick + 1.0F) / VANILLA_HURT_TICKS, 0.0F, 1.0F);
        if (progress <= 0.0F) {
            return;
        }

        Render2DUtility.withGuiGraphics(event.getGuiGraphics(), () -> renderHurtGlow(event.getGuiGraphics(), easeOutCubic(progress)));
    }

    private void renderHurtGlow(GuiGraphics graphics, float progress) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        float width = Math.min(glowWidth.getValue(), Math.min(screenWidth, screenHeight) * 0.5F);
        int alpha = Math.round(glowAlpha.getValue() * progress);
        int edgeColor = colorWithAlpha(alpha);
        int lineColor = colorWithAlpha(Math.round(alpha * 0.55F));

        Render2DUtility.drawVerticalGradientRect(0.0F, 0.0F, screenWidth, width, edgeColor, TRANSPARENT);
        Render2DUtility.drawVerticalGradientRect(0.0F, screenHeight - width, screenWidth, width, TRANSPARENT, edgeColor);
        Render2DUtility.drawHorizontalGradientRect(0.0F, 0.0F, width, screenHeight, edgeColor, TRANSPARENT);
        Render2DUtility.drawHorizontalGradientRect(screenWidth - width, 0.0F, width, screenHeight, TRANSPARENT, edgeColor);
        Render2DUtility.drawOutlineRect(0.0F, 0.0F, screenWidth, screenHeight, 1.0F, lineColor);
    }

    private int colorWithAlpha(int alpha) {
        Color color = hurtColor.getValue();
        return Render2DUtility.rgba(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static float easeOutCubic(float progress) {
        float inverse = 1.0F - clamp(progress, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
