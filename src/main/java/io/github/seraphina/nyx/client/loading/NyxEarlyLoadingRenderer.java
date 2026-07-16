package io.github.seraphina.nyx.client.loading;

import net.neoforged.fml.earlydisplay.render.GlDebug;
import net.neoforged.fml.earlydisplay.render.GlState;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleBufferBuilder;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_RENDERER;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_VENDOR;
import static org.lwjgl.opengl.GL11C.GL_VERSION;
import static org.lwjgl.opengl.GL11C.GL_ZERO;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL11C.glGetString;

final class NyxEarlyLoadingRenderer implements AutoCloseable {
    static final int LAYOUT_WIDTH = 854;
    static final int LAYOUT_HEIGHT = 480;

    private static final Logger LOGGER = LoggerFactory.getLogger(NyxEarlyLoadingRenderer.class);
    private static final String TITLE = "Nyx Client";
    private static final long MIN_FRAME_TIME = TimeUnit.MILLISECONDS.toNanos(10);
    private static final int BACKGROUND_TOP = 0xFF070A12;
    private static final int BACKGROUND_BOTTOM = 0xFF101722;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int TITLE_SHADOW = 0x52000000;
    private static final int TEXT_COLOR = 0xFFE9EEF6;
    private static final int TEXT_SHADOW = 0x66000000;
    private static final int ACCENT_COLOR = 0xFF8AD8FF;

    private final long glfwWindow;
    private final NyxEarlyFramebuffer framebuffer;
    private final MaterializedTheme theme;
    private final SimpleBufferBuilder buffer = new SimpleBufferBuilder("nyx-early-shared", 8192);
    private final SimpleFont titleFont;
    private final SimpleFont textFont;
    private final Semaphore renderLock = new Semaphore(1);
    private final ScheduledFuture<?> automaticRendering;
    private final ScheduledFuture<?> animationTicker;
    private final long startNanos = System.nanoTime();

    private volatile boolean complete;
    private int animationFrame;
    private long nextFrameTime;
    private float shownProgress;

    NyxEarlyLoadingRenderer(ScheduledExecutorService scheduler, long glfwWindow) {
        this.glfwWindow = glfwWindow;
        glfwMakeContextCurrent(glfwWindow);
        glfwSwapInterval(1);
        var capabilities = GL.createCapabilities();
        GlState.readFromOpenGL();
        GlDebug.setCapabilities(capabilities);
        LOGGER.info("Nyx early window GL info: {} GL version {}, {}", glGetString(GL_RENDERER), glGetString(GL_VERSION), glGetString(GL_VENDOR));

        this.theme = MaterializedTheme.materialize(Theme.createDefaultTheme(), null);
        this.titleFont = EarlyAppleFontAtlas.create(60.0F);
        this.textFont = EarlyAppleFontAtlas.create(22.0F);
        this.framebuffer = new NyxEarlyFramebuffer(LAYOUT_WIDTH, LAYOUT_HEIGHT);

        GlState.clearColor(0.03F, 0.05F, 0.08F, 1.0F);
        GL32C.glClear(GL_COLOR_BUFFER_BIT);
        GlState.enableBlend(true);
        GlState.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glfwMakeContextCurrent(0);

        this.automaticRendering = scheduler.scheduleWithFixedDelay(this::renderToScreen, 50L, 50L, TimeUnit.MILLISECONDS);
        this.animationTicker = scheduler.scheduleWithFixedDelay(() -> this.animationFrame++, 1L, 50L, TimeUnit.MILLISECONDS);
    }

    void markComplete() {
        this.complete = true;
    }

