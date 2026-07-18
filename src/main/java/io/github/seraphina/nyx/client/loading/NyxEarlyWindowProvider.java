package io.github.seraphina.nyx.client.loading;

import joptsimple.OptionParser;
import io.github.seraphina.nyx.client.utility.SeraNative;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.error.ErrorDisplay;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.ProgramArgs;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_CREATION_API;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_NATIVE_CONTEXT_API;
import static org.lwjgl.glfw.GLFW.GLFW_NO_ERROR;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_API;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_DEBUG_CONTEXT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwGetError;
import static org.lwjgl.glfw.GLFW.glfwGetMonitorPos;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwMaximizeWindow;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;

public final class NyxEarlyWindowProvider extends DisplayWindow {
    public static final String PROVIDER_NAME = "nyxearlywindow";

    private static final Logger LOGGER = LoggerFactory.getLogger(NyxEarlyWindowProvider.class);
    private static final ThreadGroup BACKGROUND_THREAD_GROUP = new ThreadGroup("nyx-loadingscreen");
    private static final String ERROR_URL = "https://links.neoforged.net/early-display-errors";

    private final ProgressMeter mainProgress;
    private final ReentrantLock crashLock = new ReentrantLock();

    private ScheduledFuture<NyxEarlyLoadingRenderer> rendererFuture;
    private ScheduledExecutorService renderScheduler;
    private Runnable repaintTick = () -> {
    };
    private long window;
    private int winWidth;
    private int winHeight;
    private volatile boolean closed;
    @Nullable
    private String assetsDir;
    @Nullable
    private String assetIndex;
    @Nullable
    private String neoForgeVersion;
    @Nullable
    private String minecraftVersion;

