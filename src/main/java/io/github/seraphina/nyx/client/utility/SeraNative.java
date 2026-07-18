package io.github.seraphina.nyx.client.utility;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/*
* 想要AI写GPU加速运算，死活写不出来，气笑了
* */
public final class SeraNative {
    private static final int PROBE_MAGIC = 0x4E5958;
    private static final int NATIVE_HINT_WINDOWS = 1;
    private static final int NATIVE_HINT_NVAPI_LOADED = 1 << 1;
    private static final int NATIVE_HINT_NVCUDA_LOADED = 1 << 2;
    private static final String PROPERTY_LOAD_STATUS = "nyx.native.loadStatus";
    private static final String PROPERTY_LOAD_PATH = "nyx.native.loadPath";
    private static final String PROPERTY_GPU_HINT_STATUS = "nyx.native.gpuHintStatus";
    private static final String GPU_PREFERENCE_KEY = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences";
    private static final String HIGH_PERFORMANCE_GPU = "GpuPreference=2;";
    private static final boolean AVAILABLE = load();
    private static GpuPreferenceStatus gpuPreferenceStatus;

    private SeraNative() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static String loadStatus() {
        return System.getProperty(PROPERTY_LOAD_STATUS, AVAILABLE ? "loaded" : "unavailable");
    }

    public static synchronized GpuPreferenceStatus ensureHighPerformanceGpuPreference() {
        if (gpuPreferenceStatus != null) {
            return gpuPreferenceStatus;
        }
        if (!isWindows()) {
            gpuPreferenceStatus = GpuPreferenceStatus.NOT_WINDOWS;
            return gpuPreferenceStatus;
        }

        Set<String> executablePaths = javaExecutablePaths();
        if (executablePaths.isEmpty()) {
            gpuPreferenceStatus = GpuPreferenceStatus.NO_JAVA_EXECUTABLE;
            return gpuPreferenceStatus;
        }

        boolean updated = false;
        boolean failed = false;
        for (String executablePath : executablePaths) {
            PreferenceWriteResult result = writeHighPerformanceGpuPreference(executablePath);
            updated |= result == PreferenceWriteResult.UPDATED;
            failed |= result == PreferenceWriteResult.FAILED;
        }

        if (updated) {
            gpuPreferenceStatus = GpuPreferenceStatus.UPDATED_RESTART_REQUIRED;
        } else if (failed) {
            gpuPreferenceStatus = GpuPreferenceStatus.FAILED;
        } else {
            gpuPreferenceStatus = GpuPreferenceStatus.ALREADY_CONFIGURED;
        }
        return gpuPreferenceStatus;
    }

    public static NativeGpuHintStatus requestNativeHighPerformanceGpu() {
        if (!AVAILABLE) {
            return cachedNativeGpuHintStatus();
        }

        int flags;
        try {
            flags = nativeRequestHighPerformanceGpu();
        } catch (UnsatisfiedLinkError ignored) {
            return cachedNativeGpuHintStatus();
        }
        boolean windows = (flags & NATIVE_HINT_WINDOWS) != 0;
        boolean nvapiLoaded = (flags & NATIVE_HINT_NVAPI_LOADED) != 0;
        boolean nvcudaLoaded = (flags & NATIVE_HINT_NVCUDA_LOADED) != 0;
        NativeGpuHintStatus status;
        if (nvapiLoaded || nvcudaLoaded) {
            status = NativeGpuHintStatus.NVIDIA_DRIVER_TOUCHED;
        } else {
            status = windows ? NativeGpuHintStatus.NVIDIA_DRIVER_NOT_FOUND : NativeGpuHintStatus.NOT_WINDOWS;
        }
        System.setProperty(PROPERTY_GPU_HINT_STATUS, status.name());
        return status;
    }

