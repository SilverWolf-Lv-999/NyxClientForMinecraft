package io.github.seraphina.nyx.client.utility;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.seraphina.nyx.client.utility.render.GL;
import io.github.seraphina.nyx.client.utility.render.Shader;
import io.github.seraphina.nyx.client.utility.render.Shaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32C;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Render2DUtility {
    private static final int MIN_SEGMENTS = 32;
    private static final int MAX_SEGMENTS = 64;
    private static final float MAX_GAUSSIAN_BLUR_RADIUS = 32.0F;
    private static final RenderPipeline GUI_TEXTURED_TRIANGLE_FAN = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("nyxclient", "pipeline/gui_textured_triangle_fan"))
        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLE_FAN)
        .build();
    private static final RenderPipeline GUI_TRIANGLE_STRIP = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("nyxclient", "pipeline/gui_triangle_strip"))
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
        .build();
    private static final ThreadLocal<GuiGraphics> CURRENT_GRAPHICS = new ThreadLocal<>();
    private static final Map<BlurCacheKey, BlurTarget> GAUSSIAN_BLUR_CACHE = new HashMap<>();
    private static BlurTarget gaussianBlurScratch;
    private static int gaussianBlurVao;

    private Render2DUtility() {
    }

    public static void withGuiGraphics(GuiGraphics graphics, Runnable action) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(action, "action");

        GuiGraphics previous = CURRENT_GRAPHICS.get();
        CURRENT_GRAPHICS.set(graphics);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT_GRAPHICS.remove();
            } else {
                CURRENT_GRAPHICS.set(previous);
            }
        }
    }

    public static void close() {
        closeGaussianBlurResources();
        Shaders.close();
    }

    public static GuiGraphics currentGuiGraphics() {
        return currentGraphics();
    }

    public static int rgb(int red, int green, int blue) {
        return rgba(red, green, blue, 255);
    }

    public static int rgba(int red, int green, int blue, int alpha) {
        return (clamp255(alpha) << 24)
            | (clamp255(red) << 16)
            | (clamp255(green) << 8)
            | clamp255(blue);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (clamp255(alpha) << 24);
    }

    public static int applyOpacity(int color, float opacity) {
        int alpha = (color >>> 24) & 0xFF;
        return withAlpha(color, Math.round(alpha * clamp01(opacity)));
    }

    public static int mix(int from, int to, float progress) {
        return lerpColor(from, to, clamp01(progress));
    }

    public static void drawTexture(GpuTextureView texture, float x, float y, float width, float height) {
        drawTexture(texture, x, y, width, height, 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF);
    }

    public static void drawTexture(GpuTextureView texture, float x, float y, float width, float height, int color) {
        drawTexture(texture, x, y, width, height, 0.0F, 0.0F, 1.0F, 1.0F, color);
    }

    public static void drawTexture(GpuTextureView texture, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, int color) {
        Objects.requireNonNull(texture, "texture");
        if (!canDraw(width, height, color)) {
            return;
        }

        GuiGraphics graphics = currentGraphics();
        graphics.submitGuiElementRenderState(new TextureRenderState(
            RenderPipelines.GUI_TEXTURED,
            TextureSetup.singleTexture(texture, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
            new Matrix3x2f(graphics.pose()),
            x,
            y,
            x + width,
            y + height,
            clamp01(u0),
            clamp01(v0),
            clamp01(u1),
            clamp01(v1),
            color,
            graphics.peekScissorStack()
        ));
    }

    public static void drawTextureCover(GpuTextureView texture, float sourceWidth, float sourceHeight,
                                        float x, float y, float width, float height) {
        drawTextureCover(texture, sourceWidth, sourceHeight, x, y, width, height, 0xFFFFFFFF);
    }

    public static void drawTextureCover(GpuTextureView texture, float sourceWidth, float sourceHeight,
                                        float x, float y, float width, float height, int color) {
        Objects.requireNonNull(texture, "texture");
        if (!canDraw(width, height, color)) {
            return;
        }

        if (sourceWidth <= 0.0F || sourceHeight <= 0.0F) {
            drawTexture(texture, x, y, width, height, color);
            return;
        }

        float sourceAspect = sourceWidth / sourceHeight;
        float targetAspect = width / height;
        float u0 = 0.0F;
        float v0 = 0.0F;
        float u1 = 1.0F;
        float v1 = 1.0F;
        if (sourceAspect > targetAspect) {
            float visibleWidth = targetAspect / sourceAspect;
            float crop = (1.0F - visibleWidth) * 0.5F;
            u0 = crop;
            u1 = 1.0F - crop;
        } else if (sourceAspect < targetAspect) {
            float visibleHeight = sourceAspect / targetAspect;
            float crop = (1.0F - visibleHeight) * 0.5F;
            v0 = crop;
            v1 = 1.0F - crop;
        }

        drawTexture(texture, x, y, width, height, u0, v0, u1, v1, color);
    }

    public static void drawRoundedTexture(GpuTextureView texture, float x, float y, float width, float height, float radius) {
        drawRoundedTexture(texture, x, y, width, height, radius, 0xFFFFFFFF);
    }

    public static void drawRoundedTexture(GpuTextureView texture, float x, float y, float width, float height, float radius, int color) {
        drawRoundedTexture(texture, x, y, width, height, radius, radius, radius, radius, 0.0F, 0.0F, 1.0F, 1.0F, color);
    }

    public static void drawRoundedTexture(GpuTextureView texture, float x, float y, float width, float height, float radius,
                                          float u0, float v0, float u1, float v1, int color) {
        drawRoundedTexture(texture, x, y, width, height, radius, radius, radius, radius, u0, v0, u1, v1, color);
    }

    public static void drawRoundedTexture(GpuTextureView texture, float x, float y, float width, float height,
                                          float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius,
                                          float u0, float v0, float u1, float v1, int color) {
        Objects.requireNonNull(texture, "texture");
        if (!canDraw(width, height, color)) {
            return;
        }

        float tl = clampRadius(width, height, topLeftRadius);
        float tr = clampRadius(width, height, topRightRadius);
        float br = clampRadius(width, height, bottomRightRadius);
        float bl = clampRadius(width, height, bottomLeftRadius);
        if (tl <= 0.0F && tr <= 0.0F && br <= 0.0F && bl <= 0.0F) {
            drawTexture(texture, x, y, width, height, u0, v0, u1, v1, color);
            return;
        }

        float[] points = roundedRectFanPoints(x, y, width, height, tl, tr, br, bl);
        submitTexturedFan(texture, points, textureUvsForPoints(points, x, y, width, height, u0, v0, u1, v1), color);
    }

    public static void drawGaussianBlur(float x, float y, float width, float height, float blurRadius) {
        drawGaussianBlurredRect(x, y, width, height, blurRadius, 0xFFFFFFFF);
    }

    public static void drawGaussianBlur(float x, float y, float width, float height, float blurRadius, int color) {
        drawGaussianBlurredRect(x, y, width, height, blurRadius, color);
    }

    public static void drawGaussianBlurredRect(float x, float y, float width, float height, float blurRadius) {
        drawGaussianBlurredRect(x, y, width, height, blurRadius, 0xFFFFFFFF);
    }

    public static void drawGaussianBlurredRect(float x, float y, float width, float height, float blurRadius, int color) {
        if (!canDraw(width, height, color) || blurRadius <= 0.0F) {
            return;
        }

        GpuTextureView blurred = gaussianBlurredMainTexture(blurRadius);
        if (blurred == null) {
            return;
        }

        TextureUv uv = mainTargetUv(x, y, width, height, blurred);
        drawTexture(blurred, x, y, width, height, uv.u0(), uv.v0(), uv.u1(), uv.v1(), color);
    }

    public static void drawRoundedGaussianBlur(float x, float y, float width, float height, float radius, float blurRadius) {
        drawRoundedGaussianBlurredRect(x, y, width, height, radius, blurRadius, 0xFFFFFFFF);
    }

    public static void drawRoundedGaussianBlur(float x, float y, float width, float height, float radius, float blurRadius, int color) {
        drawRoundedGaussianBlurredRect(x, y, width, height, radius, blurRadius, color);
    }

    public static void drawRoundedGaussianBlurredRect(float x, float y, float width, float height, float radius, float blurRadius) {
        drawRoundedGaussianBlurredRect(x, y, width, height, radius, blurRadius, 0xFFFFFFFF);
    }

    public static void drawRoundedGaussianBlurredRect(float x, float y, float width, float height, float radius, float blurRadius, int color) {
        drawRoundedGaussianBlurredRect(x, y, width, height, radius, radius, radius, radius, blurRadius, color);
    }

    public static void drawRoundedGaussianBlurredRect(float x, float y, float width, float height,
                                                     float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius,
                                                     float blurRadius, int color) {
        if (!canDraw(width, height, color) || blurRadius <= 0.0F) {
            return;
        }

        GpuTextureView blurred = gaussianBlurredMainTexture(blurRadius);
        if (blurred == null) {
            return;
        }

        TextureUv uv = mainTargetUv(x, y, width, height, blurred);
        drawRoundedTexture(blurred, x, y, width, height, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius,
            uv.u0(), uv.v0(), uv.u1(), uv.v1(), color);
    }

    public static void drawGaussianBlurredCircle(float centerX, float centerY, float radius, float blurRadius) {
        drawGaussianBlurredCircle(centerX, centerY, radius, blurRadius, 0xFFFFFFFF);
    }

    public static void drawGaussianBlurredCircle(float centerX, float centerY, float radius, float blurRadius, int color) {
        if (radius <= 0.0F) {
            return;
        }
        drawGaussianBlurredOval(centerX - radius, centerY - radius, radius * 2.0F, radius * 2.0F, blurRadius, color);
    }

    public static void drawGaussianBlurredOval(float x, float y, float width, float height, float blurRadius) {
        drawGaussianBlurredOval(x, y, width, height, blurRadius, 0xFFFFFFFF);
    }

    public static void drawGaussianBlurredOval(float x, float y, float width, float height, float blurRadius, int color) {
        if (!canDraw(width, height, color) || blurRadius <= 0.0F) {
            return;
        }

        GpuTextureView blurred = gaussianBlurredMainTexture(blurRadius);
        if (blurred == null) {
            return;
        }

        float[] points = ovalFanPoints(x, y, width, height);
        TextureUv uv = mainTargetUv(x, y, width, height, blurred);
        submitTexturedFan(blurred, points, textureUvsForPoints(points, x, y, width, height, uv.u0(), uv.v0(), uv.u1(), uv.v1()), color);
    }

    public static void drawGaussianBlurredTexture(GpuTextureView texture, float x, float y, float width, float height, float blurRadius) {
        drawGaussianBlurredTexture(texture, x, y, width, height, blurRadius, 0xFFFFFFFF);
    }

    public static void drawGaussianBlurredTexture(GpuTextureView texture, float x, float y, float width, float height, float blurRadius, int color) {
        drawGaussianBlurredTexture(texture, x, y, width, height, 0.0F, 0.0F, 1.0F, 1.0F, blurRadius, color);
    }

    public static void drawGaussianBlurredTexture(GpuTextureView texture, float x, float y, float width, float height,
                                                  float u0, float v0, float u1, float v1, float blurRadius, int color) {
        Objects.requireNonNull(texture, "texture");
        if (!canDraw(width, height, color)) {
            return;
        }

        GpuTextureView blurred = gaussianBlurredTexture(texture, blurRadius);
        if (blurred == null) {
            return;
        }
        drawTexture(blurred, x, y, width, height, u0, v0, u1, v1, color);
    }

    public static void drawRoundedGaussianBlurredTexture(GpuTextureView texture, float x, float y, float width, float height,
                                                        float radius, float blurRadius, int color) {
        drawRoundedGaussianBlurredTexture(texture, x, y, width, height, radius, radius, radius, radius, blurRadius, color);
    }

    public static void drawRoundedGaussianBlurredTexture(GpuTextureView texture, float x, float y, float width, float height,
                                                        float topLeftRadius, float topRightRadius,
                                                        float bottomRightRadius, float bottomLeftRadius,
                                                        float blurRadius, int color) {
        Objects.requireNonNull(texture, "texture");
        if (!canDraw(width, height, color)) {
            return;
        }

        GpuTextureView blurred = gaussianBlurredTexture(texture, blurRadius);
        if (blurred == null) {
            return;
        }
        drawRoundedTexture(blurred, x, y, width, height, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius,
            0.0F, 0.0F, 1.0F, 1.0F, color);
    }

    public static void drawRect(float x, float y, float width, float height, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        submitQuad(x, y, x + width, y + height, color, color, color, color);
    }

    public static void drawOutlineRect(float x, float y, float width, float height, float strokeWidth, int color) {
        if (!canStroke(width, height, strokeWidth, color)) {
            return;
        }

        float stroke = Math.min(strokeWidth, Math.min(width, height) * 0.5F);
        drawRect(x, y, width, stroke, color);
        drawRect(x, y + height - stroke, width, stroke, color);
        drawRect(x, y + stroke, stroke, Math.max(0.0F, height - stroke * 2.0F), color);
        drawRect(x + width - stroke, y + stroke, stroke, Math.max(0.0F, height - stroke * 2.0F), color);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        drawRoundedRect(x, y, width, height, radius, radius, radius, radius, color);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                       float bottomRightRadius, float bottomLeftRadius, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        float tl = clampRadius(width, height, topLeftRadius);
        float tr = clampRadius(width, height, topRightRadius);
        float br = clampRadius(width, height, bottomRightRadius);
        float bl = clampRadius(width, height, bottomLeftRadius);
        if (tl <= 0.0F && tr <= 0.0F && br <= 0.0F && bl <= 0.0F) {
            drawRect(x, y, width, height, color);
            return;
        }

        submitFan(roundedRectFanPoints(x, y, width, height, tl, tr, br, bl), color);
    }

    public static void drawOutlineRoundedRect(float x, float y, float width, float height, float radius, float strokeWidth, int color) {
        if (!canStroke(width, height, strokeWidth, color)) {
            return;
        }

        float safeRadius = clampRadius(width, height, radius);
        if (safeRadius <= 0.0F) {
            drawOutlineRect(x, y, width, height, strokeWidth, color);
            return;
        }

        float stroke = Math.min(strokeWidth, Math.min(width, height) * 0.5F);
        if (stroke >= Math.min(width, height) * 0.5F) {
            drawRoundedRect(x, y, width, height, safeRadius, color);
            return;
        }

        submitStrip(roundedRectStrokeStripPoints(x, y, width, height, safeRadius, stroke), color);
    }

    public static void drawCircle(float centerX, float centerY, float radius, int color) {
        if (radius <= 0.0F || isTransparent(color)) {
            return;
        }

        drawOval(centerX - radius, centerY - radius, radius * 2.0F, radius * 2.0F, color);
    }

    public static void drawOutlineCircle(float centerX, float centerY, float radius, float strokeWidth, int color) {
        if (radius <= 0.0F || strokeWidth <= 0.0F || isTransparent(color)) {
            return;
        }

        drawArc(centerX - radius, centerY - radius, radius * 2.0F, radius * 2.0F, 0.0F, 360.0F, strokeWidth, color);
    }

    public static void drawOval(float x, float y, float width, float height, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        float radiusX = width * 0.5F;
        float radiusY = height * 0.5F;
        int segments = segmentsForRadius(Math.max(radiusX, radiusY));
        FloatList points = new FloatList((segments + 3) * 2);
        points.add(centerX, centerY);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            points.add(centerX + (float)Math.cos(angle) * radiusX, centerY + (float)Math.sin(angle) * radiusY);
        }
        submitFan(points.toArray(), color);
    }

    public static void drawLine(float startX, float startY, float endX, float endY, float strokeWidth, int color) {
        if (strokeWidth <= 0.0F || isTransparent(color)) {
            return;
        }

        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001F) {
            drawCircle(startX, startY, strokeWidth * 0.5F, color);
            return;
        }

        float half = strokeWidth * 0.5F;
        float normalX = -dy / length * half;
        float normalY = dx / length * half;
        submitFan(new float[] {
            startX + normalX, startY + normalY,
            endX + normalX, endY + normalY,
            endX - normalX, endY - normalY,
            startX - normalX, startY - normalY
        }, color);
    }

    public static void drawArc(float x, float y, float width, float height, float startAngle, float sweepAngle, float strokeWidth, int color) {
        if (!canStroke(width, height, strokeWidth, color) || sweepAngle == 0.0F) {
            return;
        }

        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        float radiusX = width * 0.5F;
        float radiusY = height * 0.5F;
        int segments = Math.max(2, (int)Math.ceil(Math.abs(sweepAngle) / 360.0F * segmentsForRadius(Math.max(radiusX, radiusY))));
        float halfStroke = Math.min(strokeWidth, Math.min(width, height)) * 0.5F;
        FloatList points = new FloatList((segments + 1) * 4);
        for (int i = 0; i <= segments; i++) {
            float angle = startAngle + sweepAngle * i / segments;
            double radians = Math.toRadians(angle);
            float cos = (float)Math.cos(radians);
            float sin = (float)Math.sin(radians);
            points.add(centerX + cos * (radiusX + halfStroke), centerY + sin * (radiusY + halfStroke));
            points.add(centerX + cos * Math.max(0.0F, radiusX - halfStroke), centerY + sin * Math.max(0.0F, radiusY - halfStroke));
        }
        submitStrip(points.toArray(), color);
    }

    public static void drawVerticalGradientRect(float x, float y, float width, float height, int topColor, int bottomColor) {
        if (!canDrawGradient(width, height, topColor, bottomColor)) {
            return;
        }

        submitQuad(x, y, x + width, y + height, topColor, topColor, bottomColor, bottomColor);
    }

    public static void drawHorizontalGradientRect(float x, float y, float width, float height, int leftColor, int rightColor) {
        if (!canDrawGradient(width, height, leftColor, rightColor)) {
            return;
        }

        submitQuad(x, y, x + width, y + height, leftColor, rightColor, rightColor, leftColor);
    }

    public static void drawLinearGradientRect(float x, float y, float width, float height, float startX, float startY,
                                              float endX, float endY, int[] colors, float[] positions) {
        if (!canDrawGradient(width, height, colors, positions)) {
            return;
        }

        Gradient gradient = Gradient.of(colors, positions);
        submitQuad(
            x,
            y,
            x + width,
            y + height,
            gradient.colorAt(projectGradient(x, y, startX, startY, endX, endY)),
            gradient.colorAt(projectGradient(x + width, y, startX, startY, endX, endY)),
            gradient.colorAt(projectGradient(x + width, y + height, startX, startY, endX, endY)),
            gradient.colorAt(projectGradient(x, y + height, startX, startY, endX, endY))
        );
    }

    public static void drawRoundedVerticalGradientRect(float x, float y, float width, float height, float radius, int topColor, int bottomColor) {
        drawRoundedLinearGradientRect(x, y, width, height, radius, x, y, x, y + height, new int[] {topColor, bottomColor}, null);
    }

    public static void drawRoundedHorizontalGradientRect(float x, float y, float width, float height, float radius, int leftColor, int rightColor) {
        drawRoundedLinearGradientRect(x, y, width, height, radius, x, y, x + width, y, new int[] {leftColor, rightColor}, null);
    }

    public static void drawRoundedLinearGradientRect(float x, float y, float width, float height, float radius, float startX, float startY,
                                                     float endX, float endY, int[] colors, float[] positions) {
        if (!canDrawGradient(width, height, colors, positions)) {
            return;
        }

        float safeRadius = clampRadius(width, height, radius);
        if (safeRadius <= 0.0F) {
            drawLinearGradientRect(x, y, width, height, startX, startY, endX, endY, colors, positions);
            return;
        }

        Gradient gradient = Gradient.of(colors, positions);
        float[] points = roundedRectFanPoints(x, y, width, height, safeRadius, safeRadius, safeRadius, safeRadius);
        int[] pointColors = new int[points.length / 2];
        for (int i = 0; i < pointColors.length; i++) {
            pointColors[i] = gradient.colorAt(projectGradient(points[i * 2], points[i * 2 + 1], startX, startY, endX, endY));
        }
        submitFan(points, pointColors);
    }

    public static void drawRadialGradientCircle(float centerX, float centerY, float radius, int innerColor, int outerColor) {
        drawRadialGradientCircle(centerX, centerY, radius, new int[] {innerColor, outerColor}, null);
    }

    public static void drawRadialGradientCircle(float centerX, float centerY, float radius, int[] colors, float[] positions) {
        if (radius <= 0.0F || !canDrawGradient(radius, radius, colors, positions)) {
            return;
        }

        Gradient gradient = Gradient.of(colors, positions);
        int segments = segmentsForRadius(radius);
        FloatList points = new FloatList((segments + 3) * 2);
        points.add(centerX, centerY);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            points.add(centerX + (float)Math.cos(angle) * radius, centerY + (float)Math.sin(angle) * radius);
        }

        int[] pointColors = new int[points.size() / 2];
        pointColors[0] = gradient.colorAt(0.0F);
        Arrays.fill(pointColors, 1, pointColors.length, gradient.colorAt(1.0F));
        submitFan(points.toArray(), pointColors);
    }

    public static void drawDropShadow(float x, float y, float width, float height, float radius, float offsetX, float offsetY,
                                      float blurRadius, int color) {
        if (!canDraw(width, height, color) || blurRadius <= 0.0F) {
            return;
        }

        int steps = Math.max(2, Math.min(10, Math.round(blurRadius / 2.0F)));
        for (int i = steps; i >= 1; i--) {
            float progress = i / (float)steps;
            float spread = blurRadius * progress;
            int layerColor = applyOpacity(color, (1.0F - progress * 0.85F) / steps);
            drawRoundedRect(
                x + offsetX - spread,
                y + offsetY - spread,
                width + spread * 2.0F,
                height + spread * 2.0F,
                radius + spread,
                layerColor
            );
        }
    }

    public static void drawBlurredRect(float x, float y, float width, float height, float radius, float blurRadius, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        if (blurRadius > 0.0F) {
            drawDropShadow(x, y, width, height, radius, 0.0F, 0.0F, blurRadius, color);
        }
        drawRoundedRect(x, y, width, height, radius, color);
    }

    public static void drawGaussianBlurredPanel(float x, float y, float width, float height, float radius, float blurRadius,
                                                int fillColor, float borderWidth, int borderColor) {
        drawGaussianBlurredPanel(x, y, width, height, radius, blurRadius, 0xFFFFFFFF, fillColor, borderWidth, borderColor);
    }

    public static void drawGaussianBlurredPanel(float x, float y, float width, float height, float radius, float blurRadius,
                                                int blurColor, int fillColor, float borderWidth, int borderColor) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }

        if (blurRadius > 0.0F && !isTransparent(blurColor)) {
            drawRoundedGaussianBlurredRect(x, y, width, height, radius, blurRadius, blurColor);
        }
        if (!isTransparent(fillColor)) {
            drawRoundedRect(x, y, width, height, radius, fillColor);
        }
        if (borderWidth > 0.0F && !isTransparent(borderColor)) {
            drawOutlineRoundedRect(x, y, width, height, radius, borderWidth, borderColor);
        }
    }

    public static void drawPill(float x, float y, float width, float height, int fillColor, int borderColor) {
        drawRoundedRect(x, y, width, height, height * 0.5F, fillColor);
        if (!isTransparent(borderColor)) {
            drawOutlineRoundedRect(x, y, width, height, height * 0.5F, 1.0F, borderColor);
        }
    }

    public static void drawSoftCard(float x, float y, float width, float height, float radius, int fillColor, int borderColor) {
        drawRoundedRect(x, y, width, height, radius, fillColor);
        if (!isTransparent(borderColor)) {
            drawOutlineRoundedRect(x, y, width, height, radius, 1.0F, borderColor);
        }
    }

    public static void drawToggleSwitch(float x, float y, float width, float height, boolean enabled, int enabledColor, int disabledColor, int knobColor) {
        drawPill(x, y, width, height, enabled ? enabledColor : disabledColor, 0);

        float padding = Math.max(2.0F, height * 0.125F);
        float knobRadius = Math.max(1.0F, (height - padding * 2.0F) * 0.5F);
        float knobX = enabled ? x + width - padding - knobRadius : x + padding + knobRadius;
        float knobY = y + height * 0.5F;
        drawCircle(knobX, knobY, knobRadius, knobColor);
    }

    public static void drawText(String text, float x, float y, float size, int color) {
        if (!canDrawText(text, size, color)) {
            return;
        }

        Font font = minecraftFont();
        if (font != null) {
            drawText(font, text, x, y, size, color);
        }
    }

    public static void drawText(Font font, String text, float x, float y, int color) {
        Objects.requireNonNull(font, "font");
        drawText(font, text, x, y, font.lineHeight, color);
    }

    public static void drawText(Font font, String text, float x, float y, float size, int color) {
        Objects.requireNonNull(font, "font");
        if (!canDrawText(text, size, color)) {
            return;
        }

        GuiGraphics graphics = currentGraphics();
        float scale = size / Math.max(1.0F, font.lineHeight);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);

            String[] lines = text.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].isEmpty()) {
                    graphics.drawString(font, lines[i], 0, Math.round(i * font.lineHeight), color, false);
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void drawTextBaseline(String text, float x, float baselineY, float size, int color) {
        drawText(text, x, baselineY - size * 0.8F, size, color);
    }

    public static void drawTextBaseline(Font font, String text, float x, float baselineY, int color) {
        Objects.requireNonNull(font, "font");
        drawText(font, text, x, baselineY - font.lineHeight * 0.8F, color);
    }

    public static void drawCenteredText(String text, float centerX, float y, float size, int color) {
        if (!canDrawText(text, size, color)) {
            return;
        }

        drawText(text, centerX - getTextWidth(text, size) * 0.5F, y, size, color);
    }

    public static void drawCenteredTextInRect(String text, float x, float y, float width, float height, float size, int color) {
        if (width <= 0.0F || height <= 0.0F || !canDrawText(text, size, color)) {
            return;
        }

        float textX = x + (width - getTextWidth(text, size)) * 0.5F;
        float textY = y + (height - getTextHeight(size)) * 0.5F;
        drawText(text, textX, textY, size, color);
    }

    public static float getTextWidth(String text, float size) {
        if (text == null || text.isEmpty() || size <= 0.0F) {
            return 0.0F;
        }

        Font font = minecraftFont();
        if (font == null) {
            return text.length() * size * 0.5F;
        }

        float maxWidth = 0.0F;
        for (String line : text.split("\\R", -1)) {
            maxWidth = Math.max(maxWidth, font.width(line) * (size / Math.max(1.0F, font.lineHeight)));
        }
        return maxWidth;
    }

    public static float getTextHeight(float size) {
        return Math.max(0.0F, size);
    }

    public static void withClip(float x, float y, float width, float height, Runnable action) {
        Objects.requireNonNull(action, "action");
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }

        GuiGraphics graphics = currentGraphics();
        graphics.enableScissor(floor(x), floor(y), ceil(x + width), ceil(y + height));
        try {
            action.run();
        } finally {
            graphics.disableScissor();
        }
    }

    public static void withRoundedClip(float x, float y, float width, float height, float radius, Runnable action) {
        withClip(x, y, width, height, action);
    }

    public static void withTranslation(float x, float y, Runnable action) {
        Objects.requireNonNull(action, "action");
        GuiGraphics graphics = currentGraphics();
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            action.run();
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void withScale(float scaleX, float scaleY, float pivotX, float pivotY, Runnable action) {
        Objects.requireNonNull(action, "action");
        GuiGraphics graphics = currentGraphics();
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(pivotX, pivotY);
            graphics.pose().scale(scaleX, scaleY);
            graphics.pose().translate(-pivotX, -pivotY);
            action.run();
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void withVerticalFlip(float degrees, float pivotX, float pivotY, float minScale, Runnable action) {
        withScale(Math.max(Math.max(0.0F, minScale), verticalFlipScale(degrees)), 1.0F, pivotX, pivotY, action);
    }

    public static float verticalFlipScale(float degrees) {
        return Math.abs((float)Math.cos(Math.toRadians(degrees)));
    }

    public static boolean isVerticalFlipBackFace(float degrees) {
        float normalized = degrees % 360.0F;
        if (normalized < 0.0F) {
            normalized += 360.0F;
        }
        return normalized > 90.0F && normalized < 270.0F;
    }

    public static void withRotation(float degrees, float pivotX, float pivotY, Runnable action) {
        Objects.requireNonNull(action, "action");
        GuiGraphics graphics = currentGraphics();
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(pivotX, pivotY);
            graphics.pose().rotate((float)Math.toRadians(degrees));
            graphics.pose().translate(-pivotX, -pivotY);
            action.run();
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void submitQuad(float x0, float y0, float x1, float y1, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        GuiGraphics graphics = currentGraphics();
        graphics.submitGuiElementRenderState(new QuadRenderState(
            RenderPipelines.GUI,
            TextureSetup.noTexture(),
            new Matrix3x2f(graphics.pose()),
            x0,
            y0,
            x1,
            y1,
            topLeft,
            topRight,
            bottomRight,
            bottomLeft,
            graphics.peekScissorStack()
        ));
    }

    private static void submitFan(float[] points, int color) {
        submitFan(points, new int[] {color});
    }

    private static void submitFan(float[] points, int[] colors) {
        if (points.length < 6) {
            return;
        }

        GuiGraphics graphics = currentGraphics();
        graphics.submitGuiElementRenderState(new FanRenderState(
            RenderPipelines.DEBUG_TRIANGLE_FAN,
            TextureSetup.noTexture(),
            new Matrix3x2f(graphics.pose()),
            points,
            colors,
            graphics.peekScissorStack()
        ));
    }

    private static void submitStrip(float[] points, int color) {
        if (points.length < 6 || isTransparent(color)) {
            return;
        }

        GuiGraphics graphics = currentGraphics();
        graphics.submitGuiElementRenderState(new StripRenderState(
            GUI_TRIANGLE_STRIP,
            TextureSetup.noTexture(),
            new Matrix3x2f(graphics.pose()),
            points,
            color,
            graphics.peekScissorStack()
        ));
    }

    private static void submitTexturedFan(GpuTextureView texture, float[] points, float[] uvs, int color) {
        if (points.length < 6 || points.length != uvs.length || isTransparent(color)) {
            return;
        }

        GuiGraphics graphics = currentGraphics();
        graphics.submitGuiElementRenderState(new TexturedFanRenderState(
            GUI_TEXTURED_TRIANGLE_FAN,
            TextureSetup.singleTexture(texture, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
            new Matrix3x2f(graphics.pose()),
            points,
            uvs,
            color,
            graphics.peekScissorStack()
        ));
    }

    @Nullable
    private static GpuTextureView gaussianBlurredMainTexture(float blurRadius) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }

        RenderTarget target = minecraft.getMainRenderTarget();
        if (target == null || target.getColorTextureView() == null) {
            return null;
        }
        return gaussianBlurredTexture(target.getColorTextureView(), blurRadius);
    }

    @Nullable
    private static GpuTextureView gaussianBlurredTexture(GpuTextureView source, float blurRadius) {
        Objects.requireNonNull(source, "source");
        if (source.isClosed() || source.texture() == null || source.texture().isClosed()) {
            return null;
        }

        if (blurRadius <= 0.0F) {
            return source;
        }

        int sourceId = glTextureId(source);
        if (sourceId == 0) {
            return source;
        }

        int width = source.getWidth(0);
        int height = source.getHeight(0);
        if (width <= 0 || height <= 0) {
            return null;
        }

        int previousFramebuffer = GlStateManager.getFrameBuffer(GL32C.GL_FRAMEBUFFER);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int previousTexture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        float[] previousClearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GpuTextureView result = source;

        try {
            float safeRadius = Math.min(MAX_GAUSSIAN_BLUR_RADIUS, Math.max(0.0F, blurRadius));
            BlurCacheKey key = new BlurCacheKey(sourceId, width, height, Math.round(safeRadius * 100.0F));
            BlurTarget output = GAUSSIAN_BLUR_CACHE.computeIfAbsent(key, ignored -> new BlurTarget("nyx-gaussian-blur-output"));
            ensureGaussianBlurTarget(output, width, height);
            ensureGaussianBlurScratch(width, height);

            Shader shader = gaussianBlurShader();
            if (shader == null || gaussianBlurScratch == null || gaussianBlurScratch.view == null) {
                return source;
            }

            if (gaussianBlurVao == 0) {
                gaussianBlurVao = GL.genVertexArray();
            }
            GL.bindVertexArray(gaussianBlurVao);
            GL.disableDepth();
            GL.disableBlend();
            GL.disableCull();
            GL.disableScissorTest();

            renderGaussianBlurPass(shader, sourceId, gaussianBlurScratch, width, height, safeRadius, 1.0F, 0.0F);
            renderGaussianBlurPass(shader, glTextureId(gaussianBlurScratch.view), output, width, height, safeRadius, 0.0F, 1.0F);
            result = output.view;
        } finally {
            GL.bindFramebuffer(previousFramebuffer);
            GL.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
            GL.bindVertexArray(previousVertexArray);
            GL.useProgram(previousProgram);
            GL.bindTexture(previousTexture0, 0);
            GlStateManager._activeTexture(previousActiveTexture);
            GL11.glClearColor(previousClearColor[0], previousClearColor[1], previousClearColor[2], previousClearColor[3]);
            setCapability(GL11.GL_DEPTH_TEST, previousDepthTest);
            setCapability(GL11.GL_BLEND, previousBlend);
            setCapability(GL11.GL_CULL_FACE, previousCull);
            setCapability(GL11.GL_SCISSOR_TEST, previousScissor);
        }

        return result;
    }

    @Nullable
    private static Shader gaussianBlurShader() {
        Shaders.init();
        return Shaders.GAUSSIAN_BLUR;
    }

    private static void renderGaussianBlurPass(Shader shader, int inputTexture, BlurTarget output, int width, int height,
                                               float radius, float directionX, float directionY) {
        GL.bindFramebuffer(output.framebuffer);
        GL.viewport(0, 0, width, height);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        shader.bind();
        shader.set("InputTexture", 0);
        shader.set("TexelSize", 1.0F / width, 1.0F / height);
        shader.set("Direction", directionX, directionY);
        shader.set("Radius", radius);
        shader.set("Sigma", Math.max(0.5F, radius * 0.5F));

        configureTextureForBlur(inputTexture);
        GL32C.glDrawArrays(GL32C.GL_TRIANGLES, 0, 3);
    }

    private static void configureTextureForBlur(int textureId) {
        GL.bindTexture(textureId, 0);
        GL.textureParam(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL.textureParam(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL.textureParam(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL.textureParam(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static void ensureGaussianBlurScratch(int width, int height) {
        if (gaussianBlurScratch == null) {
            gaussianBlurScratch = new BlurTarget("nyx-gaussian-blur-scratch");
        }
        ensureGaussianBlurTarget(gaussianBlurScratch, width, height);
    }

    private static void ensureGaussianBlurTarget(BlurTarget target, int width, int height) {
        if (target.matches(width, height)) {
            return;
        }
        target.resize(width, height);
    }

    private static void closeGaussianBlurResources() {
        if (gaussianBlurScratch != null) {
            gaussianBlurScratch.close();
            gaussianBlurScratch = null;
        }

        for (BlurTarget target : GAUSSIAN_BLUR_CACHE.values()) {
            target.close();
        }
        GAUSSIAN_BLUR_CACHE.clear();

        if (gaussianBlurVao != 0) {
            GL.deleteVertexArray(gaussianBlurVao);
            gaussianBlurVao = 0;
        }
    }

    private static int glTextureId(GpuTextureView texture) {
        if (texture == null || !(texture.texture() instanceof GlTexture glTexture)) {
            return 0;
        }
        return glTexture.glId();
    }

    private static GuiGraphics currentGraphics() {
        GuiGraphics graphics = CURRENT_GRAPHICS.get();
        if (graphics == null) {
            throw new IllegalStateException("Render2DUtility drawing must run inside Render2DUtility.withGuiGraphics(guiGraphics, action)");
        }
        return graphics;
    }

    private static Font minecraftFont() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.font;
    }

    private static float[] roundedRectFanPoints(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                                float bottomRightRadius, float bottomLeftRadius) {
        float[] boundary = roundedRectBoundaryPoints(x, y, width, height, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
        FloatList points = new FloatList(boundary.length + 2);
        points.add(x + width * 0.5F, y + height * 0.5F);
        points.addAll(boundary);
        return points.toArray();
    }

    private static float[] ovalFanPoints(float x, float y, float width, float height) {
        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        float radiusX = width * 0.5F;
        float radiusY = height * 0.5F;
        int segments = segmentsForRadius(Math.max(radiusX, radiusY));
        FloatList points = new FloatList((segments + 3) * 2);
        points.add(centerX, centerY);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            points.add(centerX + (float)Math.cos(angle) * radiusX, centerY + (float)Math.sin(angle) * radiusY);
        }
        return points.toArray();
    }

    private static float[] textureUvsForPoints(float[] points, float x, float y, float width, float height,
                                               float u0, float v0, float u1, float v1) {
        float[] uvs = new float[points.length];
        float safeWidth = Math.max(0.0001F, width);
        float safeHeight = Math.max(0.0001F, height);
        for (int i = 0; i < points.length; i += 2) {
            float localX = clamp01((points[i] - x) / safeWidth);
            float localY = clamp01((points[i + 1] - y) / safeHeight);
            uvs[i] = lerp(u0, u1, localX);
            uvs[i + 1] = lerp(v0, v1, localY);
        }
        return uvs;
    }

    private static TextureUv mainTargetUv(float x, float y, float width, float height, GpuTextureView texture) {
        Minecraft minecraft = Minecraft.getInstance();
        float scale = minecraft == null ? 1.0F : Math.max(1, minecraft.getWindow().getGuiScale());
        float textureWidth = Math.max(1.0F, texture.getWidth(0));
        float textureHeight = Math.max(1.0F, texture.getHeight(0));
        float u0 = clamp01(x * scale / textureWidth);
        float u1 = clamp01((x + width) * scale / textureWidth);
        float v0 = clamp01(1.0F - y * scale / textureHeight);
        float v1 = clamp01(1.0F - (y + height) * scale / textureHeight);
        return new TextureUv(u0, v0, u1, v1);
    }

    private static float[] roundedRectBoundaryPoints(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                                     float bottomRightRadius, float bottomLeftRadius) {
        FloatList points = new FloatList(96);
        float firstX = bottomRightRadius <= 0.0F ? x + width : x + width - bottomRightRadius;
        float firstY = y + height;
        points.add(firstX, firstY);
        appendCorner(points, x + width - bottomRightRadius, y + height - bottomRightRadius, bottomRightRadius, 0.0F, 90.0F, false, x + width, y + height);
        appendCorner(points, x + width - topRightRadius, y + topRightRadius, topRightRadius, 90.0F, 180.0F, true, x + width, y);
        appendCorner(points, x + topLeftRadius, y + topLeftRadius, topLeftRadius, 180.0F, 270.0F, true, x, y);
        appendCorner(points, x + bottomLeftRadius, y + height - bottomLeftRadius, bottomLeftRadius, 270.0F, 360.0F, true, x, y + height);
        points.add(firstX, firstY);
        return points.toArray();
    }

    private static float[] roundedRectStrokeStripPoints(float x, float y, float width, float height, float radius, float strokeWidth) {
        float innerX = x + strokeWidth;
        float innerY = y + strokeWidth;
        float innerWidth = Math.max(0.0F, width - strokeWidth * 2.0F);
        float innerHeight = Math.max(0.0F, height - strokeWidth * 2.0F);
        float innerRadius = Math.max(0.0F, radius - strokeWidth);
        FloatList points = new FloatList(128);
        float firstOuterX = x + width - radius;
        float firstOuterY = y + height;
        float firstInnerX = innerX + innerWidth - innerRadius;
        float firstInnerY = innerY + innerHeight;
        points.add(firstOuterX, firstOuterY);
        points.add(firstInnerX, firstInnerY);
        appendStrokeCorner(points, x + width - radius, y + height - radius,
            innerX + innerWidth - innerRadius, innerY + innerHeight - innerRadius,
            radius, innerRadius, 0.0F, 90.0F, false);
        appendStrokeCorner(points, x + width - radius, y + radius,
            innerX + innerWidth - innerRadius, innerY + innerRadius,
            radius, innerRadius, 90.0F, 180.0F, true);
        appendStrokeCorner(points, x + radius, y + radius,
            innerX + innerRadius, innerY + innerRadius,
            radius, innerRadius, 180.0F, 270.0F, true);
        appendStrokeCorner(points, x + radius, y + height - radius,
            innerX + innerRadius, innerY + innerHeight - innerRadius,
            radius, innerRadius, 270.0F, 360.0F, true);
        points.add(firstOuterX, firstOuterY);
        points.add(firstInnerX, firstInnerY);
        return points.toArray();
    }

    private static void appendCorner(FloatList points, float centerX, float centerY, float radius, float startAngle, float endAngle,
                                     boolean includeStart, float fallbackX, float fallbackY) {
        if (radius <= 0.0F) {
            if (includeStart) {
                points.add(fallbackX, fallbackY);
            }
            return;
        }

        int segments = Math.max(3, segmentsForRadius(radius) / 4);
        int startIndex = includeStart ? 0 : 1;
        for (int i = startIndex; i <= segments; i++) {
            float angle = startAngle + (endAngle - startAngle) * i / segments;
            double radians = Math.toRadians(angle);
            points.add(centerX + (float)Math.sin(radians) * radius, centerY + (float)Math.cos(radians) * radius);
        }
    }

    private static void appendStrokeCorner(FloatList points, float outerCenterX, float outerCenterY, float innerCenterX, float innerCenterY,
                                           float outerRadius, float innerRadius, float startAngle, float endAngle, boolean includeStart) {
        int segments = Math.max(3, segmentsForRadius(outerRadius) / 4);
        int startIndex = includeStart ? 0 : 1;
        for (int i = startIndex; i <= segments; i++) {
            float angle = startAngle + (endAngle - startAngle) * i / segments;
            double radians = Math.toRadians(angle);
            float sin = (float)Math.sin(radians);
            float cos = (float)Math.cos(radians);
            points.add(outerCenterX + sin * outerRadius, outerCenterY + cos * outerRadius);
            points.add(innerCenterX + sin * innerRadius, innerCenterY + cos * innerRadius);
        }
    }

    private static float projectGradient(float x, float y, float startX, float startY, float endX, float endY) {
        float dx = endX - startX;
        float dy = endY - startY;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0001F) {
            return 0.0F;
        }
        return clamp01(((x - startX) * dx + (y - startY) * dy) / lengthSquared);
    }

    private static boolean canDraw(float width, float height, int color) {
        return width > 0.0F && height > 0.0F && !isTransparent(color);
    }

    private static boolean canStroke(float width, float height, float strokeWidth, int color) {
        return canDraw(width, height, color) && strokeWidth > 0.0F;
    }

    private static boolean canDrawGradient(float width, float height, int startColor, int endColor) {
        return width > 0.0F && height > 0.0F && (!isTransparent(startColor) || !isTransparent(endColor));
    }

    private static boolean canDrawGradient(float width, float height, int[] colors, float[] positions) {
        Objects.requireNonNull(colors, "colors");
        if (positions != null && positions.length != colors.length) {
            throw new IllegalArgumentException("Gradient positions length must match colors length");
        }

        if (width <= 0.0F || height <= 0.0F || colors.length == 0) {
            return false;
        }

        for (int color : colors) {
            if (!isTransparent(color)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canDrawText(String text, float size, int color) {
        return text != null && !text.isEmpty() && size > 0.0F && !isTransparent(color);
    }

    private static boolean isTransparent(int color) {
        return ((color >>> 24) & 0xFF) == 0;
    }

    private static float clampRadius(float width, float height, float radius) {
        return Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static int segmentsForRadius(float radius) {
        return Math.max(MIN_SEGMENTS, Math.min(MAX_SEGMENTS, (int)Math.ceil(Math.max(1.0F, radius) * 0.75F)));
    }

    private static int floor(float value) {
        return (int)Math.floor(value);
    }

    private static int ceil(float value) {
        return (int)Math.ceil(value);
    }

    private static void setCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private static int lerpColor(int from, int to, float progress) {
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * progress);
        int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * progress);
        int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * progress);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * progress);
        return rgba(r, g, b, a);
    }

    @Nullable
    private static ScreenRectangle boundsFor(float[] points, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        if (points.length < 2) {
            return null;
        }

        float minX = points[0];
        float maxX = points[0];
        float minY = points[1];
        float maxY = points[1];
        for (int i = 2; i < points.length; i += 2) {
            minX = Math.min(minX, points[i]);
            maxX = Math.max(maxX, points[i]);
            minY = Math.min(minY, points[i + 1]);
            maxY = Math.max(maxY, points[i + 1]);
        }

        ScreenRectangle bounds = new ScreenRectangle(floor(minX), floor(minY), Math.max(1, ceil(maxX - minX)), Math.max(1, ceil(maxY - minY)))
            .transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
    }

    @Nullable
    private static ScreenRectangle boundsFor(float x0, float y0, float x1, float y1, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        float minX = Math.min(x0, x1);
        float maxX = Math.max(x0, x1);
        float minY = Math.min(y0, y1);
        float maxY = Math.max(y0, y1);
        ScreenRectangle bounds = new ScreenRectangle(floor(minX), floor(minY), Math.max(1, ceil(maxX - minX)), Math.max(1, ceil(maxY - minY)))
            .transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
    }

    private record QuadRenderState(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2f pose,
        float x0,
        float y0,
        float x1,
        float y1,
        int topLeft,
        int topRight,
        int bottomRight,
        int bottomLeft,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {
        private QuadRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float x0, float y0, float x1, float y1,
                                int topLeft, int topRight, int bottomRight, int bottomLeft, @Nullable ScreenRectangle scissorArea) {
            this(
                pipeline,
                textureSetup,
                pose,
                x0,
                y0,
                x1,
                y1,
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                scissorArea,
                boundsFor(x0, y0, x1, y1, pose, scissorArea)
            );
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            consumer.addVertexWith2DPose(this.pose, this.x0, this.y0).setColor(this.topLeft);
            consumer.addVertexWith2DPose(this.pose, this.x0, this.y1).setColor(this.bottomLeft);
            consumer.addVertexWith2DPose(this.pose, this.x1, this.y1).setColor(this.bottomRight);
            consumer.addVertexWith2DPose(this.pose, this.x1, this.y0).setColor(this.topRight);
        }
    }

    private record TextureRenderState(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2f pose,
        float x0,
        float y0,
        float x1,
        float y1,
        float u0,
        float v0,
        float u1,
        float v1,
        int color,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {
        private TextureRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float x0, float y0, float x1, float y1,
                                   float u0, float v0, float u1, float v1, int color, @Nullable ScreenRectangle scissorArea) {
            this(pipeline, textureSetup, pose, x0, y0, x1, y1, u0, v0, u1, v1, color, scissorArea, boundsFor(x0, y0, x1, y1, pose, scissorArea));
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            consumer.addVertexWith2DPose(this.pose, this.x0, this.y0).setUv(this.u0, this.v0).setColor(this.color);
            consumer.addVertexWith2DPose(this.pose, this.x0, this.y1).setUv(this.u0, this.v1).setColor(this.color);
            consumer.addVertexWith2DPose(this.pose, this.x1, this.y1).setUv(this.u1, this.v1).setColor(this.color);
            consumer.addVertexWith2DPose(this.pose, this.x1, this.y0).setUv(this.u1, this.v0).setColor(this.color);
        }
    }

    private record FanRenderState(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2f pose,
        float[] points,
        int[] colors,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {
        private FanRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float[] points, int[] colors,
                               @Nullable ScreenRectangle scissorArea) {
            this(pipeline, textureSetup, pose, points, colors, scissorArea, boundsFor(points, pose, scissorArea));
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < this.points.length; i += 2) {
                consumer.addVertexWith2DPose(this.pose, this.points[i], this.points[i + 1]).setColor(colorAt(i / 2));
            }
        }

        private int colorAt(int index) {
            if (this.colors.length == 1) {
                return this.colors[0];
            }
            return this.colors[Math.min(index, this.colors.length - 1)];
        }
    }

    private record StripRenderState(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2f pose,
        float[] points,
        int color,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {
        private StripRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float[] points, int color,
                                 @Nullable ScreenRectangle scissorArea) {
            this(pipeline, textureSetup, pose, points, color, scissorArea, boundsFor(points, pose, scissorArea));
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < this.points.length; i += 2) {
                consumer.addVertexWith2DPose(this.pose, this.points[i], this.points[i + 1]).setColor(this.color);
            }
        }
    }

    private record TexturedFanRenderState(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2f pose,
        float[] points,
        float[] uvs,
        int color,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {
        private TexturedFanRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float[] points, float[] uvs,
                                       int color, @Nullable ScreenRectangle scissorArea) {
            this(pipeline, textureSetup, pose, points, uvs, color, scissorArea, boundsFor(points, pose, scissorArea));
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < this.points.length; i += 2) {
                consumer.addVertexWith2DPose(this.pose, this.points[i], this.points[i + 1]).setUv(this.uvs[i], this.uvs[i + 1]).setColor(this.color);
            }
        }
    }

    private record TextureUv(float u0, float v0, float u1, float v1) {
    }

    private record BlurCacheKey(int sourceTexture, int width, int height, int radius) {
    }

    private static final class BlurTarget implements AutoCloseable {
        private final String label;
        private int width;
        private int height;
        private int framebuffer;
        @Nullable
        private GpuTexture texture;
        @Nullable
        private GpuTextureView view;

        private BlurTarget(String label) {
            this.label = label;
        }

        private boolean matches(int width, int height) {
            return this.width == width && this.height == height && this.texture != null && !this.texture.isClosed()
                && this.view != null && !this.view.isClosed() && this.framebuffer != 0;
        }

        private void resize(int width, int height) {
            closeTexture();

            if (this.framebuffer == 0) {
                this.framebuffer = GL.genFramebuffer();
            }

            int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
            GpuTexture nextTexture = RenderSystem.getDevice().createTexture(this.label, usage, TextureFormat.RGBA8, width, height, 1, 1);
            GpuTextureView nextView = RenderSystem.getDevice().createTextureView(nextTexture);
            int previousFramebuffer = GlStateManager.getFrameBuffer(GL32C.GL_FRAMEBUFFER);
            GL.bindFramebuffer(this.framebuffer);
            GL.framebufferTexture2D(GL32C.GL_FRAMEBUFFER, GL32C.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glTextureId(nextView), 0);
            int status = GL32C.glCheckFramebufferStatus(GL32C.GL_FRAMEBUFFER);
            GL.bindFramebuffer(previousFramebuffer);
            if (status != GL32C.GL_FRAMEBUFFER_COMPLETE) {
                nextView.close();
                nextTexture.close();
                throw new IllegalStateException("Gaussian blur framebuffer is incomplete: " + status);
            }

            configureTextureForBlur(glTextureId(nextView));
            this.texture = nextTexture;
            this.view = nextView;
            this.width = width;
            this.height = height;
        }

        private void closeTexture() {
            if (this.view != null) {
                this.view.close();
                this.view = null;
            }
            if (this.texture != null) {
                this.texture.close();
                this.texture = null;
            }
            this.width = 0;
            this.height = 0;
        }

        @Override
        public void close() {
            closeTexture();
            if (this.framebuffer != 0) {
                GL.deleteFramebuffer(this.framebuffer);
                this.framebuffer = 0;
            }
        }
    }

    private static final class Gradient {
        private final int[] colors;
        private final float[] positions;

        private Gradient(int[] colors, float[] positions) {
            this.colors = colors;
            this.positions = positions;
        }

        private static Gradient of(int[] colors, float[] positions) {
            float[] stops = positions;
            if (stops == null) {
                stops = new float[colors.length];
                if (colors.length == 1) {
                    stops[0] = 0.0F;
                } else {
                    for (int i = 0; i < stops.length; i++) {
                        stops[i] = i / (float)(stops.length - 1);
                    }
                }
            }
            return new Gradient(colors.clone(), stops.clone());
        }

        private int colorAt(float position) {
            if (this.colors.length == 1 || position <= this.positions[0]) {
                return this.colors[0];
            }

            for (int i = 1; i < this.positions.length; i++) {
                if (position <= this.positions[i]) {
                    float range = this.positions[i] - this.positions[i - 1];
                    float local = range <= 0.0F ? 0.0F : (position - this.positions[i - 1]) / range;
                    return lerpColor(this.colors[i - 1], this.colors[i], clamp01(local));
                }
            }
            return this.colors[this.colors.length - 1];
        }
    }

    private static final class FloatList {
        private float[] values;
        private int size;

        private FloatList(int capacity) {
            this.values = new float[Math.max(2, capacity)];
        }

        private void add(float x, float y) {
            ensureCapacity(this.size + 2);
            this.values[this.size++] = x;
            this.values[this.size++] = y;
        }

        private void addAll(float[] points) {
            ensureCapacity(this.size + points.length);
            System.arraycopy(points, 0, this.values, this.size, points.length);
            this.size += points.length;
        }

        private int size() {
            return this.size;
        }

        private float[] toArray() {
            return Arrays.copyOf(this.values, this.size);
        }

        private void ensureCapacity(int capacity) {
            if (capacity <= this.values.length) {
                return;
            }
            this.values = Arrays.copyOf(this.values, Math.max(capacity, this.values.length * 2));
        }
    }
}
