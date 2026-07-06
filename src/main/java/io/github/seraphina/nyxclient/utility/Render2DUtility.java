package io.github.seraphina.nyxclient.utility;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.GradientStyle;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import io.github.seraphina.nyxclient.utility.skija.SkiaUtility;

import java.util.Objects;
import java.util.function.Consumer;

public final class Render2DUtility {
    private static final ThreadLocal<Canvas> CURRENT_CANVAS = new ThreadLocal<>();

    private Render2DUtility() {
    }

    public static void withCanvas(Consumer<Canvas> renderer) {
        draw(renderer);
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

        draw(canvas -> {
            try (Paint paint = fillPaint(color)) {
                canvas.drawRect(rect(x, y, width, height), paint);
            }
        });
    }

    public static void drawOutlineRect(float x, float y, float width, float height, float strokeWidth, int color) {
        if (!canStroke(width, height, strokeWidth, color)) {
            return;
        }

        draw(canvas -> {
            try (Paint paint = strokePaint(color, strokeWidth)) {
                canvas.drawRect(rect(x, y, width, height), paint);
            }
        });
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        draw(canvas -> {
            try (Paint paint = fillPaint(color)) {
                canvas.drawRRect(rrect(x, y, width, height, radius), paint);
            }
        });
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                                       float bottomRightRadius, float bottomLeftRadius, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        draw(canvas -> {
            try (Paint paint = fillPaint(color)) {
                canvas.drawRRect(rrect(x, y, width, height, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius), paint);
            }
        });
    }

    public static void drawOutlineRoundedRect(float x, float y, float width, float height, float radius, float strokeWidth, int color) {
        if (!canStroke(width, height, strokeWidth, color)) {
            return;
        }

        draw(canvas -> {
            try (Paint paint = strokePaint(color, strokeWidth)) {
                canvas.drawRRect(rrect(x, y, width, height, radius), paint);
            }
        });
    }

    public static void drawCircle(float centerX, float centerY, float radius, int color) {
        if (radius <= 0.0F || isTransparent(color)) {
            return;
        }

        draw(canvas -> {
            try (Paint paint = fillPaint(color)) {
                canvas.drawCircle(centerX, centerY, radius, paint);
            }
        });
    }

    public static void drawOutlineCircle(float centerX, float centerY, float radius, float strokeWidth, int color) {
        if (radius <= 0.0F || strokeWidth <= 0.0F || isTransparent(color)) {
            return;
        }

        draw(canvas -> {
            try (Paint paint = strokePaint(color, strokeWidth)) {
                canvas.drawCircle(centerX, centerY, radius, paint);
            }
        });
    }

    public static void drawOval(float x, float y, float width, float height, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        draw(canvas -> {
            try (Paint paint = fillPaint(color)) {
                canvas.drawOval(rect(x, y, width, height), paint);
            }
        });
    }

    public static void drawLine(float startX, float startY, float endX, float endY, float strokeWidth, int color) {
        if (strokeWidth <= 0.0F || isTransparent(color)) {
            return;
        }

        draw(canvas -> {
            try (Paint paint = strokePaint(color, strokeWidth)) {
                paint.setStrokeCap(PaintStrokeCap.ROUND);
                canvas.drawLine(startX, startY, endX, endY, paint);
            }
        });
    }

    public static void drawArc(float x, float y, float width, float height, float startAngle, float sweepAngle, float strokeWidth, int color) {
        if (!canStroke(width, height, strokeWidth, color)) {
            return;
        }

        draw(canvas -> {
            try (Paint paint = strokePaint(color, strokeWidth)) {
                paint.setStrokeCap(PaintStrokeCap.ROUND);
                canvas.drawArc(x, y, x + width, y + height, startAngle, sweepAngle, false, paint);
            }
        });
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

        draw(canvas -> {
            try (Shader shader = Shader.makeLinearGradient(startX, startY, endX, endY, colors, positions, GradientStyle.DEFAULT);
                 Paint paint = fillPaint(0xFFFFFFFF)) {
                paint.setShader(shader);
                canvas.drawRect(rect(x, y, width, height), paint);
            }
        });
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

        draw(canvas -> {
            try (Shader shader = Shader.makeLinearGradient(startX, startY, endX, endY, colors, positions, GradientStyle.DEFAULT);
                 Paint paint = fillPaint(0xFFFFFFFF)) {
                paint.setShader(shader);
                canvas.drawRRect(rrect(x, y, width, height, radius), paint);
            }
        });
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

        draw(canvas -> {
            try (Shader shader = Shader.makeRadialGradient(centerX, centerY, radius, colors, positions, GradientStyle.DEFAULT);
                 Paint paint = fillPaint(0xFFFFFFFF)) {
                paint.setShader(shader);
                canvas.drawCircle(centerX, centerY, radius, paint);
            }
        });
    }