    void stopAutomaticRendering() throws InterruptedException, TimeoutException {
        if (this.automaticRendering.isCancelled()) {
            return;
        }
        if (!this.renderLock.tryAcquire(5L, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }
        try {
            this.automaticRendering.cancel(false);
            this.animationTicker.cancel(false);
        } finally {
            this.renderLock.release();
        }
    }

    void renderToScreen() {
        if (!this.renderLock.tryAcquire()) {
            return;
        }
        try {
            long now = System.nanoTime();
            if (now < this.nextFrameTime) {
                return;
            }
            this.nextFrameTime = now + MIN_FRAME_TIME;
            glfwMakeContextCurrent(this.glfwWindow);
            GlState.readFromOpenGL();
            var backup = GlState.createSnapshot();

            int[] width = new int[1];
            int[] height = new int[1];
            glfwGetFramebufferSize(this.glfwWindow, width, height);
            this.framebuffer.resize(width[0], height[0]);
            renderToFramebuffer();

            GlState.viewport(0, 0, width[0], height[0]);
            this.framebuffer.blitToScreen(ThemeColor.ofArgb(BACKGROUND_TOP), width[0], height[0]);
            glfwSwapBuffers(this.glfwWindow);

            GlState.applySnapshot(backup);
        } catch (Throwable throwable) {
            LOGGER.error("Unexpected error while rendering the Nyx early loading screen", throwable);
        } finally {
            if (!this.automaticRendering.isCancelled()) {
                glfwMakeContextCurrent(0);
            }
            this.renderLock.release();
        }
    }

    void renderToFramebuffer() {
        GlDebug.pushGroup("update Nyx early loading framebuffer");
        GlState.readFromOpenGL();
        var backup = GlState.createSnapshot();

        this.framebuffer.activate();
        Viewport viewport = fitViewport();
        GlState.clearColor(0.03F, 0.05F, 0.08F, 1.0F);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GlState.enableBlend(true);
        GlState.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);

        for (var shader : this.theme.shaders().values()) {
            shader.activate();
            if (shader.hasUniform("screenSize")) {
                shader.setUniform2f("screenSize", LAYOUT_WIDTH, LAYOUT_HEIGHT);
            }
        }

        RenderContext context = new RenderContext(
            this.buffer,
            this.theme,
            LAYOUT_WIDTH,
            LAYOUT_HEIGHT,
            viewport.offsetX(),
            viewport.offsetY(),
            viewport.scale(),
            this.animationFrame
        );
        renderNyx(context);

        this.framebuffer.deactivate();
        GlState.applySnapshot(backup);
        GlDebug.popGroup();
    }

    int getFramebufferTextureId() {
        return this.framebuffer.textureId();
    }

    @Override
    public void close() {
        this.automaticRendering.cancel(false);
        this.animationTicker.cancel(false);

        long previousContext = GLFW.glfwGetCurrentContext();
        var previousCapabilities = GL.getCapabilities();
        boolean restoreContext = previousContext != this.glfwWindow;
        if (restoreContext) {
            GLFW.glfwMakeContextCurrent(this.glfwWindow);
            GL.createCapabilities();
        }

        try {
            this.titleFont.close();
            this.textFont.close();
            this.theme.close();
            this.framebuffer.close();
            this.buffer.close();
            SimpleBufferBuilder.destroy();
        } finally {
            if (restoreContext) {
                GLFW.glfwMakeContextCurrent(previousContext);
                GL.setCapabilities(previousCapabilities);
            }
        }
    }

    private Viewport fitViewport() {
        float desiredAspect = LAYOUT_WIDTH / (float)LAYOUT_HEIGHT;
        float actualAspect = this.framebuffer.width() / (float)this.framebuffer.height();
        int offsetX;
        int offsetY;
        float scale;
        if (actualAspect > desiredAspect) {
            float actualWidth = desiredAspect * this.framebuffer.height();
            offsetX = (int)(this.framebuffer.width() - actualWidth) / 2;
            offsetY = 0;
            GlState.viewport(offsetX, 0, (int)actualWidth, this.framebuffer.height());
            scale = this.framebuffer.height() / (float)LAYOUT_HEIGHT;
        } else {
            float actualHeight = this.framebuffer.width() / desiredAspect;
            offsetX = 0;
            offsetY = (int)(this.framebuffer.height() - actualHeight) / 2;
            GlState.viewport(0, offsetY, this.framebuffer.width(), (int)actualHeight);
            scale = this.framebuffer.width() / (float)LAYOUT_WIDTH;
        }
        return new Viewport(offsetX, offsetY, scale);
    }

