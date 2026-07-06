package io.github.seraphina.nyxclient.utility.font;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SystemFonts {
    private static final Font FALLBACK_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 18);

    private static List<Font> fonts = List.of();
    private static List<String> familyNames = List.of();

    private SystemFonts() {
    }

    public static synchronized void load() {
        Font[] allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        Map<String, Font> uniqueFonts = new LinkedHashMap<>();

        Arrays.stream(allFonts)
            .sorted(Comparator.comparing(Font::getFontName, String.CASE_INSENSITIVE_ORDER))
            .forEach(font -> uniqueFonts.putIfAbsent(font.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT), font));

        fonts = Collections.unmodifiableList(new ArrayList<>(uniqueFonts.values()));
        familyNames = Collections.unmodifiableList(Arrays.stream(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT)
            )
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .distinct()
            .toList());
    }

    public static List<Font> getFonts() {
        ensureLoaded();
        return fonts;
    }

    public static List<String> getFamilyNames() {
        ensureLoaded();
        return familyNames;
    }

    public static Optional<Font> findFont(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        ensureLoaded();
        String normalizedName = name.trim();
        return fonts.stream()
            .filter(font -> equalsFontName(font, normalizedName))
            .findFirst()
            .or(() -> familyNames.stream()
                .filter(family -> family.equalsIgnoreCase(normalizedName))
                .findFirst()
                .map(family -> new Font(family, Font.PLAIN, FALLBACK_FONT.getSize())));
    }

    public static Font getDefaultFont() {
        ensureLoaded();

        return findFont("Microsoft YaHei UI")
            .or(() -> findFont("Microsoft YaHei"))
            .or(() -> findFont("Segoe UI"))
            .or(() -> findFont("Arial"))
            .orElse(FALLBACK_FONT);
    }

    public static Font getFallbackFont() {
        return FALLBACK_FONT;
    }

    private static boolean equalsFontName(Font font, String name) {
        return font.getFontName(Locale.ROOT).equalsIgnoreCase(name)
            || font.getFamily(Locale.ROOT).equalsIgnoreCase(name)
            || font.getName().equalsIgnoreCase(name);
    }

    private static void ensureLoaded() {
        if (fonts.isEmpty()) {
            load();
        }
    }
}