    public NyxEarlyWindowProvider() {
        super();
        super.completeProgress();
        this.mainProgress = StartupNotificationManager.addProgressBar("", 0);
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public void initialize(ProgramArgs arguments) {
        LOGGER.info("Sera native high-performance GPU hint {}", SeraNative.loadStatus());
        LOGGER.info("Native high-performance GPU request status {}", SeraNative.requestNativeHighPerformanceGpu());
        LOGGER.info("Windows high-performance GPU preference status {}", SeraNative.ensureHighPerformanceGpuPreference());
        NyxEarlyLocatedPathCleaner.removeNyxModPathsFromFmlLocated("immediate window provider");

        OptionParser parser = new OptionParser();
        var widthOption = parser.accepts("width")
            .withRequiredArg().ofType(Integer.class)
            .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH));
        var heightOption = parser.accepts("height")
            .withRequiredArg().ofType(Integer.class)
            .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT));
        var maximizedOption = parser.accepts("earlywindow.maximized");
        var assetsDirOption = parser.accepts("assetsDir").withRequiredArg().ofType(String.class);
        var assetIndexOption = parser.accepts("assetIndex").withRequiredArg().ofType(String.class);
        parser.allowsUnrecognizedOptions();
        var parsed = parser.parse(arguments.getArguments());

        this.winWidth = parsed.valueOf(widthOption);
        this.winHeight = parsed.valueOf(heightOption);
        if (parsed.has(assetsDirOption) && parsed.has(assetIndexOption)) {
            this.assetsDir = parsed.valueOf(assetsDirOption);
            this.assetIndex = parsed.valueOf(assetIndexOption);
        }

        boolean maximized = parsed.has(maximizedOption) || FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_MAXIMIZED);
        this.renderScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().group(BACKGROUND_THREAD_GROUP)
                .name("nyx-loadingscreen")
                .daemon()
                .uncaughtExceptionHandler((thread, throwable) -> {
                    System.err.println("Uncaught error on Nyx loading screen thread: " + throwable);
                    throwable.printStackTrace();
                })
                .factory()
        );

        initWindow(maximized);
        this.rendererFuture = this.renderScheduler.schedule(
            () -> new NyxEarlyLoadingRenderer(this.renderScheduler, this.window),
            1L,
            TimeUnit.MILLISECONDS
        );
        updateProgress("Initializing Game Graphics");
    }

    @Override
    public void setMinecraftVersion(String version) {
        this.minecraftVersion = version;
    }

    @Override
    public void setNeoForgeVersion(String version) {
        if (!Objects.equals(this.neoForgeVersion, version)) {
            this.neoForgeVersion = version;
            StartupNotificationManager.modLoaderConsumer().ifPresent(consumer -> consumer.accept("Starting NeoForge " + version));
        }
    }

    @Override
    public long takeOverGlfwWindow() {
        NyxEarlyLoadingRenderer renderer = renderer();
        updateProgress("Initializing Game Graphics");
        try {
            renderer.stopAutomaticRendering();
        } catch (TimeoutException exception) {
            dumpBackgroundThreadStack();
            crashElegantly("Cannot hand over rendering to Minecraft. The Nyx loading screen renderer seems stuck.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        completeProgress();
        glfwMakeContextCurrent(this.window);
        glfwSwapInterval(0);
        glfwSetWindowSizeCallback(this.window, null).close();
        this.repaintTick = renderer::renderToScreen;
        return this.window;
    }

    @Override
    public void periodicTick() {
        if (this.rendererFuture != null && this.rendererFuture.state() == Future.State.FAILED) {
            throw new RuntimeException("Initialization of the Nyx loading screen failed.", this.rendererFuture.exceptionNow());
        }
        glfwPollEvents();
        if (!this.closed) {
            this.repaintTick.run();
        }
    }

    @Override
    public void updateProgress(String label) {
        this.mainProgress.label(label);
    }

    @Override
    public void completeProgress() {
        this.mainProgress.complete();
        if (this.rendererFuture != null && this.rendererFuture.isDone() && this.rendererFuture.state() == Future.State.SUCCESS) {
            this.rendererFuture.resultNow().markComplete();
        }
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        if (this.renderScheduler != null) {
            this.renderScheduler.shutdown();
        }
        if (this.rendererFuture != null) {
            try {
                this.rendererFuture.get().close();
            } catch (ExecutionException exception) {
                LOGGER.error("Cannot close Nyx early renderer since it failed to initialize", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void crash(String message) {
        crashElegantly(message);
    }

    @Override
    public void displayFatalErrorAndExit(List<ModLoadingIssue> issues, @Nullable Path modsFolder, @Nullable Path logFile, @Nullable Path crashReportFile) {
        long windowId = takeOverGlfwWindow();
        GL.createCapabilities();
        close();
        ErrorDisplay.fatal(windowId, this.assetsDir, this.assetIndex, issues, modsFolder, logFile, crashReportFile);
    }

    @Override
    public void renderToFramebuffer() {
        renderer().renderToFramebuffer();
    }

    @Override
    public int getFramebufferTextureId() {
        return renderer().getFramebufferTextureId();
    }

    private NyxEarlyLoadingRenderer renderer() {
        try {
            return this.rendererFuture.get(30L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        } catch (ExecutionException exception) {
            throw new RuntimeException(exception);
        } catch (TimeoutException exception) {
            dumpBackgroundThreadStack();
            crashElegantly("We seem to be having trouble initializing the Nyx loading window, waited for 30 seconds.");
            throw new IllegalStateException("Nyx early renderer timed out", exception);
        }
    }

    private void initWindow(boolean maximized) {
        long glfwInitBegin = System.nanoTime();
        if (!glfwInit()) {
            crashElegantly("We are unable to initialize the graphics system.\nglfwInit failed.\n");
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        long glfwInitEnd = System.nanoTime();
        if (glfwInitEnd - glfwInitBegin > 1_000_000_000L) {
            LOGGER.error("WARNING: glfwInit took {} seconds to start.", (glfwInitEnd - glfwInitBegin) / 1.0E9);
        }
        getLastGlfwError().ifPresent(error -> LOGGER.error("Suppressing last GLFW error: {}", error));

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.DEBUG_OPENGL)) {
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
        }

        long primaryMonitor = glfwGetPrimaryMonitor();
        if (primaryMonitor == 0L) {
            crashElegantly("Failed to locate a primary monitor.\nglfwGetPrimaryMonitor failed.\n");
            throw new IllegalStateException("Cannot find a primary monitor");
        }
        GLFWVidMode vidMode = glfwGetVideoMode(primaryMonitor);
        if (vidMode == null) {
            crashElegantly("Failed to get current display resolution.\nglfwGetVideoMode failed.\n");
            throw new IllegalStateException("Cannot get display resolution");
        }

        AtomicBoolean successfulWindow = new AtomicBoolean(false);
        var windowFailFuture = this.renderScheduler.schedule(() -> {
            if (!successfulWindow.get()) {
                crashElegantly("Timed out trying to set up the game window.");
            }
        }, 30L, TimeUnit.SECONDS);

        this.window = glfwCreateWindow(this.winWidth, this.winHeight, "Minecraft: Nyx Client Loading...", 0L, 0L);
        String creationError = getLastGlfwError().orElse("unknown error");
        if (this.window == 0L) {
            LOGGER.error("Failed to create window: {}", creationError);
            crashElegantly("Failed to create a window:\n" + creationError);
            throw new IllegalStateException("Failed to create a window");
        }

        successfulWindow.set(true);
        if (!windowFailFuture.cancel(true)) {
            throw new IllegalStateException("Window creation watchdog was not cancelled");
        }

        int[] x = new int[1];
        int[] y = new int[1];
        glfwGetMonitorPos(primaryMonitor, x, y);
        int monitorX = x[0];
        int monitorY = y[0];
        if (maximized) {
            glfwMaximizeWindow(this.window);
        }

        glfwGetWindowSize(this.window, x, y);
        this.winWidth = x[0];
        this.winHeight = y[0];
        glfwSetWindowPos(this.window, (vidMode.width() - this.winWidth) / 2 + monitorX, (vidMode.height() - this.winHeight) / 2 + monitorY);
        glfwSetWindowSizeCallback(this.window, this::winResize);
        glfwShowWindow(this.window);
        getLastGlfwError().ifPresent(error -> LOGGER.warn("Failed to show and position window: {}", error));
        glfwPollEvents();
    }

    private void winResize(long resizedWindow, int width, int height) {
        if (resizedWindow == this.window && width != 0 && height != 0) {
            this.winWidth = width;
            this.winHeight = height;
        }
    }

    private void crashElegantly(String errorDetails) {
        this.crashLock.lock();
        StringBuilder message = new StringBuilder(2000);
        message.append("Failed to initialize the mod loading system and display.\n\n");
        message.append("Failure details:\n");
        message.append(errorDetails);
        message.append("\n\nIf you click yes, we will try and open ");
        message.append(ERROR_URL);
        message.append(" in your default browser");
        LOGGER.error("ERROR DISPLAY\n{}", message);

        Thread thread = new Thread(() -> {
            boolean result = TinyFileDialogs.tinyfd_messageBox("Minecraft: Nyx Client", message.toString(), "yesno", "error", true);
            if (result) {
                try {
                    Desktop.getDesktop().browse(URI.create(ERROR_URL));
                } catch (IOException exception) {
                    TinyFileDialogs.tinyfd_messageBox("Minecraft: Nyx Client", "Could not open your browser.\nVisit " + ERROR_URL, "ok", "error", true);
                }
            }
        }, "nyx-crash-report");
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException ignored) {
        }
        System.exit(1);
    }

    private static Optional<String> getLastGlfwError() {
        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            PointerBuffer pointerBuffer = memoryStack.mallocPointer(1);
            int error = glfwGetError(pointerBuffer);
            if (error != GLFW_NO_ERROR) {
                long descriptionPointer = pointerBuffer.get();
                String description = descriptionPointer == 0L ? null : MemoryUtil.memUTF8(descriptionPointer);
                if (description != null) {
                    return Optional.of(String.format(Locale.ROOT, "[0x%X] %s", error, description));
                }
                return Optional.of(String.format(Locale.ROOT, "[0x%X]", error));
            }
        }
        return Optional.empty();
    }

    private static void dumpBackgroundThreadStack() {
        BACKGROUND_THREAD_GROUP.list();
    }
}
