package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.music.MusicPlaybackService;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.Arrays;

@ModuleInfo(name = "nyxclient.module.spectrum.name", description = "nyxclient.module.spectrum.description", category = Category.VISUAL)
public class Spectrum extends Module {
    public static final Spectrum INSTANCE = new Spectrum();

    private static final int MAX_BARS = 128;
    private static final float MAX_FRAME_SECONDS = 0.05F;
    private static final float ATTACK_SPEED = 18.0F;
    private static final float RELEASE_SPEED = 7.5F;
    private static final int BACKGROUND_TOP = 0x00000000;
    private static final int BACKGROUND_BOTTOM = 0x52000000;

    public final IntValue bars = ValueBuild.intSetting("bars", 64, 24, MAX_BARS, 2, this);
    public final IntValue height = ValueBuild.intSetting("height", 76, 20, 180, 2, this);
    public final IntValue bottomOffset = ValueBuild.intSetting("bottom offset", 2, 0, 80, 1, this);
    public final IntValue opacity = ValueBuild.intSetting("opacity", 190, 0, 255, 5, this);
    public final DoubleValue sensitivity = ValueBuild.doubleSetting("sensitivity", 1.0D, 0.25D, 2.5D, 0.05D, this);
    public final BoolValue background = ValueBuild.boolSetting("background", true, this);
    public final ColorValue startColor = ValueBuild.colorSetting("start color", new Color(87, 199, 255), false, this);
    public final ColorValue endColor = ValueBuild.colorSetting("end color", new Color(255, 79, 216), false, this);

    private final float[] animatedBands = new float[MAX_BARS];
    private long lastFrameNanos;

    @Override
    public void onEnable() {
        Arrays.fill(animatedBands, 0.0F);
        lastFrameNanos = 0L;
    }

    @Override
    public void onDisable() {
        Arrays.fill(animatedBands, 0.0F);
        lastFrameNanos = 0L;
    }

    @EventTarget
    public void onRender2D(Render2DEvent.HUD event) {
        if (mc.player == null || mc.level == null || opacity.getValue() <= 0) {
            return;
        }

        int barCount = bars.getValue();
        float frameSeconds = frameSeconds();
        float[] targets = MusicPlaybackService.INSTANCE.spectrumSnapshot(barCount);
        float maxValue = updateAnimation(targets, barCount, frameSeconds);
        if (maxValue <= 0.002F) {
            return;
        }

        Render2DUtility.withGuiGraphics(event.getGuiGraphics(), () -> render(event.getGuiGraphics(), barCount, maxValue));
    }

    private float updateAnimation(float[] targets, int barCount, float frameSeconds) {
        float maxValue = 0.0F;
        float sensitivityValue = sensitivity.getValue().floatValue();
        for (int i = 0; i < barCount; i++) {
            float source = MathUtility.clamp(targets[i], 0.0F, 1.0F);
            float target = 1.0F - (float)Math.pow(1.0F - source, sensitivityValue);
            target = (float)Math.pow(target, 0.72F);
            float speed = target > animatedBands[i] ? ATTACK_SPEED : RELEASE_SPEED;
            animatedBands[i] = MathUtility.animateExp(animatedBands[i], target, speed, frameSeconds);
            maxValue = Math.max(maxValue, animatedBands[i]);
        }

        for (int i = barCount; i < animatedBands.length; i++) {
            animatedBands[i] = MathUtility.animateExp(animatedBands[i], 0.0F, RELEASE_SPEED, frameSeconds);
        }
        return maxValue;
    }

    private void render(GuiGraphics graphics, int barCount, float maxValue) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        float bottom = screenHeight - bottomOffset.getValue();
        float maxHeight = Math.min(height.getValue(), screenHeight * 0.35F);
        float sidePadding = Math.max(8.0F, screenWidth * 0.035F);
        float gap = barCount > 96 ? 1.0F : 1.5F;
        float width = Math.max(1.0F, screenWidth - sidePadding * 2.0F);
        float barWidth = Math.max(1.2F, (width - gap * (barCount - 1)) / barCount);
        float spectrumWidth = barWidth * barCount + gap * (barCount - 1);
        float startX = (screenWidth - spectrumWidth) * 0.5F;
        float alphaProgress = MathUtility.clamp(maxValue * 1.8F, 0.0F, 1.0F);

        if (background.getValue()) {
            int backgroundBottom = Render2DUtility.applyOpacity(BACKGROUND_BOTTOM, alphaProgress);
            Render2DUtility.drawVerticalGradientRect(
                0.0F,
                Math.max(0.0F, bottom - maxHeight - 14.0F),
                screenWidth,
                maxHeight + bottomOffset.getValue() + 14.0F,
                BACKGROUND_TOP,
                backgroundBottom
            );
        }

        for (int i = 0; i < barCount; i++) {
            float value = animatedBands[i];
            if (value <= 0.001F) {
                continue;
            }

            float x = startX + i * (barWidth + gap);
            float barHeight = Math.max(1.0F, value * maxHeight);
            float y = bottom - barHeight;
            float radius = Math.min(barWidth * 0.5F, 2.5F);
            int color = colorAt(barCount == 1 ? 0.0F : i / (float)(barCount - 1));
            int topColor = withOpacity(lighten(color, 0.22F), value);
            int bottomColor = withOpacity(darken(color, 0.38F), value * 0.78F);

            Render2DUtility.drawRoundedVerticalGradientRect(x, y, barWidth, barHeight, radius, topColor, bottomColor);
        }
    }

    private int colorAt(float progress) {
        return Render2DUtility.mix(startColor.getValue().getRGB(), endColor.getValue().getRGB(), progress);
    }

    private int withOpacity(int color, float value) {
        int alpha = Math.round(opacity.getValue() * MathUtility.clamp(value, 0.0F, 1.0F));
        return Render2DUtility.withAlpha(color, alpha);
    }

    private static int lighten(int color, float amount) {
        return Render2DUtility.mix(color, 0xFFFFFFFF, amount);
    }

    private static int darken(int color, float amount) {
        return Render2DUtility.mix(color, 0xFF000000, amount);
    }

    private float frameSeconds() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 1.0F / 60.0F;
        }

        float seconds = (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        return MathUtility.clamp(seconds, 0.0F, MAX_FRAME_SECONDS);
    }
}