    private static boolean load() {
        if (isProcessNativeLoaded()) {
            return true;
        }

        if (loadBundled() && probe()) {
            markLoaded("bundled", System.getProperty(PROPERTY_LOAD_PATH, ""));
            return true;
        }

        try {
            System.loadLibrary(System.getProperty("nyx.native.library", "sera_native"));
            boolean loaded = probe();
            if (loaded) {
                markLoaded("library-path", System.getProperty("nyx.native.library", "sera_native"));
            }
            return loaded;
        } catch (SecurityException | UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    private static boolean loadBundled() {
        for (String libraryName : nativeLibraryNames()) {
            if (loadBundled(libraryName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean loadBundled(String libraryName) {
        String resourcePath = "/assets/nyxclient/native/" + platformId() + "/" + libraryName;
        try (InputStream input = SeraNative.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return false;
            }

            Path directory = Files.createDirectories(Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "nyxclient-native"
            ));
            Path library = directory.resolve(libraryName);
            Files.copy(input, library, StandardCopyOption.REPLACE_EXISTING);
            System.load(library.toAbsolutePath().toString());
            System.setProperty(PROPERTY_LOAD_PATH, library.toAbsolutePath().toString());
            return true;
        } catch (IOException | SecurityException | UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    private static boolean isProcessNativeLoaded() {
        return System.getProperty(PROPERTY_LOAD_STATUS, "").startsWith("loaded");
    }

    private static void markLoaded(String source, String path) {
        System.setProperty(PROPERTY_LOAD_STATUS, "loaded:" + source);
        if (path != null && !path.isBlank()) {
            System.setProperty(PROPERTY_LOAD_PATH, path);
        }
    }

    private static NativeGpuHintStatus cachedNativeGpuHintStatus() {
        String status = System.getProperty(PROPERTY_GPU_HINT_STATUS);
        if (status != null) {
            try {
                return NativeGpuHintStatus.valueOf(status);
            } catch (IllegalArgumentException ignored) {
                // Fall through to the default unavailable state.
            }
        }
        return NativeGpuHintStatus.UNAVAILABLE;
    }

    private static Set<String> nativeLibraryNames() {
        Set<String> names = new LinkedHashSet<>();
        if (isWindows()) {
            names.add("libsera_native.dll");
        }
        names.add(System.mapLibraryName("sera_native"));
        return names;
    }

    private static String platformId() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String cpu = arch.contains("64") ? "x64" : "x86";
        if (os.contains("win")) {
            return "windows-" + cpu;
        }
        if (os.contains("mac")) {
            return "macos-" + cpu;
        }
        return "linux-" + cpu;
    }

    private static boolean probe() {
        try {
            return nativeProbe() == PROBE_MAGIC;
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static Set<String> javaExecutablePaths() {
        Set<String> paths = new LinkedHashSet<>();
        ProcessHandle.current().info().command().ifPresent(paths::add);

        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            Path bin = Path.of(javaHome, "bin");
            paths.add(bin.resolve("javaw.exe").toString());
            paths.add(bin.resolve("java.exe").toString());
        }

        return paths;
    }

    private static PreferenceWriteResult writeHighPerformanceGpuPreference(String executablePath) {
        try {
            if (isHighPerformancePreferenceConfigured(executablePath)) {
                return PreferenceWriteResult.ALREADY_CONFIGURED;
            }
            Process process = new ProcessBuilder(
                    "reg",
                    "add",
                    GPU_PREFERENCE_KEY,
                    "/v",
                    executablePath,
                    "/t",
                    "REG_SZ",
                    "/d",
                    HIGH_PERFORMANCE_GPU,
                    "/f"
            ).redirectErrorStream(true).start();
            if (!process.waitFor(2L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return PreferenceWriteResult.FAILED;
            }
            return process.exitValue() == 0 ? PreferenceWriteResult.UPDATED : PreferenceWriteResult.FAILED;
        } catch (IOException ignored) {
            return PreferenceWriteResult.FAILED;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return PreferenceWriteResult.FAILED;
        }
    }

    private static boolean isHighPerformancePreferenceConfigured(String executablePath) {
        try {
            Process process = new ProcessBuilder(
                    "reg",
                    "query",
                    GPU_PREFERENCE_KEY,
                    "/v",
                    executablePath
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(2L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0 && output.contains(HIGH_PERFORMANCE_GPU);
        } catch (IOException ignored) {
            return false;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static native int nativeProbe();

    private static native int nativeRequestHighPerformanceGpu();

    private enum PreferenceWriteResult {
        ALREADY_CONFIGURED,
        UPDATED,
        FAILED
    }

    public enum GpuPreferenceStatus {
        ALREADY_CONFIGURED,
        UPDATED_RESTART_REQUIRED,
        FAILED,
        NO_JAVA_EXECUTABLE,
        NOT_WINDOWS
    }

    public enum NativeGpuHintStatus {
        NVIDIA_DRIVER_TOUCHED,
        NVIDIA_DRIVER_NOT_FOUND,
        UNAVAILABLE,
        NOT_WINDOWS
    }
}
