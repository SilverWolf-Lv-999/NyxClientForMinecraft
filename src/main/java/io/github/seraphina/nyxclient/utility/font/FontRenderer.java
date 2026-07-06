package io.github.seraphina.nyxclient.utility.font;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import io.github.seraphina.nyxclient.manager.FontManager;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.utility.render.GL;
import io.github.seraphina.nyxclient.utility.render.Shader;
import io.github.seraphina.nyxclient.utility.render.Shaders;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FontRenderer implements AutoCloseable {
    private static final int INITIAL_ATLAS_SIZE = 1024;
    private static final int MAX_ATLAS_SIZE = 4096;
    private static final int GLYPH_PADDING = 2;
    private static final int FLOATS_PER_VERTEX = 8;

    private static int vao;
    private static int vbo;

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
    private int textureId;
    private boolean atlasDirty = true;
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

        Render2DUtility.withOpenGL(() -> renderNow(text, x, y, color));
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
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
            textureId = 0;
        }
        if (atlasGraphics != null) {
            atlasGraphics.dispose();
            atlasGraphics = null;
        }
        atlasImage = null;
        glyphs.clear();
    }

    public static void closeSharedResources() {
        if (vbo != 0) {
            GL.deleteBuffer(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            GL.deleteVertexArray(vao);
            vao = 0;
        }
    }

    private synchronized void renderNow(String text, float x, float y, int color) {
        if (closed || atlasImage == null) {
            return;
        }

        float[] vertices = buildVertices(text, x, y, color);
        if (vertices.length == 0) {
            return;
        }

        ensureSharedResources();
        ensureTexture();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        Window window = minecraft.getWindow();
        float guiWidth = Math.max(1, window.getGuiScaledWidth());
        float guiHeight = Math.max(1, window.getGuiScaledHeight());
        if (Shaders.FONT == null) {
            Shaders.init();
        }

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(textureId);
        Shader shader = Shaders.FONT;
        shader.bind();
        shader.set("ScreenSize", guiWidth, guiHeight);
        shader.set("FontTexture", 0);
        GL.bindVertexArray(vao);
        GL.bindVertexBuffer(vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertices.length / FLOATS_PER_VERTEX);
        GL.bindVertexBuffer(0);
        GL.bindVertexArray(0);
    }

    private float[] buildVertices(String text, float x, float y, int color) {
        int vertexFloatCount = visibleGlyphCount(text) * 6 * FLOATS_PER_VERTEX;
        if (vertexFloatCount == 0) {
            return new float[0];
        }

        float[] vertices = new float[vertexFloatCount];
        int index = 0;
        float cursorX = x;
        float baselineY = y + ascent;

        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (codePoint == '\n') {
                cursorX = x;
                baselineY += lineHeight;
                continue;
            }
            if (codePoint == '\r') {
                continue;
            }
            if (codePoint == '\t') {
                cursorX += glyph(' ').advance * 4.0F;
                continue;
            }

            Glyph glyph = glyph(codePoint);
            if (glyph.visible) {
                float x0 = cursorX + glyph.xOffset;
                float y0 = baselineY + glyph.yOffset;
                float x1 = x0 + glyph.width;
                float y1 = y0 + glyph.height;
                float u0 = glyph.textureX / (float)atlasSize;
                float v0 = glyph.textureY / (float)atlasSize;
                float u1 = (glyph.textureX + glyph.width) / (float)atlasSize;
                float v1 = (glyph.textureY + glyph.height) / (float)atlasSize;

                index = addVertex(vertices, index, x0, y0, red, green, blue, alpha, u0, v0);
                index = addVertex(vertices, index, x1, y0, red, green, blue, alpha, u1, v0);
                index = addVertex(vertices, index, x1, y1, red, green, blue, alpha, u1, v1);
                index = addVertex(vertices, index, x1, y1, red, green, blue, alpha, u1, v1);
                index = addVertex(vertices, index, x0, y1, red, green, blue, alpha, u0, v1);
                index = addVertex(vertices, index, x0, y0, red, green, blue, alpha, u0, v0);
            }

            cursorX += glyph.advance;
        }

        if (index == vertices.length) {
            return vertices;
        }

        float[] usedVertices = new float[index];
        System.arraycopy(vertices, 0, usedVertices, 0, index);
        return usedVertices;
    }

    private int visibleGlyphCount(String text) {
        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
                continue;
            }
            if (glyph(codePoint).visible) {
                count++;
            }
        }
        return count;
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
        atlasDirty = true;

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
        atlasDirty = true;
        return true;
    }

    private void ensureTexture() {
        if (textureId == 0) {
            textureId = GL11.glGenTextures();
            GlStateManager._bindTexture(textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            atlasDirty = true;
        }

        if (!atlasDirty) {
            return;
        }

        GlStateManager._bindTexture(textureId);
        ByteBuffer pixels = imageToRgbaBuffer(atlasImage);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, atlasSize, atlasSize, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        atlasDirty = false;
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

    private static ByteBuffer imageToRgbaBuffer(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argbPixels = new int[width * height];
        image.getRGB(0, 0, width, height, argbPixels, 0, width);

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int argb : argbPixels) {
            buffer.put((byte)((argb >>> 16) & 0xFF));
            buffer.put((byte)((argb >>> 8) & 0xFF));
            buffer.put((byte)(argb & 0xFF));
            buffer.put((byte)((argb >>> 24) & 0xFF));
        }
        buffer.flip();
        return buffer;
    }

    private static boolean canRender(String text, int color) {
        return text != null && !text.isEmpty() && ((color >>> 24) & 0xFF) != 0;
    }

    private static int addVertex(float[] vertices, int index, float x, float y, float red, float green, float blue, float alpha, float u, float v) {
        vertices[index++] = x;
        vertices[index++] = y;
        vertices[index++] = red;
        vertices[index++] = green;
        vertices[index++] = blue;
        vertices[index++] = alpha;
        vertices[index++] = u;
        vertices[index++] = v;
        return index;
    }

    private static void ensureSharedResources() {
        if (vao != 0 && vbo != 0) {
            return;
        }

        vao = GL.genVertexArray();
        vbo = GL.genBuffer();
        GL.bindVertexArray(vao);
        GL.bindVertexBuffer(vbo);
        GL.enableVertexAttribute(0);
        GL.vertexAttribute(0, 2, GL11.GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 0L);
        GL.enableVertexAttribute(1);
        GL.vertexAttribute(1, 4, GL11.GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 2L * Float.BYTES);
        GL.enableVertexAttribute(2);
        GL.vertexAttribute(2, 2, GL11.GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 6L * Float.BYTES);
        GL.bindVertexBuffer(0);
        GL.bindVertexArray(0);
    }

    private record Glyph(int textureX, int textureY, int width, int height, float xOffset, float yOffset, float advance, boolean visible) {
    }

    private record GlyphSlot(int x, int y) {
    }
}
