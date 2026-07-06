package io.github.seraphina.nyxclient.utility.skija;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FramebufferFormat;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

public final class SkiaUtility {
    private static final int MAX_TRACKED_TEXTURE_UNITS = 12;

    private static DirectContext directContext;
    private static BackendRenderTarget renderTarget;
    private static Surface surface;

    private static int surfaceWidth;
    private static int surfaceHeight;
    private static int surfaceFramebufferId = -1;
    private static int surfaceStencilBits;

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

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget != null) {
            GpuTextureView colorView = mainTarget.getColorTextureView();
            if (colorView instanceof GlTextureView glColorView && RenderSystem.getDevice() instanceof GlDevice glDevice) {
                GpuTexture depthTexture = mainTarget.useDepth ? mainTarget.getDepthTexture() : null;
                int framebufferId = glColorView.getFbo(glDevice.directStateAccess(), depthTexture);
                int stencilBits = mainTarget.useStencil ? 8 : 0;
                return getSurface(colorView.getWidth(0), colorView.getHeight(0), framebufferId, stencilBits);
            }
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
        int stencilBits = GL11.glGetInteger(GL11.GL_STENCIL_BITS);
        return getSurface(width, height, framebufferId, stencilBits);
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

        GLState glState = GLState.capture();
        try {
            Canvas canvas = getCanvas();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, surfaceFramebufferId);
            GlStateManager._viewport(0, 0, surfaceWidth, surfaceHeight);
            directContext.resetGLAll();

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
        } finally {
            if (directContext != null) {
                directContext.resetGLAll();
            }
            glState.restore();
        }
    }

    private static Surface getSurface(int width, int height, int framebufferId, int stencilBits) {
        RenderSystem.assertOnRenderThread();
        ensureStarted();

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Skia surface size must be positive: " + width + "x" + height);
        }

        if (surface == null
            || surfaceWidth != width
            || surfaceHeight != height
            || surfaceFramebufferId != framebufferId
            || surfaceStencilBits != stencilBits) {
            rebuildSurface(width, height, framebufferId, stencilBits);
        }

        directContext.resetGLAll();
        return surface;
    }

    private static void rebuildSurface(int width, int height, int framebufferId, int stencilBits) {
        closeSurface();

        renderTarget = BackendRenderTarget.makeGL(
            width,
            height,
            0,
            stencilBits,
            framebufferId,
            FramebufferFormat.GR_GL_RGBA8
        );
        surface = Surface.wrapBackendRenderTarget(
            directContext,
            renderTarget,
            SurfaceOrigin.BOTTOM_LEFT,
            ColorType.RGBA_8888,
            null
        );

        surfaceWidth = width;
        surfaceHeight = height;
        surfaceFramebufferId = framebufferId;
        surfaceStencilBits = stencilBits;
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
        surfaceStencilBits = 0;
    }

    private static final class GLState {
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int program;
        private final int vertexArray;
        private final int arrayBuffer;
        private final int elementArrayBuffer;
        private final int activeTexture;
        private final int[] textureBindings;
        private final int[] samplerBindings;
        private final int[] viewport = new int[4];
        private final int[] scissorBox = new int[4];
        private final boolean scissorTest;
        private final boolean depthTest;
        private final boolean depthMask;
        private final boolean blend;
        private final boolean cull;
        private final boolean stencilTest;
        private final boolean framebufferSrgb;
        private final boolean[] colorMask = new boolean[4];
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final int blendEquationRgb;
        private final int blendEquationAlpha;

        private GLState() {
            this.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            this.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            this.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            this.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            this.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            this.elementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            this.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            this.scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            this.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            this.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            this.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            this.stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            this.framebufferSrgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
            this.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            this.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            this.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            this.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            this.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            this.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

            GL11.glGetIntegerv(GL11.GL_VIEWPORT, this.viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, this.scissorBox);
            readColorMask();

            int textureUnitCount = Math.min(MAX_TRACKED_TEXTURE_UNITS, GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));
            this.textureBindings = new int[textureUnitCount];
            this.samplerBindings = new int[textureUnitCount];
            for (int i = 0; i < textureUnitCount; i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                this.textureBindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                this.samplerBindings[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            }
            GL13.glActiveTexture(this.activeTexture);
        }

        private static GLState capture() {
            return new GLState();
        }

        private void restore() {
            if (this.readFramebuffer == this.drawFramebuffer) {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.drawFramebuffer);
            } else {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.readFramebuffer);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
            }

            GlStateManager._glUseProgram(this.program);
            GlStateManager._glBindVertexArray(this.vertexArray);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, this.arrayBuffer);
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.elementArrayBuffer);

            for (int i = 0; i < this.textureBindings.length; i++) {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
                GlStateManager._bindTexture(this.textureBindings[i]);
                GL33.glBindSampler(i, this.samplerBindings[i]);
            }
            GlStateManager._activeTexture(this.activeTexture);

            if (this.scissorTest) {
                GlStateManager._enableScissorTest();
            } else {
                GlStateManager._disableScissorTest();
            }
            GlStateManager._scissorBox(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);

            if (this.depthTest) {
                GlStateManager._enableDepthTest();
            } else {
                GlStateManager._disableDepthTest();
            }
            GlStateManager._depthMask(this.depthMask);

            if (this.blend) {
                GlStateManager._enableBlend();
            } else {
                GlStateManager._disableBlend();
            }
            GlStateManager._blendFuncSeparate(this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
            GL20.glBlendEquationSeparate(this.blendEquationRgb, this.blendEquationAlpha);

            if (this.cull) {
                GlStateManager._enableCull();
            } else {
                GlStateManager._disableCull();
            }

            if (this.stencilTest) {
                GlStateManager._enableStencilTest();
            } else {
                GlStateManager._disableStencilTest();
            }

            if (this.framebufferSrgb) {
                GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
            } else {
                GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            }

            GlStateManager._viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
            GlStateManager._colorMask(this.colorMask[0], this.colorMask[1], this.colorMask[2], this.colorMask[3]);
        }

        private void readColorMask() {
            ByteBuffer mask = BufferUtils.createByteBuffer(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < this.colorMask.length; i++) {
                this.colorMask[i] = mask.get(i) != 0;
            }
        }
    }
}
