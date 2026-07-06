package io.github.seraphina.nyxclient.utility;

import io.github.seraphina.nyxclient.module.client.Client;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class LanguageUtility {
    private static final String DEFAULT_LANGUAGE = "en_us";
    private static final String LANGUAGE_DIRECTORY = "assets/nyxclient/language/";
    private static final Map<String, Properties> LANGUAGE_CACHE = new HashMap<>();

    private static String loadedLanguage = "";
    private static Properties defaultProperties = new Properties();
    private static Properties languageProperties = new Properties();

    private LanguageUtility() {
    }

    public static String translate(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        refreshIfNeeded();

        String value = languageProperties.getProperty(key);
        if (value == null) {
            value = defaultProperties.getProperty(key);
        }

        return value == null ? key : value;
    }

    public static String translate(String key, Object... args) {
        return String.format(Locale.ROOT, translate(key), args);
    }

    public static synchronized void reload() {
        LANGUAGE_CACHE.clear();
        loadLanguage(getSelectedLanguage());
    }

    private static synchronized void refreshIfNeeded() {
        String selectedLanguage = getSelectedLanguage();
        if (!selectedLanguage.equals(loadedLanguage)) {
            loadLanguage(selectedLanguage);
        }
    }

    private static void loadLanguage(String language) {
        defaultProperties = loadProperties(DEFAULT_LANGUAGE);
        languageProperties = DEFAULT_LANGUAGE.equals(language) ? defaultProperties : loadProperties(language);
        loadedLanguage = language;
    }

    private static Properties loadProperties(String language) {
        return LANGUAGE_CACHE.computeIfAbsent(language, value -> PropertiesUtility.loadResource(LANGUAGE_DIRECTORY + value + ".properties"));
    }

    private static String getSelectedLanguage() {
        Client.Language language = Client.INSTANCE.LANGUAGE.getValue();
        if (language == null) {
            return DEFAULT_LANGUAGE;
        }

        return switch (language) {
            case EN_US -> "en_us";
            case ZH_CN -> "zh_cn";
            case MINECRAFT_LANGUAGE -> getMinecraftLanguage();
        };
    }

    private static String getMinecraftLanguage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || minecraft.options.languageCode == null || minecraft.options.languageCode.isBlank()) {
            return DEFAULT_LANGUAGE;
        }

        return minecraft.options.languageCode.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