    public static void drawDropShadow(float x, float y, float width, float height, float radius, float offsetX, float offsetY,
                                      float blurRadius, int color) {
        if (!canDraw(width, height, color) || blurRadius <= 0.0F) {
            return;
        }

        draw(canvas -> {
            try (ImageFilter filter = ImageFilter.makeDropShadowOnly(offsetX, offsetY, blurRadius, blurRadius, color);
                 Paint paint = fillPaint(0xFFFFFFFF)) {
                paint.setImageFilter(filter);
                canvas.drawRRect(rrect(x, y, width, height, radius), paint);
            }
        });
    }

    public static void drawBlurredRect(float x, float y, float width, float height, float radius, float blurRadius, int color) {
        if (!canDraw(width, height, color)) {
            return;
        }

        if (blurRadius <= 0.0F) {
            drawRoundedRect(x, y, width, height, radius, color);
            return;
        }

        draw(canvas -> {
            try (ImageFilter filter = ImageFilter.makeBlur(blurRadius, blurRadius, FilterTileMode.DECAL);
                 Paint paint = fillPaint(color)) {
                paint.setImageFilter(filter);
                canvas.drawRRect(rrect(x, y, width, height, radius), paint);
            }
        });
    }

    public static void drawText(String text, float x, float y, float size, int color) {
        if (!canDrawText(text, size, color)) {
            return;
        }

        try (Font font = font(size)) {
            drawText(font, text, x, y, color);
        }
    }

    public static void drawText(Font font, String text, float x, float y, int color) {
        Objects.requireNonNull(font, "font");
        if (!canDrawText(text, font.getSize(), color)) {
            return;
        }

        FontMetrics metrics = font.getMetrics();
        drawTextBaseline(font, text, x, y - metrics.getAscent(), color);
    }

    public static void drawTextBaseline(String text, float x, float baselineY, float size, int color) {
        if (!canDrawText(text, size, color)) {
            return;
        }

        try (Font font = font(size)) {
            drawTextBaseline(font, text, x, baselineY, color);
        }
    }

    public static void drawTextBaseline(Font font, String text, float x, float baselineY, int color) {
        Objects.requireNonNull(font, "font");
        if (!canDrawText(text, font.getSize(), color)) {
            return;
        }

        draw(canvas -> {
            try (Paint paint = fillPaint(color)) {
                canvas.drawString(text, x, baselineY, font, paint);
            }
        });
    }

    public static void drawCenteredText(String text, float centerX, float y, float size, int color) {
        if (!canDrawText(text, size, color)) {
            return;
        }

        try (Font font = font(size)) {
            drawText(font, text, centerX - font.measureTextWidth(text) * 0.5F, y, color);
        }
    }

    public static void drawCenteredTextInRect(String text, float x, float y, float width, float height, float size, int color) {
        if (!canDraw(width, height, color) || !canDrawText(text, size, color)) {
            return;
        }

        try (Font font = font(size)) {
            FontMetrics metrics = font.getMetrics();
            float textX = x + (width - font.measureTextWidth(text)) * 0.5F;
            float baselineY = y + (height - metrics.getHeight()) * 0.5F - metrics.getAscent();
            drawTextBaseline(font, text, textX, baselineY, color);
        }
    }

    public static float getTextWidth(String text, float size) {
        if (text == null || text.isEmpty() || size <= 0.0F) {
            return 0.0F;
        }

        try (Font font = font(size)) {
            return font.measureTextWidth(text);
        }
    }

