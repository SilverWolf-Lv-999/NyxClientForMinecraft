package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.resources.Identifier;

import java.awt.Color;

@ModuleInfo(name = "nyxclient.module.watermaker.name", description = "nyxclient.module.watermaker.description", category = Category.VISUAL)
public class Watermaker extends Module {
    public static final Watermaker INSTANCE = new Watermaker();

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("nyxclient", "ui/icon/logo.png");
    private static final float LOGO_SIZE = 48.0F;
    private static final float GLOW_RADIUS = 8.0F;
    private static final float GRADIENT_CYCLE_SECONDS = 4.0F;
    private static final float RAINBOW_CYCLE_SECONDS = 6.0F;

    public final DoubleValue scale = ValueBuild.doubleSetting("scale", 1.0D, 0.1D, 1.0D, 0.1D, this);
    public final EnumValue<ModuleColor> style = ValueBuild.enumSetting("style", ModuleColor.GRADIENT, this);
    public final ColorValue colorOfSolid = ValueBuild.colorSetting(
        "solid color",
        Color.WHITE,
        () -> style.getValue() == ModuleColor.SOLID,
        this
    );
    public final ColorValue colorOfGradient1 = ValueBuild.colorSetting(
        "color 1",
        Color.CYAN,
        () -> style.getValue() == ModuleColor.GRADIENT,
        this
    );
    public final ColorValue colorOfGradient2 = ValueBuild.colorSetting(
        "color 2",
        Color.GREEN,
        () -> style.getValue() == ModuleColor.GRADIENT,
        this
    );

    @EventTarget
    public void onRender2D(Render2DEvent.HUD event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        float logoScale = scale.getValue().floatValue();
        float size = LOGO_SIZE * logoScale;
        int color = color();
        Render2DUtility.withGuiGraphics(event.getGuiGraphics(), () -> {
            var texture = mc.getTextureManager().getTexture(LOGO).getTextureView();
            Render2DUtility.drawTextureGlow(texture, 0.0F, 0.0F, size, size, GLOW_RADIUS * logoScale, color);
            Render2DUtility.drawTexture(
                texture,
                0.0F,
                0.0F,
                size,
                size,
                color
            );
        });
    }

    private int color() {
        return switch (style.getValue()) {
            case SOLID -> colorOfSolid.getValue().getRGB();
            case GRADIENT -> gradientColor();
            case RAINBOW -> rainbowColor();
        };
    }

    private int gradientColor() {
        float cycle = cycleProgress(GRADIENT_CYCLE_SECONDS);
        return Render2DUtility.mix(colorOfGradient1.getValue().getRGB(), colorOfGradient2.getValue().getRGB(), cycle);
    }

    private int rainbowColor() {
        float hue = cycleProgress(RAINBOW_CYCLE_SECONDS);
        return Color.HSBtoRGB(hue, 0.85F, 1.0F) | 0xFF000000;
    }

    private static float cycleProgress(float durationSeconds) {
        long durationNanos = (long)(durationSeconds * 1_000_000_000.0F);
        double phase = System.nanoTime() % durationNanos / (double)durationNanos;
        return (1.0F - (float)Math.cos(phase * Math.PI * 2.0D)) * 0.5F;
    }

    public enum ModuleColor {
        RAINBOW,
        GRADIENT,
        SOLID
    }
}
