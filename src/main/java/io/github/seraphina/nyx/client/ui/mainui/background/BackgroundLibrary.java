package io.github.seraphina.nyx.client.ui.mainui.background;

import io.github.seraphina.nyx.client.manager.PathManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class BackgroundLibrary {
    public static final String DEFAULT_KEY = "default";

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        "png", "apng", "jpg", "jpeg", "gif", "bmp", "webp", "tga"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
        "mp4", "m4v", "mov", "webm", "mkv", "avi", "wmv", "flv", "mpeg", "mpg"
    );

    private BackgroundLibrary() {
    }

    public static List<BackgroundMedia> load() {
        List<BackgroundMedia> backgrounds = new ArrayList<>();
        backgrounds.add(BackgroundMedia.defaultBackground());

        try {
            Files.createDirectories(PathManager.BACK_GROUND_PATH);
        } catch (IOException ignored) {
            return backgrounds;
        }

        try (Stream<Path> stream = Files.list(PathManager.BACK_GROUND_PATH)) {
            stream.filter(Files::isRegularFile)
                .filter(BackgroundLibrary::isSupported)
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .map(path -> BackgroundMedia.fileBackground(path, isAnimated(path)))
                .forEach(backgrounds::add);
        } catch (IOException ignored) {
        }
        return backgrounds;
    }

    public static boolean isSupported(Path path) {
        String extension = extension(path);
        return IMAGE_EXTENSIONS.contains(extension) || VIDEO_EXTENSIONS.contains(extension);
    }

    public static boolean isAnimated(Path path) {
        String extension = extension(path);
        return VIDEO_EXTENSIONS.contains(extension)
            || "gif".equals(extension)
            || "apng".equals(extension)
            || ("png".equals(extension) && containsPngChunk(path, "acTL"));
    }

    private static boolean containsPngChunk(Path path, String chunkName) {
        byte[] needle = chunkName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] buffer = new byte[1024 * 1024];
        int length;
        try (InputStream input = Files.newInputStream(path)) {
            length = input.read(buffer);
        } catch (IOException ignored) {
            return false;
        }

        for (int i = 0; i <= length - needle.length; i++) {
            boolean matches = true;
            for (int j = 0; j < needle.length; j++) {
                if (buffer[i + j] != needle[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static String extension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
