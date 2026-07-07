package io.github.seraphina.nyx.client.utility;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class PropertiesUtility {
    private PropertiesUtility() {
    }

    public static Properties loadResource(String resourcePath) {
        Properties properties = new Properties();

        try (InputStream inputStream = getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return properties;
            }

            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load properties resource: " + resourcePath, exception);
        }
    }

    private static InputStream getResourceAsStream(String resourcePath) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream inputStream = contextClassLoader.getResourceAsStream(resourcePath);
            if (inputStream != null) {
                return inputStream;
            }
        }

        return PropertiesUtility.class.getClassLoader().getResourceAsStream(resourcePath);
    }
}
