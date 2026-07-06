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
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

import static org.lwjgl.nanovg.NanoVG.NVG_HOLE;
import static org.lwjgl.nanovg.NanoVG.NVG_ROUND;
import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgBoxGradient;
import static org.lwjgl.nanovg.NanoVG.nvgCircle;
import static org.lwjgl.nanovg.NanoVG.nvgEllipse;
import static org.lwjgl.nanovg.NanoVG.nvgEndFrame;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgLineCap;
import static org.lwjgl.nanovg.NanoVG.nvgLineJoin;
import static org.lwjgl.nanovg.NanoVG.nvgLineTo;
import static org.lwjgl.nanovg.NanoVG.nvgLinearGradient;
import static org.lwjgl.nanovg.NanoVG.nvgMoveTo;
import static org.lwjgl.nanovg.NanoVG.nvgPathWinding;
import static org.lwjgl.nanovg.NanoVG.nvgRadialGradient;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgResetScissor;
import static org.lwjgl.nanovg.NanoVG.nvgResetTransform;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRectVarying;
import static org.lwjgl.nanovg.NanoVG.nvgScissor;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;
import static org.lwjgl.nanovg.NanoVG.nvgTransform;

public final class Render2DUtility {
    private static final int MAX_TRACKED_TEXTURE_UNITS = 12;
    private static final int MIN_SEGMENTS = 18;
    private static final int MAX_SEGMENTS = 96;
    private static final int FULL_BRIGHT = 15728880;

    private static final ThreadLocal<NanoVGRenderer> CURRENT_RENDERER = new ThreadLocal<>();
    private static final Queue<Consumer<NanoVGRenderer>> PENDING_RENDERERS = new ArrayDeque<>();

    private Render2DUtility() {
    }

