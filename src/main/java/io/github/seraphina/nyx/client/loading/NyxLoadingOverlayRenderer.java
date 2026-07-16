package io.github.seraphina.nyx.client.loading;

import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.util.Mth;

public final class NyxLoadingOverlayRenderer {
    private static final String TITLE = "Nyx Client";
    private static final int TITLE_SIZE = 56;
    private static final int PROGRESS_SIZE = 18;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int TITLE_SHADOW = 0x52000000;
    private static final int PROGRESS_COLOR = 0xFFE9EEF6;
    private static final int PROGRESS_SHADOW = 0x66000000;

    private NyxLoadingOverlayRenderer() {
    }

    public static void render(int width, int height, float progress, float opacity, long millis) {
        int titleColor = applyOpacity(TITLE_COLOR, opacity);
        int titleShadow = applyOpacity(TITLE_SHADOW, opacity);
        int progressColor = applyOpacity(PROGRESS_COLOR, opacity);
        int progressShadow = applyOpacity(PROGRESS_SHADOW, opacity);
        if (((titleColor >>> 24) & 0xFF) == 0) {
            return;
        }

        FontRenderer titleFont = FontManager.getAppleDisplayRenderer(TITLE_SIZE);
        FontRenderer progressFont = FontManager.getAppleTextRenderer(PROGRESS_SIZE);
        renderAnimatedTitle(titleFont, width, height, titleColor, titleShadow, millis);
        renderProgress(progressFont, width, height, progress, progressColor, progressShadow);
    }

    private static void renderAnimatedTitle(FontRenderer font, int width, int height, int color, int shadowColor, long millis) {
        float x = (width - font.getStringWidth(TITLE)) * 0.5F;
        float y = (height - font.getLineHeight()) * 0.5F - 8.0F;
        int visibleIndex = 0;
        for (int offset = 0; offset < TITLE.length(); ) {
            int codePoint = TITLE.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            offset += Character.charCount(codePoint);
            float advance = font.getStringWidth(glyph);
            if (codePoint == ' ') {
                x += advance;
                visibleIndex++;
                continue;
            }

            float bounce = bounceOffset(millis, visibleIndex++);
            font.drawString(glyph, x + 2.0F, y + 4.0F + bounce, shadowColor);
            font.drawString(glyph, x, y + bounce, color);
            x += advance;
        }
    }

    private static void renderProgress(FontRenderer font, int width, int height, float progress, int color, int shadowColor) {
        String text = Math.round(Mth.clamp(progress, 0.0F, 1.0F) * 100.0F) + "%";
        float x = width - 22.0F - font.getStringWidth(text);
        float y = height - 18.0F - font.getLineHeight();
        font.drawString(text, x + 1.0F, y + 2.0F, shadowColor);
        font.drawString(text, x, y, color);
    }

    private static float bounceOffset(long millis, int index) {
        float cycle = 1550.0F;
        float phase = (millis - index * 115.0F) % cycle;
        if (phase < 0.0F) {
            phase += cycle;
        }
        if (phase > 550.0F) {
            return 0.0F;
        }
        return -16.0F * (float)Math.sin((phase / 550.0F) * Math.PI);
    }

    private static int applyOpacity(int color, float opacity) {
        int alpha = (color >>> 24) & 0xFF;
        int fadedAlpha = Math.round(alpha * Mth.clamp(opacity, 0.0F, 1.0F));
        return (color & 0x00FFFFFF) | (fadedAlpha << 24);
    }
}
