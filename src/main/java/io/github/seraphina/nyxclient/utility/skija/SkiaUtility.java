package io.github.seraphina.nyxclient.utility.skija;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FramebufferFormat;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.Objects;
import java.util.function.Consumer;

public final class SkiaUtility {
    private static DirectContext directContext;
    private static BackendRenderTarget renderTarget;
    private static Surface surface;

    private static int surfaceWidth;
    private static int surfaceHeight;
    private static int surfaceFramebufferId = -1;

    private SkiaUtility() {
    }

    public static void start() {
        RenderSystem.assertOnRenderThread();

        if (isStarted()) {
            return;
        }

        directContext = DirectContext.makeGL();
    }

    public static void end() {
        RenderSystem.assertOnRenderThread();

        closeSurface();

        if (directContext != null) {
            directContext.close();
            directContext = null;
        }
    }

    public static boolean isStarted() {
        return directContext != null && !directContext.isClosed();
    }

    public static DirectContext getDirectContext() {
        ensureStarted();
        return directContext;
    }

    public static Surface getSurface() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            throw new IllegalStateException("Minecraft client is not initialized");
        }

        Window window = minecraft.getWindow();
        return getSurface(window.getWidth(), window.getHeight());
    }

    public static Surface getSurface(int width, int height) {
        RenderSystem.assertOnRenderThread();
        ensureStarted();

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Skia surface size must be positive: " + width + "x" + height);
        }

        int framebufferId = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if (surface == null || surfaceWidth != width || surfaceHeight != height || surfaceFramebufferId != framebufferId) {
            rebuildSurface(width, height, framebufferId);
        }

        directContext.resetGLAll();
        return surface;
    }

    public static Canvas getCanvas() {
        return getSurface().getCanvas();
    }

    public static int getGuiScale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            throw new IllegalStateException("Minecraft client is not initialized");
        }

        return Math.max(1, minecraft.getWindow().getGuiScale());
    }

    public static int getGuiScaledWidth() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            throw new IllegalStateException("Minecraft client is not initialized");
        }

        return minecraft.getWindow().getGuiScaledWidth();
    }

    public static int getGuiScaledHeight() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            throw new IllegalStateException("Minecraft client is not initialized");
        }

        return minecraft.getWindow().getGuiScaledHeight();
    }

    public static void draw(Consumer<Canvas> renderer) {
        drawGui(renderer);
    }

    public static void drawGui(Consumer<Canvas> renderer) {
        draw(renderer, true);
    }

    public static void drawRaw(Consumer<Canvas> renderer) {
        draw(renderer, false);
    }

    public static void flush() {
        RenderSystem.assertOnRenderThread();
        if (directContext != null && surface != null) {
            directContext.flushAndSubmit(surface);
        }
    }

    public static void discardSurface() {
        RenderSystem.assertOnRenderThread();
        closeSurface();
    }

    private static void ensureStarted() {
        if (!isStarted()) {
            start();
        }
    }

    private static void draw(Consumer<Canvas> renderer, boolean guiScaled) {
        Objects.requireNonNull(renderer, "renderer");
        RenderSystem.assertOnRenderThread();

        Canvas canvas = getCanvas();
        int saveCount = canvas.save();
        try {
            if (guiScaled) {
                int guiScale = getGuiScale();
                canvas.scale(guiScale, guiScale);
            }

            renderer.accept(canvas);
        } finally {
            canvas.restoreToCount(saveCount);
            flush();
        }
    }

    private static void rebuildSurface(int width, int height, int framebufferId) {
        closeSurface();

        int sampleCount = GL11.glGetInteger(GL13.GL_SAMPLES);
        int stencilBits = GL11.glGetInteger(GL11.GL_STENCIL_BITS);

        renderTarget = BackendRenderTarget.makeGL(
            width,
            height,
            sampleCount,
            stencilBits,
            framebufferId,
            FramebufferFormat.GR_GL_RGBA8
        );
        surface = Surface.wrapBackendRenderTarget(
            directContext,
            renderTarget,
            SurfaceOrigin.BOTTOM_LEFT,
            ColorType.RGBA_8888,
            ColorSpace.getSRGB()
        );

        surfaceWidth = width;
        surfaceHeight = height;
        surfaceFramebufferId = framebufferId;
    }

    private static void closeSurface() {
        if (surface != null) {
            surface.close();
            surface = null;
        }

        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }

        surfaceWidth = 0;
        surfaceHeight = 0;
        surfaceFramebufferId = -1;
    }
}