    public static void withOpenGL(Runnable renderer) {
        Objects.requireNonNull(renderer, "renderer");
        draw(context -> context.runOpenGL(renderer));
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

        Queue<Consumer<NanoVGRenderer>> renderers = new ArrayDeque<>(PENDING_RENDERERS);
        PENDING_RENDERERS.clear();

        GLState state = GLState.capture();
        NanoVGRenderer renderer = null;
        try {
            renderer = NanoVGRenderer.create(minecraft);
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
        NanoVGRenderer.closeSharedResources();
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

        draw(renderer -> renderer.fillRect(x, y, width, height, color));
    }

    public static void drawOutlineRect(float x, float y, float width, float height, float strokeWidth, int color) {
        drawOutlineRoundedRect(x, y, width, height, 0.0F, strokeWidth, color);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        draw(renderer -> renderer.fillRoundedRect(x, y, width, height, radius, color));
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                       float bottomRightRadius, float bottomLeftRadius, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        draw(renderer -> renderer.fillRoundedRect(
            x,
            y,
            width,
            height,
            topLeftRadius,
            topRightRadius,
            bottomRightRadius,
            bottomLeftRadius,
            color
        ));
    }

    public static void drawOutlineRoundedRect(float x, float y, float width, float height, float radius, float strokeWidth, int color) {
        if (!canStroke(width, height, strokeWidth, color)) {
            return;
        }

        draw(renderer -> renderer.strokeRoundedRect(x, y, width, height, radius, strokeWidth, color));
    }

    public static void drawCircle(float centerX, float centerY, float radius, int color) {
        if (radius <= 0.0F || isTransparent(color)) {
            return;
        }

        draw(renderer -> renderer.fillCircle(centerX, centerY, radius, color));
    }

    public static void drawOutlineCircle(float centerX, float centerY, float radius, float strokeWidth, int color) {
        if (radius <= 0.0F || strokeWidth <= 0.0F || isTransparent(color)) {
            return;
        }

        draw(renderer -> renderer.strokeCircle(centerX, centerY, radius, strokeWidth, color));
    }

    public static void drawOval(float x, float y, float width, float height, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        draw(renderer -> renderer.fillOval(x + width * 0.5F, y + height * 0.5F, width * 0.5F, height * 0.5F, color));
    }

    public static void drawLine(float startX, float startY, float endX, float endY, float strokeWidth, int color) {
        if (strokeWidth <= 0.0F || isTransparent(color)) {
            return;
        }

        draw(renderer -> renderer.strokeLine(startX, startY, endX, endY, strokeWidth, color));
    }

    public static void drawArc(float x, float y, float width, float height, float startAngle, float sweepAngle, float strokeWidth, int color) {
        if (!canStroke(width, height, strokeWidth, color) || sweepAngle == 0.0F) {
            return;
        }

        draw(renderer -> renderer.strokeArc(x, y, width, height, startAngle, sweepAngle, strokeWidth, color));
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
        draw(renderer -> renderer.fillLinearGradientRect(
            x,
            y,
            width,
            height,
            0.0F,
            startX,
            startY,
            endX,
            endY,
            gradient.colorAt(0.0F),
            gradient.colorAt(1.0F)
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
        draw(renderer -> renderer.fillLinearGradientRect(
            x,
            y,
            width,
            height,
            radius,
            startX,
            startY,
            endX,
            endY,
            gradient.colorAt(0.0F),
            gradient.colorAt(1.0F)
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
        draw(renderer -> renderer.fillRadialGradientCircle(centerX, centerY, radius, gradient.colorAt(0.0F), gradient.colorAt(1.0F)));
    }

    public static void drawDropShadow(float x, float y, float width, float height, float radius, float offsetX, float offsetY,
                                      float blurRadius, int color) {
        if (!canDraw(width, height, color) || blurRadius <= 0.0F) {
            return;
        }

        draw(renderer -> renderer.drawBoxShadow(x, y, width, height, radius, offsetX, offsetY, blurRadius, color));
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

    private static void draw(Consumer<NanoVGRenderer> renderer) {
        Objects.requireNonNull(renderer, "renderer");

        NanoVGRenderer currentRenderer = CURRENT_RENDERER.get();
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

    private static NVGColor nvgColor(int color, MemoryStack stack) {
        return NVGColor.malloc(stack)
            .r(((color >>> 16) & 0xFF) / 255.0F)
            .g(((color >>> 8) & 0xFF) / 255.0F)
            .b((color & 0xFF) / 255.0F)
            .a(((color >>> 24) & 0xFF) / 255.0F);
    }

    private interface TransformOperation {
        void apply(Transform transform);
    }

    private record Point(float x, float y) {
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

    private static final class NanoVGRenderer {
        private static long context;

        private final Minecraft minecraft;
        private final Window window;
        private final float guiWidth;
        private final float guiHeight;
        private final float scaleX;
        private final float scaleY;
        private final ArrayDeque<Transform> transformStack = new ArrayDeque<>();
        private Transform transform = new Transform();
        private ScissorRect currentScissor;
        private boolean frameOpen;

        private NanoVGRenderer(Minecraft minecraft) {
            this.minecraft = minecraft;
            this.window = minecraft.getWindow();
            this.guiWidth = Math.max(1, this.window.getGuiScaledWidth());
            this.guiHeight = Math.max(1, this.window.getGuiScaledHeight());
            this.scaleX = this.window.getWidth() / this.guiWidth;
            this.scaleY = this.window.getHeight() / this.guiHeight;
        }

        private static NanoVGRenderer create(Minecraft minecraft) {
            ensureSharedResources();
            return new NanoVGRenderer(minecraft);
        }

        private void begin() {
            if (frameOpen) {
                return;
            }

            bindMainRenderTarget();
            GlStateManager._viewport(0, 0, window.getWidth(), window.getHeight());
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableCull();
            GlStateManager._enableBlend();
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager._disableScissorTest();

            nvgBeginFrame(context, guiWidth, guiHeight, Math.max(scaleX, scaleY));
            frameOpen = true;
            applyNanoVGState();
        }

        private void end() {
            if (!frameOpen) {
                return;
            }

            nvgEndFrame(context);
            frameOpen = false;
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
            try {
                ScissorRect clip = toScissor(x, y, width, height);
                currentScissor = previous == null ? clip : previous.intersect(clip);
                if (!currentScissor.empty()) {
                    action.run();
                }
            } finally {
                currentScissor = previous;
            }
        }

        private void runOpenGL(Runnable action) {
            Objects.requireNonNull(action, "action");
            if (currentScissor != null && currentScissor.empty()) {
                return;
            }

            end();
            applyGLScissor();
            try {
                action.run();
            } finally {
                GlStateManager._disableScissorTest();
                begin();
            }
        }

        private void drawText(Font font, String text, float x, float y, float size, int color) {
            if (currentScissor != null && currentScissor.empty()) {
                return;
            }

            Matrix4f matrix = transform.toMatrix(x, y, size / font.lineHeight);
            runOpenGL(() -> {
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
                }
            });
        }

        private void fillRect(float x, float y, float width, float height, int color) {
            if (isClippedOut()) {
                return;
            }

            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgBeginPath(context);
                nvgRect(context, x, y, width, height);
                nvgFillColor(context, nvgColor(color, stack));
                nvgFill(context);
            }
        }

        private void fillRoundedRect(float x, float y, float width, float height, float radius, int color) {
            if (isClippedOut()) {
                return;
            }

            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgBeginPath(context);
                nvgRoundedRect(context, x, y, width, height, clampRadius(width, height, radius));
                nvgFillColor(context, nvgColor(color, stack));
                nvgFill(context);
            }
        }

        private void fillRoundedRect(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                     float bottomRightRadius, float bottomLeftRadius, int color) {
            if (isClippedOut()) {
                return;
            }

            float tl = clampRadius(width, height, topLeftRadius);
            float tr = clampRadius(width, height, topRightRadius);
            float br = clampRadius(width, height, bottomRightRadius);
            float bl = clampRadius(width, height, bottomLeftRadius);
            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgBeginPath(context);
                nvgRoundedRectVarying(context, x, y, width, height, tl, tr, br, bl);
                nvgFillColor(context, nvgColor(color, stack));
                nvgFill(context);
            }
        }

        private void strokeRoundedRect(float x, float y, float width, float height, float radius, float strokeWidth, int color) {
            if (isClippedOut()) {
                return;
            }

            float halfStroke = strokeWidth * 0.5F;
            float localX = x + halfStroke;
            float localY = y + halfStroke;
            float localWidth = Math.max(0.0F, width - strokeWidth);
            float localHeight = Math.max(0.0F, height - strokeWidth);
            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgBeginPath(context);
                nvgRoundedRect(context, localX, localY, localWidth, localHeight, clampRadius(localWidth, localHeight, radius));
                nvgStrokeWidth(context, strokeWidth);
                nvgStrokeColor(context, nvgColor(color, stack));
                nvgStroke(context);
            }
        }

        private void fillCircle(float centerX, float centerY, float radius, int color) {
            if (isClippedOut()) {
                return;
            }

            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgBeginPath(context);
                nvgCircle(context, centerX, centerY, radius);
                nvgFillColor(context, nvgColor(color, stack));
                nvgFill(context);
            }
        }

        private void strokeCircle(float centerX, float centerY, float radius, float strokeWidth, int color) {
            if (isClippedOut()) {
                return;
            }

            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgBeginPath(context);
                nvgCircle(context, centerX, centerY, Math.max(0.0F, radius - strokeWidth * 0.5F));
                nvgStrokeWidth(context, strokeWidth);
                nvgStrokeColor(context, nvgColor(color, stack));
                nvgStroke(context);
            }
        }

        private void fillOval(float centerX, float centerY, float radiusX, float radiusY, int color) {
            if (isClippedOut()) {
                return;
            }

            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgBeginPath(context);
                nvgEllipse(context, centerX, centerY, radiusX, radiusY);
                nvgFillColor(context, nvgColor(color, stack));
                nvgFill(context);
            }
        }

        private void strokeLine(float startX, float startY, float endX, float endY, float strokeWidth, int color) {
            if (isClippedOut()) {
                return;
            }

            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgBeginPath(context);
                nvgMoveTo(context, startX, startY);
                nvgLineTo(context, endX, endY);
                nvgLineCap(context, NVG_ROUND);
                nvgLineJoin(context, NVG_ROUND);
                nvgStrokeWidth(context, strokeWidth);
                nvgStrokeColor(context, nvgColor(color, stack));
                nvgStroke(context);
            }
        }

