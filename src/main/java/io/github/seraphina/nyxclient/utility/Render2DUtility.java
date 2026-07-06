package io.github.seraphina.nyxclient.utility;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

public final class Render2DUtility {
    private static final int MAX_TRACKED_TEXTURE_UNITS = 12;
    private static final int FLOATS_PER_VERTEX = 6;
    private static final int MIN_SEGMENTS = 18;
    private static final int MAX_SEGMENTS = 96;
    private static final int FULL_BRIGHT = 15728880;

    private static final ThreadLocal<OpenGLRenderer> CURRENT_RENDERER = new ThreadLocal<>();
    private static final Queue<Consumer<OpenGLRenderer>> PENDING_RENDERERS = new ArrayDeque<>();

    private Render2DUtility() {
    }

    public static void withOpenGL(Runnable renderer) {
        Objects.requireNonNull(renderer, "renderer");
        draw(context -> renderer.run());
    }

    public static void flush() {
        if (PENDING_RENDERERS.isEmpty()) {
            return;
        }

        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            PENDING_RENDERERS.clear();
            return;
        }

        Queue<Consumer<OpenGLRenderer>> renderers = new ArrayDeque<>(PENDING_RENDERERS);
        PENDING_RENDERERS.clear();

        GLState state = GLState.capture();
        OpenGLRenderer renderer = null;
        try {
            renderer = OpenGLRenderer.create(minecraft);
            renderer.begin();
            CURRENT_RENDERER.set(renderer);
            while (!renderers.isEmpty()) {
                renderers.remove().accept(renderer);
            }
        } finally {
            CURRENT_RENDERER.remove();
            if (renderer != null) {
                renderer.end();
            }
            state.restore();
        }
    }

    public static void close() {
        RenderSystem.assertOnRenderThread();
        OpenGLRenderer.closeSharedResources();
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

    public static void drawRect(float x, float y, float width, float height, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        draw(renderer -> renderer.drawQuad(
            new Vertex(x, y, color),
            new Vertex(x + width, y, color),
            new Vertex(x + width, y + height, color),
            new Vertex(x, y + height, color)
        ));
    }

    public static void drawOutlineRect(float x, float y, float width, float height, float strokeWidth, int color) {
        drawOutlineRoundedRect(x, y, width, height, 0.0F, strokeWidth, color);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        draw(renderer -> renderer.drawPolygon(filledRoundedRectVertices(x, y, width, height, radius, color)));
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                       float bottomRightRadius, float bottomLeftRadius, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        draw(renderer -> renderer.drawPolygon(
            filledRoundedRectVertices(x, y, width, height, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius, color)
        ));
    }

    public static void drawOutlineRoundedRect(float x, float y, float width, float height, float radius, float strokeWidth, int color) {
        if (!canStroke(width, height, strokeWidth, color)) {
            return;
        }

        draw(renderer -> renderer.drawTriangles(strokedRoundedRectVertices(x, y, width, height, radius, strokeWidth, color)));
    }

    public static void drawCircle(float centerX, float centerY, float radius, int color) {
        if (radius <= 0.0F || isTransparent(color)) {
            return;
        }

        draw(renderer -> renderer.drawPolygon(filledEllipseVertices(centerX, centerY, radius, radius, color)));
    }

    public static void drawOutlineCircle(float centerX, float centerY, float radius, float strokeWidth, int color) {
        if (radius <= 0.0F || strokeWidth <= 0.0F || isTransparent(color)) {
            return;
        }

        draw(renderer -> renderer.drawTriangles(strokedEllipseVertices(centerX, centerY, radius, radius, strokeWidth, color)));
    }

    public static void drawOval(float x, float y, float width, float height, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        draw(renderer -> renderer.drawPolygon(filledEllipseVertices(x + width * 0.5F, y + height * 0.5F, width * 0.5F, height * 0.5F, color)));
    }

    public static void drawLine(float startX, float startY, float endX, float endY, float strokeWidth, int color) {
        if (strokeWidth <= 0.0F || isTransparent(color)) {
            return;
        }

        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0F) {
            drawCircle(startX, startY, strokeWidth * 0.5F, color);
            return;
        }

        float half = strokeWidth * 0.5F;
        float nx = -dy / length * half;
        float ny = dx / length * half;
        draw(renderer -> {
            renderer.drawQuad(
                new Vertex(startX + nx, startY + ny, color),
                new Vertex(endX + nx, endY + ny, color),
                new Vertex(endX - nx, endY - ny, color),
                new Vertex(startX - nx, startY - ny, color)
            );
            renderer.drawPolygon(filledEllipseVertices(startX, startY, half, half, color));
            renderer.drawPolygon(filledEllipseVertices(endX, endY, half, half, color));
        });
    }

    public static void drawArc(float x, float y, float width, float height, float startAngle, float sweepAngle, float strokeWidth, int color) {
        if (!canStroke(width, height, strokeWidth, color) || sweepAngle == 0.0F) {
            return;
        }

        draw(renderer -> renderer.drawTriangles(strokedArcVertices(x, y, width, height, startAngle, sweepAngle, strokeWidth, color)));
    }

    public static void drawVerticalGradientRect(float x, float y, float width, float height, int topColor, int bottomColor) {
        drawLinearGradientRect(x, y, width, height, x, y, x, y + height, new int[] {topColor, bottomColor}, null);
    }

    public static void drawHorizontalGradientRect(float x, float y, float width, float height, int leftColor, int rightColor) {
        drawLinearGradientRect(x, y, width, height, x, y, x + width, y, new int[] {leftColor, rightColor}, null);
    }

    public static void drawLinearGradientRect(float x, float y, float width, float height, float startX, float startY,
                                              float endX, float endY, int[] colors, float[] positions) {
        if (!canDrawGradient(width, height, colors, positions)) {
            return;
        }

        if (colors.length == 1) {
            drawRect(x, y, width, height, colors[0]);
            return;
        }

        Gradient gradient = Gradient.of(colors, positions);
        draw(renderer -> renderer.drawQuad(
            new Vertex(x, y, gradient.linearColor(x, y, startX, startY, endX, endY)),
            new Vertex(x + width, y, gradient.linearColor(x + width, y, startX, startY, endX, endY)),
            new Vertex(x + width, y + height, gradient.linearColor(x + width, y + height, startX, startY, endX, endY)),
            new Vertex(x, y + height, gradient.linearColor(x, y + height, startX, startY, endX, endY))
        ));
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

        if (colors.length == 1) {
            drawRoundedRect(x, y, width, height, radius, colors[0]);
            return;
        }

        Gradient gradient = Gradient.of(colors, positions);
        draw(renderer -> renderer.drawPolygon(
            filledRoundedRectVertices(x, y, width, height, radius, local -> gradient.linearColor(local.x, local.y, startX, startY, endX, endY))
        ));
    }

    public static void drawRadialGradientCircle(float centerX, float centerY, float radius, int innerColor, int outerColor) {
        drawRadialGradientCircle(centerX, centerY, radius, new int[] {innerColor, outerColor}, null);
    }

    public static void drawRadialGradientCircle(float centerX, float centerY, float radius, int[] colors, float[] positions) {
        if (radius <= 0.0F || !canDrawGradient(radius, radius, colors, positions)) {
            return;
        }

        if (colors.length == 1) {
            drawCircle(centerX, centerY, radius, colors[0]);
            return;
        }

        Gradient gradient = Gradient.of(colors, positions);
        int segments = segmentsForRadius(radius);
        List<Vertex> vertices = new ArrayList<>(segments + 2);
        vertices.add(new Vertex(centerX, centerY, gradient.colorAt(0.0F)));
        for (int i = 0; i <= segments; i++) {
            float angle = (float)(Math.PI * 2.0 * i / segments);
            float px = centerX + (float)Math.cos(angle) * radius;
            float py = centerY + (float)Math.sin(angle) * radius;
            vertices.add(new Vertex(px, py, gradient.colorAt(1.0F)));
        }

        draw(renderer -> renderer.drawTriangleFan(vertices));
    }

    public static void drawDropShadow(float x, float y, float width, float height, float radius, float offsetX, float offsetY,
                                      float blurRadius, int color) {
        if (!canDraw(width, height, color) || blurRadius <= 0.0F) {
            return;
        }

        int steps = Math.max(4, Math.min(18, Math.round(blurRadius)));
        for (int i = steps; i >= 1; i--) {
            float progress = i / (float)steps;
            float spread = blurRadius * progress;
            float alpha = ((color >>> 24) & 0xFF) * (1.0F - progress * 0.85F) / steps;
            int layerColor = withAlpha(color, Math.round(alpha));
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
        if (!canDrawText(text, font.lineHeight, color)) {
            return;
        }

        drawText(font, text, x, y, font.lineHeight, color);
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
        if (!canDraw(width, height, color) || !canDrawText(text, size, color)) {
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

        return font.width(text) * (size / font.lineHeight);
    }

    public static float getTextHeight(float size) {
        return Math.max(0.0F, size);
    }

    public static void withClip(float x, float y, float width, float height, Runnable action) {
        Objects.requireNonNull(action, "action");
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }

        draw(renderer -> renderer.withClip(x, y, width, height, action));
    }

    public static void withRoundedClip(float x, float y, float width, float height, float radius, Runnable action) {
        withClip(x, y, width, height, action);
    }

    public static void withTranslation(float x, float y, Runnable action) {
        Objects.requireNonNull(action, "action");
        draw(renderer -> renderer.withTransform(action, transform -> transform.translate(x, y)));
    }

    public static void withScale(float scaleX, float scaleY, float pivotX, float pivotY, Runnable action) {
        Objects.requireNonNull(action, "action");
        draw(renderer -> renderer.withTransform(action, transform -> {
            transform.translate(pivotX, pivotY);
            transform.scale(scaleX, scaleY);
            transform.translate(-pivotX, -pivotY);
        }));
    }

    public static void withRotation(float degrees, float pivotX, float pivotY, Runnable action) {
        Objects.requireNonNull(action, "action");
        draw(renderer -> renderer.withTransform(action, transform -> {
            transform.translate(pivotX, pivotY);
            transform.rotate(degrees);
            transform.translate(-pivotX, -pivotY);
        }));
    }

    private static void draw(Consumer<OpenGLRenderer> renderer) {
        Objects.requireNonNull(renderer, "renderer");

        OpenGLRenderer currentRenderer = CURRENT_RENDERER.get();
        if (currentRenderer != null) {
            renderer.accept(currentRenderer);
            return;
        }

        PENDING_RENDERERS.add(renderer);
    }

    private static void drawText(Font font, String text, float x, float y, float size, int color) {
        draw(renderer -> renderer.drawText(font, text, x, y, size, color));
    }

    private static Font minecraftFont() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.font;
    }

    private static List<Vertex> filledRoundedRectVertices(float x, float y, float width, float height, float radius, int color) {
        return filledRoundedRectVertices(x, y, width, height, radius, radius, radius, radius, color);
    }

    private static List<Vertex> filledRoundedRectVertices(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                                          float bottomRightRadius, float bottomLeftRadius, int color) {
        return filledRoundedRectVertices(x, y, width, height, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius, point -> color);
    }

    private static List<Vertex> filledRoundedRectVertices(float x, float y, float width, float height, float radius, ColorPicker colorPicker) {
        return filledRoundedRectVertices(x, y, width, height, radius, radius, radius, radius, colorPicker);
    }

    private static List<Vertex> filledRoundedRectVertices(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                                          float bottomRightRadius, float bottomLeftRadius, ColorPicker colorPicker) {
        int segments = segmentsForRadius(Math.max(Math.max(topLeftRadius, topRightRadius), Math.max(bottomRightRadius, bottomLeftRadius)));
        List<Point> boundary = roundedRectBoundary(x, y, width, height, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius, segments);
        List<Vertex> vertices = new ArrayList<>(boundary.size() + 1);
        Point center = new Point(x + width * 0.5F, y + height * 0.5F);
        vertices.add(new Vertex(center.x, center.y, colorPicker.color(center)));
        for (Point point : boundary) {
            vertices.add(new Vertex(point.x, point.y, colorPicker.color(point)));
        }
        return vertices;
    }

    private static List<Vertex> strokedRoundedRectVertices(float x, float y, float width, float height, float radius, float strokeWidth, int color) {
        float stroke = Math.min(strokeWidth, Math.min(width, height) * 0.5F);
        if (stroke <= 0.0F) {
            return List.of();
        }

        float outerRadius = clampRadius(width, height, radius);
        float innerWidth = width - stroke * 2.0F;
        float innerHeight = height - stroke * 2.0F;
        if (innerWidth <= 0.0F || innerHeight <= 0.0F) {
            return filledRoundedRectVertices(x, y, width, height, outerRadius, color);
        }

        int segments = segmentsForRadius(outerRadius);
        List<Point> outer = roundedRectBoundary(x, y, width, height, outerRadius, outerRadius, outerRadius, outerRadius, segments);
        List<Point> inner = roundedRectBoundary(
            x + stroke,
            y + stroke,
            innerWidth,
            innerHeight,
            Math.max(0.0F, outerRadius - stroke),
            Math.max(0.0F, outerRadius - stroke),
            Math.max(0.0F, outerRadius - stroke),
            Math.max(0.0F, outerRadius - stroke),
            segments
        );
        return ringVertices(outer, inner, color);
    }

    private static List<Vertex> filledEllipseVertices(float centerX, float centerY, float radiusX, float radiusY, int color) {
        int segments = segmentsForRadius(Math.max(radiusX, radiusY));
        List<Vertex> vertices = new ArrayList<>(segments + 2);
        vertices.add(new Vertex(centerX, centerY, color));
        for (int i = 0; i <= segments; i++) {
            float angle = (float)(Math.PI * 2.0 * i / segments);
            vertices.add(new Vertex(centerX + (float)Math.cos(angle) * radiusX, centerY + (float)Math.sin(angle) * radiusY, color));
        }
        return vertices;
    }

    private static List<Vertex> strokedEllipseVertices(float centerX, float centerY, float radiusX, float radiusY, float strokeWidth, int color) {
        float stroke = Math.min(strokeWidth, Math.min(radiusX, radiusY));
        float innerX = Math.max(0.0F, radiusX - stroke);
        float innerY = Math.max(0.0F, radiusY - stroke);
        if (innerX <= 0.0F || innerY <= 0.0F) {
            return filledEllipseVertices(centerX, centerY, radiusX, radiusY, color);
        }

        int segments = segmentsForRadius(Math.max(radiusX, radiusY));
        List<Point> outer = new ArrayList<>(segments);
        List<Point> inner = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            float angle = (float)(Math.PI * 2.0 * i / segments);
            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);
            outer.add(new Point(centerX + cos * radiusX, centerY + sin * radiusY));
            inner.add(new Point(centerX + cos * innerX, centerY + sin * innerY));
        }
        return ringVertices(outer, inner, color);
    }

    private static List<Vertex> strokedArcVertices(float x, float y, float width, float height, float startAngle, float sweepAngle,
                                                   float strokeWidth, int color) {
        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        float radiusX = width * 0.5F;
        float radiusY = height * 0.5F;
        float stroke = Math.min(strokeWidth, Math.min(radiusX, radiusY));
        int segments = Math.max(2, Math.min(MAX_SEGMENTS, (int)Math.ceil(Math.abs(sweepAngle) / 360.0F * segmentsForRadius(Math.max(radiusX, radiusY)))));
        float innerX = Math.max(0.0F, radiusX - stroke);
        float innerY = Math.max(0.0F, radiusY - stroke);
        List<Vertex> vertices = new ArrayList<>(segments * 6);
        for (int i = 0; i < segments; i++) {
            float angleA = (float)Math.toRadians(startAngle + sweepAngle * i / segments);
            float angleB = (float)Math.toRadians(startAngle + sweepAngle * (i + 1) / segments);
            Point outerA = new Point(centerX + (float)Math.cos(angleA) * radiusX, centerY + (float)Math.sin(angleA) * radiusY);
            Point outerB = new Point(centerX + (float)Math.cos(angleB) * radiusX, centerY + (float)Math.sin(angleB) * radiusY);
            Point innerA = new Point(centerX + (float)Math.cos(angleA) * innerX, centerY + (float)Math.sin(angleA) * innerY);
            Point innerB = new Point(centerX + (float)Math.cos(angleB) * innerX, centerY + (float)Math.sin(angleB) * innerY);
            addQuad(vertices, outerA, outerB, innerB, innerA, color);
        }
        return vertices;
    }

    private static List<Point> roundedRectBoundary(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                                   float bottomRightRadius, float bottomLeftRadius, int segmentsPerCorner) {
        float tl = clampRadius(width, height, topLeftRadius);
        float tr = clampRadius(width, height, topRightRadius);
        float br = clampRadius(width, height, bottomRightRadius);
        float bl = clampRadius(width, height, bottomLeftRadius);
        List<Point> points = new ArrayList<>(segmentsPerCorner * 4 + 4);
        addCorner(points, x + width - tr, y + tr, tr, 270.0F, 360.0F, segmentsPerCorner);
        addCorner(points, x + width - br, y + height - br, br, 0.0F, 90.0F, segmentsPerCorner);
        addCorner(points, x + bl, y + height - bl, bl, 90.0F, 180.0F, segmentsPerCorner);
        addCorner(points, x + tl, y + tl, tl, 180.0F, 270.0F, segmentsPerCorner);
        return points;
    }

    private static void addCorner(List<Point> points, float centerX, float centerY, float radius, float startAngle, float endAngle, int segments) {
        if (radius <= 0.0F) {
            points.add(new Point(centerX, centerY));
            return;
        }

        for (int i = 0; i <= segments; i++) {
            if (!points.isEmpty() && i == 0) {
                continue;
            }
            float angle = (float)Math.toRadians(startAngle + (endAngle - startAngle) * i / segments);
            points.add(new Point(centerX + (float)Math.cos(angle) * radius, centerY + (float)Math.sin(angle) * radius));
        }
    }

    private static List<Vertex> ringVertices(List<Point> outer, List<Point> inner, int color) {
        int count = Math.min(outer.size(), inner.size());
        List<Vertex> vertices = new ArrayList<>(count * 6);
        for (int i = 0; i < count; i++) {
            Point outerA = outer.get(i);
            Point outerB = outer.get((i + 1) % count);
            Point innerB = inner.get((i + 1) % count);
            Point innerA = inner.get(i);
            addQuad(vertices, outerA, outerB, innerB, innerA, color);
        }
        return vertices;
    }

    private static void addQuad(List<Vertex> vertices, Point a, Point b, Point c, Point d, int color) {
        vertices.add(new Vertex(a.x, a.y, color));
        vertices.add(new Vertex(b.x, b.y, color));
        vertices.add(new Vertex(c.x, c.y, color));
        vertices.add(new Vertex(c.x, c.y, color));
        vertices.add(new Vertex(d.x, d.y, color));
        vertices.add(new Vertex(a.x, a.y, color));
    }

    private static int segmentsForRadius(float radius) {
        return Math.max(MIN_SEGMENTS, Math.min(MAX_SEGMENTS, (int)Math.ceil(Math.max(1.0F, radius) * 0.45F)));
    }

    private static boolean canDraw(float width, float height, int color) {
        return width > 0.0F && height > 0.0F && !isTransparent(color);
    }

    private static boolean canStroke(float width, float height, float strokeWidth, int color) {
        return canDraw(width, height, color) && strokeWidth > 0.0F;
    }

    private static boolean canDrawGradient(float width, float height, int[] colors, float[] positions) {
        Objects.requireNonNull(colors, "colors");
        if (positions != null && positions.length != colors.length) {
            throw new IllegalArgumentException("Gradient positions length must match colors length");
        }

        return width > 0.0F && height > 0.0F && colors.length > 0;
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

    private interface ColorPicker {
        int color(Point point);
    }

    private interface TransformOperation {
        void apply(Transform transform);
    }

    private record Point(float x, float y) {
    }

    private record Vertex(float x, float y, int color) {
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

        private int linearColor(float x, float y, float startX, float startY, float endX, float endY) {
            float dx = endX - startX;
            float dy = endY - startY;
            float lengthSquared = dx * dx + dy * dy;
            if (lengthSquared <= 0.0F) {
                return colorAt(0.0F);
            }

            float t = ((x - startX) * dx + (y - startY) * dy) / lengthSquared;
            return colorAt(t);
        }

        private int colorAt(float position) {
            if (position <= positions[0]) {
                return colors[0];
            }

            for (int i = 1; i < positions.length; i++) {
                if (position <= positions[i]) {
                    float range = positions[i] - positions[i - 1];
                    float local = range <= 0.0F ? 0.0F : (position - positions[i - 1]) / range;
                    return lerpColor(colors[i - 1], colors[i], clamp01(local));
                }
            }

            return colors[colors.length - 1];
        }

        private static int lerpColor(int from, int to, float progress) {
            int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * progress);
            int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * progress);
            int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * progress);
            int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * progress);
            return rgba(r, g, b, a);
        }
    }

    private static final class Transform {
        private float a = 1.0F;
        private float b;
        private float c;
        private float d = 1.0F;
        private float tx;
        private float ty;

        private Transform() {
        }

        private Transform(Transform other) {
            this.a = other.a;
            this.b = other.b;
            this.c = other.c;
            this.d = other.d;
            this.tx = other.tx;
            this.ty = other.ty;
        }

        private void translate(float x, float y) {
            this.tx += this.a * x + this.c * y;
            this.ty += this.b * x + this.d * y;
        }

        private void scale(float scaleX, float scaleY) {
            this.a *= scaleX;
            this.b *= scaleX;
            this.c *= scaleY;
            this.d *= scaleY;
        }

        private void rotate(float degrees) {
            float radians = (float)Math.toRadians(degrees);
            float cos = (float)Math.cos(radians);
            float sin = (float)Math.sin(radians);
            float nextA = this.a * cos + this.c * sin;
            float nextB = this.b * cos + this.d * sin;
            float nextC = this.c * cos - this.a * sin;
            float nextD = this.d * cos - this.b * sin;
            this.a = nextA;
            this.b = nextB;
            this.c = nextC;
            this.d = nextD;
        }

        private Point transform(float x, float y) {
            return new Point(this.a * x + this.c * y + this.tx, this.b * x + this.d * y + this.ty);
        }

        private Matrix4f toMatrix(float x, float y, float scale) {
            Transform textTransform = new Transform(this);
            textTransform.translate(x, y);
            textTransform.scale(scale, scale);
            return new Matrix4f().set(
                textTransform.a, textTransform.b, 0.0F, 0.0F,
                textTransform.c, textTransform.d, 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                textTransform.tx, textTransform.ty, 0.0F, 1.0F
            );
        }
    }

    private static final class ScissorRect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private ScissorRect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean empty() {
            return width <= 0 || height <= 0;
        }

        private ScissorRect intersect(ScissorRect other) {
            int nextX = Math.max(this.x, other.x);
            int nextY = Math.max(this.y, other.y);
            int nextRight = Math.min(this.x + this.width, other.x + other.width);
            int nextTop = Math.min(this.y + this.height, other.y + other.height);
            return new ScissorRect(nextX, nextY, Math.max(0, nextRight - nextX), Math.max(0, nextTop - nextY));
        }
    }

    private static final class OpenGLRenderer {
        private static final String VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec2 Position;
            layout(location = 1) in vec4 Color;
            uniform vec2 ScreenSize;
            out vec4 vertexColor;
            void main() {
                vec2 normalized = vec2(Position.x / ScreenSize.x * 2.0 - 1.0, 1.0 - Position.y / ScreenSize.y * 2.0);
                gl_Position = vec4(normalized, 0.0, 1.0);
                vertexColor = Color;
            }
            """;
        private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec4 vertexColor;
            out vec4 fragColor;
            void main() {
                fragColor = vertexColor;
            }
            """;

        private static int shaderProgram;
        private static int vao;
        private static int vbo;
        private static int screenSizeUniform;

        private final Minecraft minecraft;
        private final Window window;
        private final float guiWidth;
        private final float guiHeight;
        private final float scaleX;
        private final float scaleY;
        private final ArrayDeque<Transform> transformStack = new ArrayDeque<>();
        private final ArrayDeque<ScissorRect> scissorStack = new ArrayDeque<>();
        private Transform transform = new Transform();
        private ScissorRect currentScissor;

        private OpenGLRenderer(Minecraft minecraft) {
            this.minecraft = minecraft;
            this.window = minecraft.getWindow();
            this.guiWidth = Math.max(1, this.window.getGuiScaledWidth());
            this.guiHeight = Math.max(1, this.window.getGuiScaledHeight());
            this.scaleX = this.window.getWidth() / this.guiWidth;
            this.scaleY = this.window.getHeight() / this.guiHeight;
        }

        private static OpenGLRenderer create(Minecraft minecraft) {
            ensureSharedResources();
            return new OpenGLRenderer(minecraft);
        }

        private void begin() {
            bindMainRenderTarget();
            GlStateManager._viewport(0, 0, window.getWidth(), window.getHeight());
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableCull();
            GlStateManager._disableStencilTest();
            GlStateManager._enableBlend();
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager._disableScissorTest();
            useShader();
        }

        private void end() {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GlStateManager._glBindVertexArray(0);
            GlStateManager._glUseProgram(0);
        }

        private void withTransform(Runnable action, TransformOperation operation) {
            transformStack.push(new Transform(transform));
            try {
                operation.apply(transform);
                action.run();
            } finally {
                transform = transformStack.pop();
            }
        }

        private void withClip(float x, float y, float width, float height, Runnable action) {
            ScissorRect previous = currentScissor;
            scissorStack.push(previous);
            try {
                ScissorRect clip = toScissor(x, y, width, height);
                currentScissor = previous == null ? clip : previous.intersect(clip);
                applyScissor();
                if (!currentScissor.empty()) {
                    action.run();
                }
            } finally {
                currentScissor = scissorStack.pop();
                applyScissor();
            }
        }

        private void drawText(Font font, String text, float x, float y, float size, int color) {
            if (currentScissor != null && currentScissor.empty()) {
                return;
            }

            Matrix4f matrix = transform.toMatrix(x, y, size / font.lineHeight);
            if (currentScissor != null) {
                RenderSystem.enableScissorForRenderTypeDraws(currentScissor.x, currentScissor.y, currentScissor.width, currentScissor.height);
            }

            try (ByteBufferBuilder buffer = new ByteBufferBuilder(786432)) {
                MultiBufferSource.BufferSource source = MultiBufferSource.immediate(buffer);
                font.drawInBatch(text, 0.0F, 0.0F, color, false, matrix, source, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
                source.endBatch();
            } finally {
                if (currentScissor != null) {
                    RenderSystem.disableScissorForRenderTypeDraws();
                }
                useShader();
                applyScissor();
            }
        }

        private void drawQuad(Vertex a, Vertex b, Vertex c, Vertex d) {
            drawTriangles(List.of(a, b, c, c, d, a));
        }

        private void drawPolygon(List<Vertex> vertices) {
            if (vertices.size() < 3) {
                return;
            }

            drawTriangleFan(vertices);
        }

        private void drawTriangleFan(List<Vertex> vertices) {
            if (vertices.size() < 3) {
                return;
            }

            float[] data = toData(vertices);
            draw(data, GL11.GL_TRIANGLE_FAN);
        }

        private void drawTriangles(List<Vertex> vertices) {
            if (vertices.size() < 3) {
                return;
            }

            float[] data = toData(vertices);
            draw(data, GL11.GL_TRIANGLES);
        }

        private void draw(float[] vertexData, int mode) {
            if (vertexData.length == 0 || currentScissor != null && currentScissor.empty()) {
                return;
            }

            useShader();
            applyScissor();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, GL15.GL_STREAM_DRAW);
            GL11.glDrawArrays(mode, 0, vertexData.length / FLOATS_PER_VERTEX);
        }

        private float[] toData(List<Vertex> vertices) {
            float[] data = new float[vertices.size() * FLOATS_PER_VERTEX];
            int index = 0;
            for (Vertex vertex : vertices) {
                Point point = transform.transform(vertex.x, vertex.y);
                int color = vertex.color;
                data[index++] = point.x;
                data[index++] = point.y;
                data[index++] = ((color >>> 16) & 0xFF) / 255.0F;
                data[index++] = ((color >>> 8) & 0xFF) / 255.0F;
                data[index++] = (color & 0xFF) / 255.0F;
                data[index++] = ((color >>> 24) & 0xFF) / 255.0F;
            }
            return data;
        }

        private ScissorRect toScissor(float x, float y, float width, float height) {
            Point a = transform.transform(x, y);
            Point b = transform.transform(x + width, y);
            Point c = transform.transform(x + width, y + height);
            Point d = transform.transform(x, y + height);
            float left = Math.max(0.0F, Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)));
            float right = Math.min(guiWidth, Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)));
            float top = Math.max(0.0F, Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y)));
            float bottom = Math.min(guiHeight, Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y)));

            int scissorX = (int)Math.floor(left * scaleX);
            int scissorY = (int)Math.floor((guiHeight - bottom) * scaleY);
            int scissorWidth = (int)Math.ceil((right - left) * scaleX);
            int scissorHeight = (int)Math.ceil((bottom - top) * scaleY);
            return new ScissorRect(scissorX, scissorY, scissorWidth, scissorHeight);
        }

        private void applyScissor() {
            if (currentScissor == null) {
                GlStateManager._disableScissorTest();
                return;
            }

            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(currentScissor.x, currentScissor.y, Math.max(0, currentScissor.width), Math.max(0, currentScissor.height));
        }

        private void useShader() {
            GlStateManager._glUseProgram(shaderProgram);
            GL20.glUniform2f(screenSizeUniform, guiWidth, guiHeight);
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        }

        private void bindMainRenderTarget() {
            RenderTarget target = minecraft.getMainRenderTarget();
            if (target == null) {
                return;
            }

            GpuTextureView colorView = target.getColorTextureView();
            if (colorView instanceof GlTextureView glColorView && RenderSystem.getDevice() instanceof GlDevice glDevice) {
                GpuTexture depthTexture = target.useDepth ? target.getDepthTexture() : null;
                int framebufferId = glColorView.texture().getFbo(glDevice.directStateAccess(), depthTexture);
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
            }
        }

        private static void ensureSharedResources() {
            if (shaderProgram != 0) {
                return;
            }

            int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            shaderProgram = GL20.glCreateProgram();
            GL20.glAttachShader(shaderProgram, vertexShader);
            GL20.glAttachShader(shaderProgram, fragmentShader);
            GL20.glLinkProgram(shaderProgram);
            if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Failed to link 2D OpenGL shader: " + GL20.glGetProgramInfoLog(shaderProgram));
            }
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);

            screenSizeUniform = GL20.glGetUniformLocation(shaderProgram, "ScreenSize");
            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 2L * Float.BYTES);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GlStateManager._glBindVertexArray(0);
        }

        private static int compileShader(int type, String source) {
            int shader = GL20.glCreateShader(type);
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Failed to compile 2D OpenGL shader: " + GL20.glGetShaderInfoLog(shader));
            }
            return shader;
        }

        private static void closeSharedResources() {
            if (vbo != 0) {
                GL15.glDeleteBuffers(vbo);
                vbo = 0;
            }
            if (vao != 0) {
                GL30.glDeleteVertexArrays(vao);
                vao = 0;
            }
            if (shaderProgram != 0) {
                GL20.glDeleteProgram(shaderProgram);
                shaderProgram = 0;
            }
            screenSizeUniform = 0;
        }
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
