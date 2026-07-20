package io.github.seraphina.nyx.client.ui.clickgui;

import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.value.AbstractValue;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import static io.github.seraphina.nyx.client.utility.MathUtility.animateExp;
import static io.github.seraphina.nyx.client.utility.MathUtility.isInsideExclusive;

public abstract class AbstractComponent {
    public static final float ROW_HEIGHT = 30.0F;
    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;

    protected static final int CONTROL_BACKGROUND = 0xFF0C0D11;
    protected static final int CONTROL_HOVER = 0xFF181B24;
    protected static final int SLIDER_BACKGROUND = 0xFF20222B;
    protected static final int TEXT = 0xFFFFFFFF;
    protected static final int TEXT_MUTED = 0xFFA0A5B5;
    protected static final int TEXT_DIM = 0xFF4B5263;
    protected static final int TEXT_SUBTLE = 0xFF6C717E;
    protected static final int BORDER = 0x1AFFFFFF;
    protected static final int BORDER_SOFT = 0x0AFFFFFF;
    protected static final int HOVER = 0x0AFFFFFF;
    protected static final int TOGGLE_OFF = 0xFF20222B;

    protected final AbstractValue<?> value;
    protected float x;
    protected float y;
    protected float width;
    protected int accentColor = 0xB33D81F7;
    private boolean compactLayout;
    private long lastRenderNanos;
    private float frameSeconds = DEFAULT_FRAME_SECONDS;

    protected AbstractComponent(AbstractValue<?> value) {
        this.value = value;
    }

    public final AbstractValue<?> value() {
        return value;
    }

    public final void render(float x, float y, float width, int mouseX, int mouseY, float partialTick) {
        setBounds(x, y, width);
        updateAnimationFrame();
        render(mouseX, mouseY, partialTick);
    }

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
    }

    public void setCompactLayout(boolean compactLayout) {
        this.compactLayout = compactLayout;
    }

    protected abstract void render(int mouseX, int mouseY, float partialTick);

    public float getHeight() {
        return rowHeight();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }

    public void tick() {
    }

    public void blur() {
    }

    protected void drawLabel(float reservedRightWidth) {
        FontRenderer labelFont = font(compactLayout ? 7.0F : 10.0F);
        float maxWidth = Math.max(20.0F, width - reservedRightWidth);
        labelFont.drawString(trimToWidth(labelFont, value.getDisplayName(), maxWidth), x,
            y + centeredTextY(rowHeight(), labelFont), TEXT_MUTED);
    }

    protected void renderPill(float pillX, float pillY, float pillWidth, float pillHeight, String text, boolean accentText) {
        FontRenderer pillFont = font(9.0F);
        Render2DUtility.drawRoundedRect(pillX, pillY, pillWidth, pillHeight, 4.0F, CONTROL_BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(pillX, pillY, pillWidth, pillHeight, 4.0F, 1.0F, BORDER_SOFT);
        pillFont.drawCenteredString(
            trimToWidth(pillFont, text, pillWidth - 10.0F),
            pillX + pillWidth * 0.5F,
            pillY + centeredTextY(pillHeight, pillFont),
            accentText ? accentColor : TEXT_SUBTLE
        );
    }

    protected FontRenderer font(float size) {
        return compactLayout
            ? FontManager.getArrayListRegularRenderer(size)
            : FontManager.getClickGuiRenderer(size);
    }

    protected FontRenderer boldFont(float size) {
        return compactLayout
            ? FontManager.getArrayListBoldRenderer(size)
            : FontManager.getClickGuiRenderer(size);
    }

    protected float animate(float current, float target, float speed) {
        if (Float.isNaN(current)) {
            return target;
        }

        return animateExp(current, target, speed, frameSeconds);
    }

    protected float frameSeconds() {
        return frameSeconds;
    }

    protected final boolean compactLayout() {
        return compactLayout;
    }

    protected final float rowHeight() {
        return compactLayout ? 18.0F : ROW_HEIGHT;
    }

    protected static int colorToArgb(java.awt.Color color) {
        return Render2DUtility.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    protected static String trimToWidth(FontRenderer renderer, String text, float maxWidth) {
        if (text == null || text.isEmpty()) {
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
        return text.substring(0, end) + suffix;
    }

    protected static float centeredTextY(float height, FontRenderer renderer) {
        return (height - renderer.getLineHeight()) * 0.5F;
    }

    protected static float lerp(float from, float to, float progress) {
        return io.github.seraphina.nyx.client.utility.MathUtility.lerp(from, to, progress);
    }

    protected static boolean isInside(double mouseX, double mouseY, float x, float y, float width, float height) {
        return isInsideExclusive(mouseX, mouseY, x, y, width, height);
    }

    protected static float clamp(float value, float min, float max) {
        return io.github.seraphina.nyx.client.utility.MathUtility.clamp(value, min, max);
    }

    private void updateAnimationFrame() {
        long now = System.nanoTime();
        if (lastRenderNanos == 0L) {
            frameSeconds = DEFAULT_FRAME_SECONDS;
        } else {
            frameSeconds = clamp((now - lastRenderNanos) / 1_000_000_000.0F, 0.0F, MAX_FRAME_SECONDS);
        }
        lastRenderNanos = now;
    }
}