        private void strokeArc(float x, float y, float width, float height, float startAngle, float sweepAngle, float strokeWidth, int color) {
            if (isClippedOut()) {
                return;
            }

            float centerX = x + width * 0.5F;
            float centerY = y + height * 0.5F;
            float radiusX = width * 0.5F;
            float radiusY = height * 0.5F;
            int segments = Math.max(2, Math.min(MAX_SEGMENTS, (int)Math.ceil(Math.abs(sweepAngle) / 360.0F * segmentsForRadius(Math.max(radiusX, radiusY)))));

            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                nvgBeginPath(context);
                for (int i = 0; i <= segments; i++) {
                    float angle = (float)Math.toRadians(startAngle + sweepAngle * i / segments);
                    float px = centerX + (float)Math.cos(angle) * radiusX;
                    float py = centerY + (float)Math.sin(angle) * radiusY;
                    if (i == 0) {
                        nvgMoveTo(context, px, py);
                    } else {
                        nvgLineTo(context, px, py);
                    }
                }
                nvgLineCap(context, NVG_ROUND);
                nvgLineJoin(context, NVG_ROUND);
                nvgStrokeWidth(context, strokeWidth);
                nvgStrokeColor(context, nvgColor(color, stack));
                nvgStroke(context);
            }
        }

        private void fillLinearGradientRect(float x, float y, float width, float height, float radius, float startX, float startY,
                                            float endX, float endY, int startColor, int endColor) {
            if (isClippedOut()) {
                return;
            }

            if (startX == endX && startY == endY) {
                if (radius <= 0.0F) {
                    fillRect(x, y, width, height, startColor);
                } else {
                    fillRoundedRect(x, y, width, height, radius, startColor);
                }
                return;
            }

            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                NVGPaint paint = NVGPaint.malloc(stack);
                nvgLinearGradient(context, startX, startY, endX, endY, nvgColor(startColor, stack), nvgColor(endColor, stack), paint);
                nvgBeginPath(context);
                if (radius <= 0.0F) {
                    nvgRect(context, x, y, width, height);
                } else {
                    nvgRoundedRect(context, x, y, width, height, clampRadius(width, height, radius));
                }
                nvgFillPaint(context, paint);
                nvgFill(context);
            }
        }

        private void fillRadialGradientCircle(float centerX, float centerY, float radius, int innerColor, int outerColor) {
            if (isClippedOut()) {
                return;
            }

            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                NVGPaint paint = NVGPaint.malloc(stack);
                nvgRadialGradient(context, centerX, centerY, 0.0F, radius, nvgColor(innerColor, stack), nvgColor(outerColor, stack), paint);
                nvgBeginPath(context);
                nvgCircle(context, centerX, centerY, radius);
                nvgFillPaint(context, paint);
                nvgFill(context);
            }
        }

        private void drawBoxShadow(float x, float y, float width, float height, float radius, float offsetX, float offsetY,
                                   float blurRadius, int color) {
            if (isClippedOut()) {
                return;
            }

            float spread = Math.max(1.0F, blurRadius);
            float shadowX = x + offsetX;
            float shadowY = y + offsetY;
            int transparent = withAlpha(color, 0);

            applyNanoVGState();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                NVGPaint paint = NVGPaint.malloc(stack);
                nvgBoxGradient(
                    context,
                    shadowX - spread,
                    shadowY - spread,
                    width + spread * 2.0F,
                    height + spread * 2.0F,
                    clampRadius(width + spread * 2.0F, height + spread * 2.0F, radius + spread),
                    blurRadius,
                    nvgColor(color, stack),
                    nvgColor(transparent, stack),
                    paint
                );

                nvgBeginPath(context);
                nvgRoundedRect(
                    context,
                    shadowX - spread,
                    shadowY - spread,
                    width + spread * 2.0F,
                    height + spread * 2.0F,
                    clampRadius(width + spread * 2.0F, height + spread * 2.0F, radius + spread)
                );
                nvgRoundedRect(context, shadowX, shadowY, width, height, clampRadius(width, height, radius));
                nvgPathWinding(context, NVG_HOLE);
                nvgFillPaint(context, paint);
                nvgFill(context);
            }
        }

        private boolean isClippedOut() {
            return currentScissor != null && currentScissor.empty();
        }

        private void applyNanoVGState() {
            begin();
            nvgResetTransform(context);
            nvgResetScissor(context);
            if (currentScissor != null) {
                if (currentScissor.empty()) {
                    nvgScissor(context, 0.0F, 0.0F, 0.0F, 0.0F);
                } else {
                    float scissorX = currentScissor.x / scaleX;
                    float scissorY = guiHeight - (currentScissor.y + currentScissor.height) / scaleY;
                    float scissorWidth = currentScissor.width / scaleX;
                    float scissorHeight = currentScissor.height / scaleY;
                    nvgScissor(context, scissorX, scissorY, scissorWidth, scissorHeight);
                }
            }
            nvgTransform(context, transform.a, transform.b, transform.c, transform.d, transform.tx, transform.ty);
        }

        private void applyGLScissor() {
            if (currentScissor == null) {
                GlStateManager._disableScissorTest();
                return;
            }

            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(currentScissor.x, currentScissor.y, Math.max(0, currentScissor.width), Math.max(0, currentScissor.height));
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
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);

                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                    throw new IllegalStateException("Main render target framebuffer is incomplete: 0x" + Integer.toHexString(status));
                }
            }
        }

        private static int segmentsForRadius(float radius) {
            return Math.max(MIN_SEGMENTS, Math.min(MAX_SEGMENTS, (int)Math.ceil(Math.max(1.0F, radius) * 0.45F)));
        }

        private static void ensureSharedResources() {
            if (context != 0L) {
                return;
            }

            context = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);
            if (context == 0L) {
                throw new IllegalStateException("Failed to create NanoVG context");
            }
        }

        private static void closeSharedResources() {
            if (context != 0L) {
                NanoVGGL3.nvgDelete(context);
                context = 0L;
            }
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
        private final int stencilFunc;
        private final int stencilRef;
        private final int stencilValueMask;
        private final int stencilFail;
        private final int stencilPassDepthFail;
        private final int stencilPassDepthPass;
        private final int stencilWriteMask;
        private final int stencilBackFunc;
        private final int stencilBackRef;
        private final int stencilBackValueMask;
        private final int stencilBackFail;
        private final int stencilBackPassDepthFail;
        private final int stencilBackPassDepthPass;
        private final int stencilBackWriteMask;
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
            this.stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
            this.stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
            this.stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
            this.stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
            this.stencilPassDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
            this.stencilPassDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
            this.stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
            this.stencilBackFunc = GL11.glGetInteger(GL20.GL_STENCIL_BACK_FUNC);
            this.stencilBackRef = GL11.glGetInteger(GL20.GL_STENCIL_BACK_REF);
            this.stencilBackValueMask = GL11.glGetInteger(GL20.GL_STENCIL_BACK_VALUE_MASK);
            this.stencilBackFail = GL11.glGetInteger(GL20.GL_STENCIL_BACK_FAIL);
            this.stencilBackPassDepthFail = GL11.glGetInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL);
            this.stencilBackPassDepthPass = GL11.glGetInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS);
            this.stencilBackWriteMask = GL11.glGetInteger(GL20.GL_STENCIL_BACK_WRITEMASK);
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
            GL20.glStencilFuncSeparate(GL11.GL_FRONT, this.stencilFunc, this.stencilRef, this.stencilValueMask);
            GL20.glStencilFuncSeparate(GL11.GL_BACK, this.stencilBackFunc, this.stencilBackRef, this.stencilBackValueMask);
            GL20.glStencilOpSeparate(GL11.GL_FRONT, this.stencilFail, this.stencilPassDepthFail, this.stencilPassDepthPass);
            GL20.glStencilOpSeparate(GL11.GL_BACK, this.stencilBackFail, this.stencilBackPassDepthFail, this.stencilBackPassDepthPass);
            GL20.glStencilMaskSeparate(GL11.GL_FRONT, this.stencilWriteMask);
            GL20.glStencilMaskSeparate(GL11.GL_BACK, this.stencilBackWriteMask);

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
