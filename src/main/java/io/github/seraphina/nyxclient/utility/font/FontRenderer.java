package io.github.seraphina.nyxclient.utility.font;

import io.github.seraphina.nyxclient.manager.FontManager;
import io.github.seraphina.nyxclient.utility.Render2DUtility;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FontRenderer implements AutoCloseable {
    private static final int INITIAL_ATLAS_SIZE = 1024;
    private static final int MAX_ATLAS_SIZE = 4096;
    private static final int GLYPH_PADDING = 2;

    private final Font javaFont;
    private final boolean antialias;
    private final FontRenderContext fontRenderContext;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();

    private BufferedImage atlasImage;
    private Graphics2D atlasGraphics;
    private int atlasSize;
    private int nextX = 1;
    private int nextY = 1;
    private int rowHeight;
    private boolean closed;
    private final float ascent;
    private final float descent;
    private final float lineHeight;

    public FontRenderer(Font javaFont) {
        this(javaFont, true);
    }

    public FontRenderer(Font javaFont, boolean antialias) {
        this.javaFont = Objects.requireNonNull(javaFont, "javaFont");
        this.antialias = antialias;
        this.fontRenderContext = new FontRenderContext(null, antialias, true);
        this.atlasSize = INITIAL_ATLAS_SIZE;
        this.atlasImage = createAtlasImage(this.atlasSize);
        this.atlasGraphics = createAtlasGraphics(this.atlasImage);

        FontMetrics metrics = this.atlasGraphics.getFontMetrics(this.javaFont);
        this.ascent = metrics.getAscent();
        this.descent = metrics.getDescent();
        this.lineHeight = metrics.getHeight();
    }

    public static void renderFont(String text, float x, float y, Color color) {
        FontManager.getDefaultRenderer().drawString(text, x, y, color);
    }

    public static void renderFont(String text, float x, float y, int color) {
        FontManager.getDefaultRenderer().drawString(text, x, y, color);
    }

    public Font getJavaFont() {
        return javaFont;
    }

    public float getAscent() {
        return ascent;
    }

    public float getDescent() {
        return descent;
    }

    public float getLineHeight() {
        return lineHeight;
    }

    public void drawString(String text, float x, float y, Color color) {
        Objects.requireNonNull(color, "color");
        drawString(text, x, y, color.getRGB());
    }

    public void drawString(String text, float x, float y, int color) {
        if (!canRender(text, color)) {
            return;
        }

        Render2DUtility.drawText(text, x, y, lineHeight, color);
    }

    public void drawCenteredString(String text, float centerX, float y, int color) {
        drawString(text, centerX - getStringWidth(text) * 0.5F, y, color);
    }

    public void drawStringBaseline(String text, float x, float baselineY, int color) {
        drawString(text, x, baselineY - ascent, color);
    }

    public synchronized float getStringWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }

        float lineWidth = 0.0F;
        float maxWidth = 0.0F;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (codePoint == '\n') {
                maxWidth = Math.max(maxWidth, lineWidth);
                lineWidth = 0.0F;
                continue;
            }
            if (codePoint == '\r') {
                continue;
            }
            if (codePoint == '\t') {
                lineWidth += glyph(' ').advance * 4.0F;
                continue;
            }

            lineWidth += glyph(codePoint).advance;
        }

        return Math.max(maxWidth, lineWidth);
    }

    public float getStringHeight(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }

        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines * lineHeight;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (atlasGraphics != null) {
            atlasGraphics.dispose();
            atlasGraphics = null;
        }
        atlasImage = null;
        glyphs.clear();
    }

    public static void closeSharedResources() {
    }

    private Glyph glyph(int codePoint) {
        return glyphs.computeIfAbsent(codePoint, this::createGlyph);
    }

    private Glyph createGlyph(int codePoint) {
        String value = new String(Character.toChars(codePoint));
        GlyphVector vector = javaFont.createGlyphVector(fontRenderContext, value);
        java.awt.Rectangle bounds = vector.getPixelBounds(fontRenderContext, 0.0F, 0.0F);
        GlyphMetrics metrics = vector.getGlyphMetrics(0);
        float advance = Math.max(0.0F, metrics.getAdvanceX());

        if (bounds.width <= 0 || bounds.height <= 0 || Character.isWhitespace(codePoint)) {
            return new Glyph(0, 0, 0, 0, 0.0F, 0.0F, advance, false);
        }

        int width = bounds.width + GLYPH_PADDING * 2;
        int height = bounds.height + GLYPH_PADDING * 2;
        GlyphSlot slot = allocateGlyphSlot(width, height);
        if (slot == null) {
            return new Glyph(0, 0, 0, 0, 0.0F, 0.0F, advance, false);
        }

        atlasGraphics.setFont(javaFont);
        atlasGraphics.setColor(Color.WHITE);
        atlasGraphics.drawGlyphVector(vector, slot.x + GLYPH_PADDING - bounds.x, slot.y + GLYPH_PADDING - bounds.y);

        return new Glyph(
            slot.x,
            slot.y,
            width,
            height,
            bounds.x - GLYPH_PADDING,
            bounds.y - GLYPH_PADDING,
            advance,
            true
        );
    }

    private GlyphSlot allocateGlyphSlot(int width, int height) {
        if (width >= atlasSize || height >= atlasSize) {
            return null;
        }

        if (nextX + width + 1 > atlasSize) {
            nextX = 1;
            nextY += rowHeight + 1;
            rowHeight = 0;
        }

        if (nextY + height + 1 > atlasSize && growAtlas()) {
            return allocateGlyphSlot(width, height);
        }

        if (nextY + height + 1 > atlasSize) {
            return null;
        }

        GlyphSlot slot = new GlyphSlot(nextX, nextY);
        nextX += width + 1;
        rowHeight = Math.max(rowHeight, height);
        return slot;
    }

    private boolean growAtlas() {
        if (atlasSize >= MAX_ATLAS_SIZE) {
            return false;
        }

        int nextSize = Math.min(MAX_ATLAS_SIZE, atlasSize * 2);
        BufferedImage nextImage = createAtlasImage(nextSize);
        Graphics2D nextGraphics = createAtlasGraphics(nextImage);
        nextGraphics.drawImage(atlasImage, 0, 0, null);

        atlasGraphics.dispose();
        atlasImage = nextImage;
        atlasGraphics = nextGraphics;
        atlasSize = nextSize;
        return true;
    }

    private static BufferedImage createAtlasImage(int size) {
        return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    }

    private Graphics2D createAtlasGraphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setFont(javaFont);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, antialias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return graphics;
    }

    private static boolean canRender(String text, int color) {
        return text != null && !text.isEmpty() && ((color >>> 24) & 0xFF) != 0;
    }

    private record Glyph(int textureX, int textureY, int width, int height, float xOffset, float yOffset, float advance, boolean visible) {
    }

    private record GlyphSlot(int x, int y) {
    }
}
