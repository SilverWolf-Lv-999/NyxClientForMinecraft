package io.github.seraphina.nyxclient.manager;

import io.github.seraphina.nyxclient.utility.font.FontRenderer;
import io.github.seraphina.nyxclient.utility.font.SystemFonts;

import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class FontManager {
    public static final float DEFAULT_FONT_SIZE = 18.0F;

    private static final Map<String, FontRenderer> RENDERERS = new HashMap<>();
    private static FontRenderer defaultRenderer;

    private FontManager() {
    }

    public static synchronized void init() {
        SystemFonts.load();
        defaultRenderer = getRenderer(SystemFonts.getDefaultFont(), DEFAULT_FONT_SIZE);
    }

    public static synchronized FontRenderer getDefaultRenderer() {
        if (defaultRenderer == null) {
            init();
        }
        return defaultRenderer;
    }

    public static synchronized FontRenderer getRenderer(String fontName, float size) {
        Font font = SystemFonts.findFont(fontName).orElseGet(SystemFonts::getDefaultFont);
        return getRenderer(font, size);
    }

    public static synchronized FontRenderer getRenderer(Font font, float size) {
        Objects.requireNonNull(font, "font");
        float safeSize = safeSize(size);
        String key = key(font, safeSize);
        return RENDERERS.computeIfAbsent(key, ignored -> createRenderer(font, safeSize));
    }

    public static List<Font> getSystemFonts() {
        return SystemFonts.getFonts();
    }

    public static List<String> getSystemFontFamilies() {
        return SystemFonts.getFamilyNames();
    }

    public static synchronized void close() {
        for (FontRenderer renderer : RENDERERS.values()) {
            renderer.close();
        }
        RENDERERS.clear();
        defaultRenderer = null;
        FontRenderer.closeSharedResources();
    }

    private static FontRenderer createRenderer(Font font, float size) {
        float safeSize = safeSize(size);
        Font derivedFont = font.deriveFont(safeSize);
        return new FontRenderer(derivedFont);
    }

    private static float safeSize(float size) {
        return size > 0.0F ? size : DEFAULT_FONT_SIZE;
    }

    private static String key(Font font, float size) {
        return font.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT) + ":" + font.getStyle() + ":" + Math.round(size * 100.0F);
    }
}
