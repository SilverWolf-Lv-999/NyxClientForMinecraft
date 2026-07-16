package io.github.seraphina.nyx.client.loading;

import net.neoforged.fml.earlydisplay.render.GlDebug;
import net.neoforged.fml.earlydisplay.render.GlState;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL32C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL32C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL32C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL32C.GL_NEAREST;
import static org.lwjgl.opengl.GL32C.GL_RGBA;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL32C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL32C.glBlitFramebuffer;
import static org.lwjgl.opengl.GL32C.glClear;
import static org.lwjgl.opengl.GL32C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL32C.glDeleteTextures;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL32C.glGenFramebuffers;
import static org.lwjgl.opengl.GL32C.glGenTextures;
import static org.lwjgl.opengl.GL32C.glTexImage2D;
import static org.lwjgl.opengl.GL32C.glTexParameteri;

final class NyxEarlyFramebuffer implements AutoCloseable {
    private final int framebuffer;
    private final int texture;
    private int width;
    private int height;

    NyxEarlyFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.framebuffer = glGenFramebuffers();
        this.texture = glGenTextures();

        GlState.bindFramebuffer(this.framebuffer);
        GlDebug.labelFramebuffer(this.framebuffer, "Nyx early framebuffer");
        GlState.bindTexture2D(this.texture);
        GlDebug.labelTexture(this.texture, "Nyx early backbuffer");
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (IntBuffer)null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.texture, 0);
        GlState.bindFramebuffer(0);
    }

    void activate() {
        GlState.bindFramebuffer(this.framebuffer);
    }

    void deactivate() {
        GlState.bindFramebuffer(0);
    }

    void blitToScreen(ThemeColor backgroundColor, int windowFramebufferWidth, int windowFramebufferHeight) {
        float widthScale = windowFramebufferWidth / (float)this.width;
        float heightScale = windowFramebufferHeight / (float)this.height;
        float scale = Math.min(widthScale, heightScale) * 0.5F;
        int left = (int)(windowFramebufferWidth * 0.5F - scale * this.width);
        int top = (int)(windowFramebufferHeight * 0.5F - scale * this.height);
        int right = (int)(windowFramebufferWidth * 0.5F + scale * this.width);
        int bottom = (int)(windowFramebufferHeight * 0.5F + scale * this.height);

        GlState.bindDrawFramebuffer(0);
        GlState.bindReadFramebuffer(this.framebuffer);
        GlState.clearColor(backgroundColor.r(), backgroundColor.g(), backgroundColor.b(), 1.0F);
        glClear(GL_COLOR_BUFFER_BIT);
        glBlitFramebuffer(
            0,
            this.height,
            this.width,
            0,
            clamp(left, 0, windowFramebufferWidth),
            clamp(top, 0, windowFramebufferHeight),
            clamp(right, 0, windowFramebufferWidth),
            clamp(bottom, 0, windowFramebufferHeight),
            GL_COLOR_BUFFER_BIT,
            GL_NEAREST
        );
        GlState.bindFramebuffer(0);
    }

    void resize(int width, int height) {
        if (this.width == width && this.height == height) {
            return;
        }

        GlState.bindFramebuffer(this.framebuffer);
        GlState.bindTexture2D(this.texture);
        this.width = width;
        this.height = height;
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (IntBuffer)null);
    }

    int textureId() {
        return this.texture;
    }

    int width() {
        return this.width;
    }

    int height() {
        return this.height;
    }

    @Override
    public void close() {
        glDeleteTextures(this.texture);
        glDeleteFramebuffers(this.framebuffer);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