    public static float getTextHeight(float size) {
        if (size <= 0.0F) {
            return 0.0F;
        }

        try (Font font = font(size)) {
            return font.getMetrics().getHeight();
        }
    }

    public static void withClip(float x, float y, float width, float height, Runnable action) {
        Objects.requireNonNull(action, "action");
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }

        draw(canvas -> {
            int saveCount = canvas.save();
            try {
                canvas.clipRect(rect(x, y, width, height), ClipMode.INTERSECT, true);
                action.run();
            } finally {
                canvas.restoreToCount(saveCount);
            }
        });
    }

    public static void withRoundedClip(float x, float y, float width, float height, float radius, Runnable action) {
        Objects.requireNonNull(action, "action");
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }

        draw(canvas -> {
            int saveCount = canvas.save();
            try {
                canvas.clipRRect(rrect(x, y, width, height, radius), ClipMode.INTERSECT, true);
                action.run();
            } finally {
                canvas.restoreToCount(saveCount);
            }
        });
    }

    public static void withTranslation(float x, float y, Runnable action) {
        Objects.requireNonNull(action, "action");
        draw(canvas -> {
            int saveCount = canvas.save();
            try {
                canvas.translate(x, y);
                action.run();
            } finally {
                canvas.restoreToCount(saveCount);
            }
        });
    }

    public static void withScale(float scaleX, float scaleY, float pivotX, float pivotY, Runnable action) {
        Objects.requireNonNull(action, "action");
        draw(canvas -> {
            int saveCount = canvas.save();
            try {
                canvas.translate(pivotX, pivotY);
                canvas.scale(scaleX, scaleY);
                canvas.translate(-pivotX, -pivotY);
                action.run();
            } finally {
                canvas.restoreToCount(saveCount);
            }
        });
    }

    public static void withRotation(float degrees, float pivotX, float pivotY, Runnable action) {
        Objects.requireNonNull(action, "action");
        draw(canvas -> {
            int saveCount = canvas.save();
            try {
                canvas.translate(pivotX, pivotY);
                canvas.rotate(degrees);
                canvas.translate(-pivotX, -pivotY);
                action.run();
            } finally {
                canvas.restoreToCount(saveCount);
            }
        });
    }

    private static void draw(Consumer<Canvas> renderer) {
        Objects.requireNonNull(renderer, "renderer");

        Canvas currentCanvas = CURRENT_CANVAS.get();
        if (currentCanvas != null) {
            renderer.accept(currentCanvas);
            return;
        }

        SkiaUtility.draw(canvas -> {
            CURRENT_CANVAS.set(canvas);
            try {
                renderer.accept(canvas);
            } finally {
                CURRENT_CANVAS.remove();
            }
        });
    }

    private static Paint fillPaint(int color) {
        return new Paint()
            .setAntiAlias(true)
            .setDither(true)
            .setMode(PaintMode.FILL)
            .setColor(color);
    }

    private static Paint strokePaint(int color, float strokeWidth) {
        return new Paint()
            .setAntiAlias(true)
            .setDither(true)
            .setMode(PaintMode.STROKE)
            .setStrokeWidth(strokeWidth)
            .setStrokeJoin(PaintStrokeJoin.ROUND)
            .setStrokeCap(PaintStrokeCap.BUTT)
            .setColor(color);
    }

    private static Font font(float size) {
        Font font = new Font();
        font.setSize(size);
        font.setSubpixel(true);
        font.setEdging(FontEdging.SUBPIXEL_ANTI_ALIAS);
        return font;
    }

    private static Rect rect(float x, float y, float width, float height) {
        return Rect.makeXYWH(x, y, width, height);
    }

    private static RRect rrect(float x, float y, float width, float height, float radius) {
        return RRect.makeXYWH(x, y, width, height, clampRadius(width, height, radius));
    }

    private static RRect rrect(float x, float y, float width, float height, float topLeftRadius, float topRightRadius,
                               float bottomRightRadius, float bottomLeftRadius) {
        return RRect.makeXYWH(
            x,
            y,
            width,
            height,
            clampRadius(width, height, topLeftRadius),
            clampRadius(width, height, topRightRadius),
            clampRadius(width, height, bottomRightRadius),
            clampRadius(width, height, bottomLeftRadius)
        );
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
}
