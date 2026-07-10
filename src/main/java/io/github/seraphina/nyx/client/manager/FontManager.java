package io.github.seraphina.nyx.client.manager;

import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.utility.font.SystemFonts;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FontManager {
    public static final float DEFAULT_FONT_SIZE = 18.0F;
    private static final String CLICK_GUI_FONT_RESOURCE = "/assets/nyxclient/fonts/MapleMono-CN-Medium.ttf";
    private static final String DEBUG_CHAT_FONT_RESOURCE = "/assets/nyxclient/fonts/MapleMono-CN-Medium.ttf";

    private static final String[] APPLE_DISPLAY_FONTS = {
        "SF Pro Display",
        "SF Pro Text",
        "San Francisco Display",
        "PingFang SC",
        "Microsoft YaHei UI",
        "Microsoft YaHei",
        "Inter",
        "Segoe UI Variable Display",
        "Segoe UI",
        "Arial"
    };
    private static final String[] APPLE_TEXT_FONTS = {
        "SF Pro Text",
        "SF Pro Display",
        "San Francisco Text",
        "PingFang SC",
        "Microsoft YaHei UI",
        "Microsoft YaHei",
        "Inter",
        "Segoe UI Variable Text",
        "Segoe UI",
        "Arial"
    };

    private static final Map<String, FontRenderer> RENDERERS = new HashMap<>();
    private static FontRenderer defaultRenderer;
    private static Font appleDisplayFont;
    private static Font appleTextFont;
    private static Font clickGuiFont;
    private static Font debugChatFont;
    private static Font debugChatBoldFont;

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

    public static synchronized FontRenderer getAppleDisplayRenderer(float size) {
        if (appleDisplayFont == null) {
            appleDisplayFont = preferredFont(APPLE_DISPLAY_FONTS);
        }
        return getRenderer(appleDisplayFont, size);
    }

    public static synchronized FontRenderer getAppleTextRenderer(float size) {
        if (appleTextFont == null) {
            appleTextFont = preferredFont(APPLE_TEXT_FONTS);
        }
        return getRenderer(appleTextFont, size);
    }

    public static synchronized void initClickGuiFonts() {
        if (clickGuiFont == null) {
            clickGuiFont = loadResourceFont(CLICK_GUI_FONT_RESOURCE)
                .orElseGet(() -> preferredFont(APPLE_TEXT_FONTS));
        }
        getRenderer(clickGuiFont, DEFAULT_FONT_SIZE);
    }

    public static synchronized FontRenderer getClickGuiRenderer(float size) {
        initClickGuiFonts();
        return getRenderer(clickGuiFont, size);
    }

    public static synchronized void initDebugChatFonts() {
        if (debugChatFont == null) {
            debugChatFont = loadResourceFont(DEBUG_CHAT_FONT_RESOURCE)
                .orElseGet(() -> preferredFont(APPLE_TEXT_FONTS));
            debugChatBoldFont = debugChatFont.deriveFont(Font.BOLD);
        }
        getRenderer(debugChatFont, DEFAULT_FONT_SIZE);
        getRenderer(debugChatBoldFont, DEFAULT_FONT_SIZE);
    }

    public static synchronized FontRenderer getDebugChatRenderer(float size) {
        initDebugChatFonts();
        return getRenderer(debugChatFont, size);
    }

    public static synchronized FontRenderer getDebugChatBoldRenderer(float size) {
        initDebugChatFonts();
        return getRenderer(debugChatBoldFont, size);
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
        appleDisplayFont = null;
        appleTextFont = null;
        clickGuiFont = null;
        debugChatFont = null;
        debugChatBoldFont = null;
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

    private static Font preferredFont(String[] names) {
        for (String name : names) {
            var font = SystemFonts.findFont(name);
            if (font.isPresent()) {
                return font.get();
            }
        }
        return SystemFonts.getDefaultFont();
    }

    private static Optional<Font> loadResourceFont(String path) {
        try (InputStream stream = FontManager.class.getResourceAsStream(path)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(Font.createFont(Font.TRUETYPE_FONT, stream));
        } catch (FontFormatException | IOException ignored) {
            return Optional.empty();
        }
    }

    private static String key(Font font, float size) {
        return font.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT) + ":" + font.getStyle() + ":" + Math.round(size * 100.0F);
    }
}
