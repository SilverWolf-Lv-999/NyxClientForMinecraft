package io.github.seraphina.nyx.client.music;

import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.manager.PathManager;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class NeteaseMusicLocalService {
    private static final Object LOCK = new Object();
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 3000;
    private static final String RESOURCE_ROOT = "/assets/nyxclient/music-service/";
    private static final String API_ARCHIVE = RESOURCE_ROOT + "netease-cloud-music-api.zip";
    private static final String SERVICE_VERSION = "netease-4.32.0-node-22.13.1-v1";
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration READY_INTERVAL = Duration.ofMillis(500);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(HEALTH_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static Process process;

    private NeteaseMusicLocalService() {
    }

    public static String baseUrl() {
        return "http://" + host() + ":" + port();
    }

    public static void start() {
        synchronized (LOCK) {
            if (process != null && process.isAlive()) {
                return;
            }

            if (isServiceAvailable()) {
                NyxClient.LOGGER.info("Using existing local NeteaseCloudMusicApi service at {}", baseUrl());
                return;
            }

            try {
                ProcessBuilder builder = processBuilder();
                process = builder.start();
                drain(process.getInputStream(), "stdout");
                drain(process.getErrorStream(), "stderr");
                watch(process);
                waitUntilReady(process);
                NyxClient.LOGGER.info("Starting bundled NeteaseCloudMusicApi service at {}", baseUrl());
            } catch (IOException exception) {
                process = null;
                NyxClient.LOGGER.warn("Failed to start bundled NeteaseCloudMusicApi service", exception);
            }
        }
    }

    public static void stop() {
        Process current;
        synchronized (LOCK) {
            current = process;
            process = null;
        }

        if (current == null || !current.isAlive()) {
            return;
        }

        current.destroy();
        try {
            if (!current.waitFor(5L, TimeUnit.SECONDS)) {
                current.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            current.destroyForcibly();
        }
    }

    public static boolean isServiceAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/"))
                .timeout(HEALTH_TIMEOUT)
                .GET()
                .header("User-Agent", "NyxClient/1.0")
                .build();
            int statusCode = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return statusCode >= 200 && statusCode < 500;
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static ProcessBuilder processBuilder() throws IOException {
        Installation installation = prepareBundledInstallation();
        ProcessBuilder builder = new ProcessBuilder(installation.nodeExecutable().toString(), installation.appScript().toString());
        builder.directory(installation.workingDirectory().toFile());

        Map<String, String> environment = builder.environment();
        environment.put("HOST", host());
        environment.put("PORT", Integer.toString(port()));
        return builder;
    }

    private static Installation prepareBundledInstallation() throws IOException {
        Path installDirectory = installDirectory();
        Path marker = installDirectory.resolve(".version");
        Path apiDirectory = installDirectory.resolve("api/node_modules/NeteaseCloudMusicApi");
        Path appScript = apiDirectory.resolve("app.js");
        Path nodeExecutable = nodeExecutable(installDirectory.resolve("node"));

        if (Files.isRegularFile(marker)
            && SERVICE_VERSION.equals(Files.readString(marker, StandardCharsets.UTF_8))
            && Files.isRegularFile(appScript)
            && Files.isRegularFile(nodeExecutable)) {
            return new Installation(nodeExecutable, appScript, apiDirectory);
        }

        deleteRecursively(installDirectory);
        Files.createDirectories(installDirectory);
        extractZipResource(API_ARCHIVE, installDirectory, false);
        extractNodeRuntime(installDirectory.resolve("node"));
        Files.writeString(marker, SERVICE_VERSION, StandardCharsets.UTF_8);

        nodeExecutable = nodeExecutable(installDirectory.resolve("node"));
        if (!Files.isRegularFile(appScript)) {
            throw new IOException("Bundled NeteaseCloudMusicApi app.js was not found after extraction");
        }
        if (!Files.isRegularFile(nodeExecutable)) {
            throw new IOException("Bundled Node.js executable was not found after extraction");
        }
        nodeExecutable.toFile().setExecutable(true, false);
        return new Installation(nodeExecutable, appScript, apiDirectory);
    }

    private static void extractNodeRuntime(Path targetDirectory) throws IOException {
        String resource = RESOURCE_ROOT + nodeArchiveName();
        if (resource.endsWith(".zip")) {
            extractZipResource(resource, targetDirectory, true);
        } else if (resource.endsWith(".tar.xz")) {
            extractTarXzResource(resource, targetDirectory, true);
        } else {
            throw new IOException("Unsupported bundled Node.js archive: " + resource);
        }
    }

    private static void extractZipResource(String resource, Path targetDirectory, boolean stripFirstDirectory) throws IOException {
        try (InputStream raw = resourceStream(resource);
             ZipInputStream input = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path relative = safeRelativePath(entry.getName(), stripFirstDirectory);
                if (relative == null) {
                    continue;
                }

                Path output = safeOutputPath(targetDirectory, relative);
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
                input.closeEntry();
            }
        }
    }

    private static void extractTarXzResource(String resource, Path targetDirectory, boolean stripFirstDirectory) throws IOException {
        try (InputStream raw = resourceStream(resource);
             XZCompressorInputStream xz = new XZCompressorInputStream(raw);
             TarArchiveInputStream input = new TarArchiveInputStream(xz)) {
            TarArchiveEntry entry;
            while ((entry = input.getNextTarEntry()) != null) {
                Path relative = safeRelativePath(entry.getName(), stripFirstDirectory);
                if (relative == null) {
                    continue;
                }

                Path output = safeOutputPath(targetDirectory, relative);
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else if (entry.isFile()) {
                    Files.createDirectories(output.getParent());
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                    if ((entry.getMode() & 0111) != 0) {
                        output.toFile().setExecutable(true, false);
                    }
                }
            }
        }
    }

    private static InputStream resourceStream(String resource) throws IOException {
        InputStream stream = NeteaseMusicLocalService.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("Missing bundled resource: " + resource);
        }
        return stream;
    }

    private static Path safeRelativePath(String name, boolean stripFirstDirectory) {
        Path path = Path.of(name.replace('\\', '/')).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            return null;
        }
        if (!stripFirstDirectory) {
            return path;
        }
        return path.getNameCount() > 1 ? path.subpath(1, path.getNameCount()) : null;
    }

    private static Path safeOutputPath(Path targetDirectory, Path relativePath) throws IOException {
        Path output = targetDirectory.resolve(relativePath).normalize();
        Path normalizedTarget = targetDirectory.toAbsolutePath().normalize();
        Path normalizedOutput = output.toAbsolutePath().normalize();
        if (!normalizedOutput.startsWith(normalizedTarget)) {
            throw new IOException("Archive entry escapes target directory: " + relativePath);
        }
        return output;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static Path installDirectory() {
        return PathManager.CLIENT_PATH.resolve("music-service").resolve(SERVICE_VERSION);
    }

    private static Path nodeExecutable(Path nodeDirectory) {
        if (isWindows()) {
            return nodeDirectory.resolve("node.exe");
        }
        return nodeDirectory.resolve("bin/node");
    }

    private static String nodeArchiveName() throws IOException {
        String platform = platformKey();
        return switch (platform) {
            case "windows-x64" -> "node-windows-x64.zip";
            case "linux-x64" -> "node-linux-x64.tar.xz";
            case "macos-x64" -> "node-macos-x64.tar.xz";
            case "macos-aarch64" -> "node-macos-aarch64.tar.xz";
            default -> throw new IOException("Unsupported platform for bundled Node.js: " + platform);
        };
    }

    private static String platformKey() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String normalizedArch = switch (arch) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch;
        };

        if (os.contains("win")) {
            return "windows-" + normalizedArch;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos-" + normalizedArch;
        }
        if (os.contains("linux")) {
            return "linux-" + normalizedArch;
        }
        return os + "-" + normalizedArch;
    }

    private static void waitUntilReady(Process startedProcess) {
        Thread thread = new Thread(() -> {
            long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
            while (startedProcess.isAlive() && System.nanoTime() < deadline) {
                if (isServiceAvailable()) {
                    NyxClient.LOGGER.info("Bundled NeteaseCloudMusicApi service is ready at {}", baseUrl());
                    return;
                }
                try {
                    Thread.sleep(READY_INTERVAL.toMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (startedProcess.isAlive()) {
                NyxClient.LOGGER.warn("Bundled NeteaseCloudMusicApi service did not become ready within {} seconds", READY_TIMEOUT.toSeconds());
            }
        }, "Nyx-NeteaseMusicApi-Ready");
        thread.setDaemon(true);
        thread.start();
    }

    private static void watch(Process startedProcess) {
        Thread thread = new Thread(() -> {
            try {
                int exitCode = startedProcess.waitFor();
                synchronized (LOCK) {
                    if (process == startedProcess) {
                        process = null;
                    }
                }
                if (exitCode != 0) {
                    NyxClient.LOGGER.warn("Bundled NeteaseCloudMusicApi service exited with code {}", exitCode);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "Nyx-NeteaseMusicApi-Watch");
        thread.setDaemon(true);
        thread.start();
    }

    private static void drain(InputStream stream, String name) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    NyxClient.LOGGER.debug("[NeteaseCloudMusicApi:{}] {}", name, line);
                }
            } catch (IOException exception) {
                NyxClient.LOGGER.debug("Failed to read bundled NeteaseCloudMusicApi {} stream", name, exception);
            }
        }, "Nyx-NeteaseMusicApi-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    private static String host() {
        return DEFAULT_HOST;
    }

    private static int port() {
        String value = System.getProperty("nyx.music.local.port");
        if (value == null || value.isBlank()) {
            value = System.getenv("NYX_MUSIC_LOCAL_PORT");
        }
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 && parsed <= 65535 ? parsed : DEFAULT_PORT;
        } catch (NumberFormatException exception) {
            return DEFAULT_PORT;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private record Installation(Path nodeExecutable, Path appScript, Path workingDirectory) {
    }
}
