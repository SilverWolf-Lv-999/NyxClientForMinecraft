package io.github.seraphina.nyx.client.utility;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Scanner;
import java.util.regex.Pattern;

public final class StringUtility {
    private static final Path RESOURCE_DIRECTORY = Path.of("src", "main", "resources");
    private static final Pattern IPV4_ADDRESS_PATTERN = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?");
    private static final Pattern DOMAIN_ADDRESS_PATTERN = Pattern.compile(
        "(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:com|net|org|gg|io|me|xyz|club|cn|cc|fun|top|vip|pro|dev|site|online|store|network|world)(?::\\d{1,5})?"
    );

    private StringUtility() {
    }

    public static String readString(String path) {
        return readString(Path.of(path));
    }

    public static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read string file: " + path, exception);
        }
    }

    public static String readResource(Class<?> type, String resourcePath) {
        String normalizedPath = normalizeResourcePath(resourcePath);

        try (InputStream inputStream = getResourceAsStream(type, normalizedPath)) {
            if (inputStream == null) {
                throw new RuntimeException("Failed to locate string resource: " + normalizedPath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read string resource: " + normalizedPath, exception);
        }
    }

    public static String getInput() {
        return new Scanner(System.in).next();
    }

    public static int readMaxStringLength(String... strings) {
        int maxLength = 0;
        for (String string : strings) {
            if (string.length() > maxLength) maxLength = string.length();
        }
        return maxLength;
    }

    public static boolean isIpAddress(String string) {
        if (string == null) {
            return false;
        }

        String address = string.trim();
        if (address.isEmpty() || !hasValidPort(address)) {
            return false;
        }

        if (IPV4_ADDRESS_PATTERN.matcher(address).matches()) {
            String host = address.substring(0, address.indexOf(':') < 0 ? address.length() : address.indexOf(':'));
            String[] parts = host.split("\\.");
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value > 255) {
                    return false;
                }
            }
            return true;
        }

        return DOMAIN_ADDRESS_PATTERN.matcher(address).matches();
    }

    private static boolean hasValidPort(String string) {
        int separator = string.lastIndexOf(':');
        if (separator < 0) {
            return true;
        }

        try {
            int port = Integer.parseInt(string.substring(separator + 1));
            return port <= 65535;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static String readStringInProject(Class<?> type, String relativePath) {
        return readStringInProject(type, Path.of(relativePath));
    }

    public static String readStringInProject(Class<?> type, Path relativePath) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(relativePath, "relativePath");

        String resourcePath = normalizeResourcePath(relativePath.toString());
        String resourceContent = readResourceOrNull(type, resourcePath);
        if (resourceContent != null) {
            return resourceContent;
        }

        Path path = findProjectFile(type, relativePath);
        if (path == null) {
            throw new RuntimeException("Failed to locate string file or resource: " + relativePath);
        }

        return readString(path);
    }

    private static String readResourceOrNull(Class<?> type, String resourcePath) {
        try (InputStream inputStream = getResourceAsStream(type, resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read string resource: " + resourcePath, exception);
        }
    }

    private static InputStream getResourceAsStream(Class<?> type, String resourcePath) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream inputStream = contextClassLoader.getResourceAsStream(resourcePath);
            if (inputStream != null) {
                return inputStream;
            }
        }

        ClassLoader classLoader = type.getClassLoader();
        if (classLoader != null) {
            InputStream inputStream = classLoader.getResourceAsStream(resourcePath);
            if (inputStream != null) {
                return inputStream;
            }
        }

        return type.getResourceAsStream("/" + resourcePath);
    }

    private static Path findProjectFile(Class<?> type, Path relativePath) {
        Path cwd = Path.of("").toAbsolutePath();
        Path codeSourceDirectory = getCodeSourceDirectory(type);

        Path path = findUpwards(cwd, relativePath);
        if (path != null) {
            return path;
        }

        path = findUpwards(cwd, RESOURCE_DIRECTORY.resolve(relativePath));
        if (path != null) {
            return path;
        }

        path = findUpwards(codeSourceDirectory, relativePath);
        if (path != null) {
            return path;
        }

        return findUpwards(codeSourceDirectory, RESOURCE_DIRECTORY.resolve(relativePath));
    }

    private static Path findUpwards(Path start, Path relativePath) {
        if (start == null) {
            return null;
        }

        Path current = start.normalize();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String normalizeResourcePath(String resourcePath) {
        String normalizedPath = resourcePath.replace('\\', '/');
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        return normalizedPath;
    }

    private static Path getCodeSourceDirectory(Class<?> type) {
        try {
            Path path = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(path)) {
                return path.getParent();
            }
            return path;
        } catch (URISyntaxException exception) {
            throw new RuntimeException("Failed to locate code source for: " + type.getName(), exception);
        }
    }
}
