package io.github.seraphina.nyx.client.module.visual.hud.component.text;

import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import io.github.seraphina.nyx.client.ui.UIComponent;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;

public abstract class TextComponent implements UIComponent<HUD> {
    private static final float WIDTH_MIN = 80.0F;
    private static final float WIDTH_MAX = 230.0F;
    private static final float HEIGHT = 24.0F;
    private static final float TEXT_PADDING_LEFT = 16.0F;
    private static final float TEXT_PADDING_RIGHT = 9.0F;
    private static final float RADIUS = 6.0F;
    private static final int BACKGROUND = 0xCC0C0D11;
    private static final int BORDER = 0x22FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SHADOW = 0x80000000;

    @Override
    public void render(GuiGraphics graphics, float partialTicks, float scale) {
        FontRenderer font = font();
        String text = displayValue(font);
        float width = width(font, text);

        Render2DUtility.drawDropShadow(0.0F, 0.0F, width, HEIGHT, RADIUS, 0.0F, 0.0F, 10.0F, SHADOW);
        Render2DUtility.drawRoundedRect(0.0F, 0.0F, width, HEIGHT, RADIUS, BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(0.0F, 0.0F, width, HEIGHT, RADIUS, 1.0F, BORDER);
        Render2DUtility.drawRoundedRect(6.0F, 7.0F, 3.0F, HEIGHT - 14.0F, 1.5F, accentColor());

        float textY = (HEIGHT - font.getLineHeight()) * 0.5F - 0.5F;
        font.drawString(text, TEXT_PADDING_LEFT, textY, TEXT);
    }

    @Override
    public AABB getBoundingBox() {
        FontRenderer font = font();
        String text = displayValue(font);
        return new AABB(0.0D, 0.0D, 0.0D, width(font, text), HEIGHT, 1.0D);
    }

    protected int accentColor() {
        return 0xFF3D81F7;
    }

    protected FontRenderer font() {
        return FontManager.getAppleTextRenderer(12.0F);
    }

    public abstract String getValue();

    private String displayValue(FontRenderer font) {
        String value = getValue();
        if (value == null || value.isBlank()) {
            value = "N/A";
        }
        return trimToWidth(font, value, WIDTH_MAX - TEXT_PADDING_LEFT - TEXT_PADDING_RIGHT);
    }

    private float width(FontRenderer font, String text) {
        return clamp(font.getStringWidth(text) + TEXT_PADDING_LEFT + TEXT_PADDING_RIGHT, WIDTH_MIN, WIDTH_MAX);
    }

    private static String trimToWidth(FontRenderer font, String text, float maxWidth) {
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        float ellipsisWidth = font.getStringWidth(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }

        int end = text.length();
        while (end > 0 && font.getStringWidth(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
