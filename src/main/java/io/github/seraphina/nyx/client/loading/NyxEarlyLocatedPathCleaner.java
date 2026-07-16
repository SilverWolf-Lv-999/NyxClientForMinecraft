package io.github.seraphina.nyx.client.loading;

import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

final class NyxEarlyLocatedPathCleaner {
    private static final Logger LOGGER = LoggerFactory.getLogger(NyxEarlyLocatedPathCleaner.class);
    private static final String MOD_ID = "nyxclient";

    private NyxEarlyLocatedPathCleaner() {
    }

    static void removeNyxModPathsFromFmlLocated(String reason) {
        try {
            FMLLoader loader = FMLLoader.getCurrentOrNull();
            if (loader == null) {
                return;
            }

            Set<Path> pathsToRemove = discoverNyxModPaths();
            if (pathsToRemove.isEmpty()) {
                return;
            }

            VarHandle locatedPathsHandle = MethodHandles.privateLookupIn(FMLLoader.class, MethodHandles.lookup())
                .findVarHandle(FMLLoader.class, "locatedPaths", Set.class);
            @SuppressWarnings("unchecked")
            Set<Path> locatedPaths = (Set<Path>)locatedPathsHandle.get(loader);
            int before = locatedPaths.size();
            locatedPaths.removeIf(locatedPath -> pathsToRemove.stream().anyMatch(path -> samePath(locatedPath, path)));
            int removed = before - locatedPaths.size();
            if (removed > 0) {
                LOGGER.info("Removed {} Nyx path(s) from FML located paths after early service loading ({})", removed, reason);
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to clean Nyx paths from FML located paths", throwable);
        }
    }

    private static Set<Path> discoverNyxModPaths() {
        Set<Path> paths = new LinkedHashSet<>();
        addCodeSourcePath(paths);
        addModClassPaths(paths);
        return paths;
    }

    private static void addCodeSourcePath(Set<Path> paths) {
        var codeSource = NyxEarlyLocatedPathCleaner.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return;
        }

        try {
            paths.add(normalize(Path.of(codeSource.getLocation().toURI())));
        } catch (IllegalArgumentException | URISyntaxException ignored) {
        }
    }

    private static void addModClassPaths(Set<Path> paths) {
        String modFolders = Optional.ofNullable(System.getenv("MOD_CLASSES"))
            .orElse(System.getProperty("fml.modFolders", ""));
        if (modFolders.isBlank()) {
            return;
        }

        for (String entry : modFolders.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }

            int splitIndex = entry.indexOf("%%");
            String modId = splitIndex == -1 ? "defaultmodid" : entry.substring(0, splitIndex);
            if (!MOD_ID.equals(modId)) {
                continue;
            }

            String path = splitIndex == -1 ? entry : entry.substring(splitIndex + "%%".length());
            if (!path.isBlank()) {
                paths.add(normalize(Path.of(path)));
            }
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static boolean samePath(Path left, Path right) {
        Path normalizedLeft = normalize(left);
        Path normalizedRight = normalize(right);
        if (normalizedLeft.equals(normalizedRight)) {
            return true;
        }

        try {
            if (Files.exists(normalizedLeft) && Files.exists(normalizedRight)) {
                return Files.isSameFile(normalizedLeft, normalizedRight);
            }
        } catch (Exception ignored) {
        }

        return normalizedLeft.toString().equalsIgnoreCase(normalizedRight.toString());
    }
}
