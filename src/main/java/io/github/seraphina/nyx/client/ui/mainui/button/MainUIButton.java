package io.github.seraphina.nyx.client.ui.mainui.button;


import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public final class MainUIButton extends AbstractButton {
    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;
    private static final float BUTTON_RADIUS = 8.0F;
    private static final int CONTROL_BACKGROUND = 0xAA0E1118;
    private static final int CONTROL_HOVER = 0xD7191D28;
    private static final int BORDER = 0x22FFFFFF;
    private static final int BORDER_HOVER = 0x663D81F7;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFE2E6EF;
    private static final int TEXT_DISABLED = 0xFF687181;

    private final Runnable onPress;
    private long lastRenderNanos;
    private float frameSeconds = DEFAULT_FRAME_SECONDS;
    private float hoverProgress;

    public MainUIButton(int x, int y, int width, int height, Component message) {
        this(x, y, width, height, message, () -> {
        });
    }

    public MainUIButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = Objects.requireNonNull(onPress, "onPress");
    }

    @Override
    public void onPress(InputWithModifiers inputWithModifiers) {
        this.onPress.run();
    }

    @Override
    protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateAnimationFrame();

        boolean highlighted = this.active && this.isHoveredOrFocused();
        this.hoverProgress = animate(this.hoverProgress, highlighted ? 1.0F : 0.0F, 16.0F);

        float x = getX();
        float y = getY();
        float width = getWidth();
        float height = getHeight();
        float opacity = this.alpha * (this.active ? 1.0F : 0.48F);

        int fill = Render2DUtility.mix(CONTROL_BACKGROUND, CONTROL_HOVER, this.hoverProgress);
        int border = Render2DUtility.mix(BORDER, BORDER_HOVER, this.hoverProgress);
        Render2DUtility.drawDropShadow(
            x,
            y,
            width,
            height,
            BUTTON_RADIUS,
            0.0F,
            5.0F,
            12.0F,
            Render2DUtility.applyOpacity(0x55000000, opacity * (0.55F + this.hoverProgress * 0.45F))
        );
        Render2DUtility.drawRoundedRect(x, y, width, height, BUTTON_RADIUS, Render2DUtility.applyOpacity(fill, opacity));
        Render2DUtility.drawOutlineRoundedRect(x, y, width, height, BUTTON_RADIUS, 1.0F, Render2DUtility.applyOpacity(border, opacity));

        FontRenderer font = FontManager.getAppleTextRenderer(12.0F);
        String text = trimToWidth(font, getMessage().getString(), width - 18.0F);
        int textColor = this.active ? Render2DUtility.mix(TEXT_MUTED, TEXT, this.hoverProgress) : TEXT_DISABLED;
        font.drawCenteredString(
            text,
            x + width * 0.5F,
            y + (height - font.getLineHeight()) * 0.5F,
            Render2DUtility.applyOpacity(textColor, opacity)
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    private void updateAnimationFrame() {
        long now = System.nanoTime();
        if (this.lastRenderNanos == 0L) {
            this.frameSeconds = DEFAULT_FRAME_SECONDS;
        } else {
            this.frameSeconds = clamp((now - this.lastRenderNanos) / 1_000_000_000.0F, 0.0F, MAX_FRAME_SECONDS);
        }
        this.lastRenderNanos = now;
    }

    private float animate(float current, float target, float speed) {
        float progress = 1.0F - (float)Math.exp(-Math.max(0.0F, speed) * this.frameSeconds);
        float result = current + (target - current) * progress;
        return Math.abs(result - target) < 0.001F ? target : result;
    }

    private static String trimToWidth(FontRenderer renderer, String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) {
            return "";
        }
        if (renderer.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        float suffixWidth = renderer.getStringWidth(suffix);
        if (suffixWidth > maxWidth) {
            return "";
        }

        int end = text.length();
        while (end > 0 && renderer.getStringWidth(text.substring(0, end)) + suffixWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + suffix;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