    private void renderNyx(RenderContext context) {
        float progress = displayedProgress();
        context.fillRect(0.0F, 0.0F, LAYOUT_WIDTH, LAYOUT_HEIGHT, BACKGROUND_TOP, BACKGROUND_BOTTOM);
        renderTitle(context);
        renderProgress(context, progress);
    }

    private void renderTitle(RenderContext context) {
        float x = (LAYOUT_WIDTH - this.titleFont.stringWidth(TITLE)) * 0.5F;
        float y = (LAYOUT_HEIGHT - this.titleFont.lineSpacing()) * 0.5F - 10.0F;
        int visibleIndex = 0;
        for (int offset = 0; offset < TITLE.length(); ) {
            int codePoint = TITLE.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            offset += Character.charCount(codePoint);
            float advance = this.titleFont.stringWidth(glyph);
            if (codePoint == ' ') {
                x += advance;
                visibleIndex++;
                continue;
            }

            float bounce = bounceOffset(visibleIndex++);
            renderText(context, x + 2.0F, y + 4.0F + bounce, this.titleFont, glyph, TITLE_SHADOW);
            renderText(context, x, y + bounce, this.titleFont, glyph, TITLE_COLOR);
            x += advance;
        }
    }

    private void renderProgress(RenderContext context, float progress) {
        String text = Math.round(progress * 100.0F) + "%";
        float x = LAYOUT_WIDTH - 24.0F - this.textFont.stringWidth(text);
        float y = LAYOUT_HEIGHT - 24.0F - this.textFont.lineSpacing();
        renderText(context, x + 1.0F, y + 2.0F, this.textFont, text, TEXT_SHADOW);
        renderText(context, x, y, this.textFont, text, TEXT_COLOR);

        float barWidth = 148.0F;
        float barHeight = 2.0F;
        float barX = LAYOUT_WIDTH - 24.0F - barWidth;
        float barY = LAYOUT_HEIGHT - 14.0F;
        context.fillRect(barX, barY, barWidth, barHeight, 0x22FFFFFF);
        context.fillRect(barX, barY, barWidth * progress, barHeight, ACCENT_COLOR);
    }

    private void renderText(RenderContext context, float x, float y, SimpleFont font, String text, int color) {
        context.renderText(x, y, font, List.of(new SimpleFont.DisplayText(text, color)));
    }

    private float bounceOffset(int index) {
        float cycle = 1.55F;
        float phase = ((this.animationFrame * 0.065F) - index * 0.115F) % cycle;
        if (phase < 0.0F) {
            phase += cycle;
        }
        if (phase > 0.55F) {
            return 0.0F;
        }
        return -18.0F * (float)Math.sin((phase / 0.55F) * Math.PI);
    }

    private float displayedProgress() {
        float target = targetProgress();
        float smoothing = target >= this.shownProgress ? 0.08F : 0.2F;
        this.shownProgress += (target - this.shownProgress) * smoothing;
        if (target >= 0.999F && this.shownProgress > 0.985F) {
            this.shownProgress = 1.0F;
        }
        return clamp01(this.shownProgress);
    }

    private float targetProgress() {
        int totalSteps = 0;
        int totalCurrent = 0;
        for (ProgressMeter progress : StartupNotificationManager.getCurrentProgress()) {
            if (progress.steps() <= 0) {
                continue;
            }
            totalSteps += progress.steps();
            totalCurrent += Math.max(0, Math.min(progress.current(), progress.steps()));
        }
        if (totalSteps > 0) {
            return clamp01(totalCurrent / (float)totalSteps);
        }
        if (this.complete) {
            return 1.0F;
        }

        float elapsedSeconds = (System.nanoTime() - this.startNanos) / 1_000_000_000.0F;
        return Math.min(0.92F, elapsedSeconds / 42.0F);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(value, 1.0F);
    }

    private record Viewport(int offsetX, int offsetY, float scale) {
    }
}
