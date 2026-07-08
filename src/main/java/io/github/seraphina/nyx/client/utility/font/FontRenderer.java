package io.github.seraphina.nyx.client.utility.font;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix3x2f;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class FontRenderer implements AutoCloseable {
    private static final int INITIAL_ATLAS_SIZE = 1024;
    private static final int MAX_ATLAS_SIZE = 4096;
    private static final int GLYPH_PADDING = 2;
    private static final float ANTIALIAS_RASTER_SCALE = 2.0F;
    private static final Color TRANSPARENT_WHITE = new Color(255, 255, 255, 0);
    private static final AtomicInteger TEXTURE_IDS = new AtomicInteger();

    private final Font javaFont;
    private final Font rasterFont;
    private final boolean antialias;
    private final FontRenderContext fontRenderContext;
    private final FontRenderContext rasterFontRenderContext;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final float rasterScale;
    private final int rasterPadding;

    private BufferedImage atlasImage;
    private Graphics2D atlasGraphics;
    private int atlasSize;
    private int nextX = 1;
    private int nextY = 1;
    private int rowHeight;
    private boolean closed;
    private DynamicTexture texture;
    private int textureAtlasSize;
    private boolean textureDirty = true;
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
        this.rasterFontRenderContext = new FontRenderContext(null, antialias, true);
        this.rasterScale = antialias ? ANTIALIAS_RASTER_SCALE : 1.0F;
        this.rasterPadding = Math.max(1, Math.round(GLYPH_PADDING * this.rasterScale));
        this.rasterFont = this.javaFont.deriveFont(this.javaFont.getSize2D() * this.rasterScale);
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

        GuiGraphics graphics = Render2DUtility.currentGuiGraphics();
        TextLayout layout;
        synchronized (this) {
            if (closed) {
                return;
            }

            layout = layout(text, x, y);
            if (layout.quads().length == 0) {
                return;
            }

            ensureTexture();
            Render2DUtility.VertexProjector projector = Render2DUtility.currentVertexProjector();
            graphics.submitGuiElementRenderState(new FontTextRenderState(
                TextureSetup.singleTexture(texture.getTextureView(), fontSampler()),
                new Matrix3x2f(graphics.pose()),
                layout.quads(),
                color,
                graphics.peekScissorStack(),
                layout.bounds(),
                projector
            ));
        }
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
        if (texture != null) {
            texture.close();
            texture = null;
        }
        atlasImage = null;
        glyphs.clear();
        textureAtlasSize = 0;
        textureDirty = false;
    }

    public static void closeSharedResources() {
    }

    private Glyph glyph(int codePoint) {
        return glyphs.computeIfAbsent(codePoint, this::createGlyph);
    }

    private TextLayout layout(String text, float x, float y) {
        GlyphDraw[] draws = new GlyphDraw[Math.min(text.length(), 256)];
        int drawCount = 0;
        float cursorX = 0.0F;
        float lineY = 0.0F;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (codePoint == '\n') {
                cursorX = 0.0F;
                lineY += lineHeight;
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
                float x0 = x + cursorX + glyph.xOffset;
                float y0 = y + lineY + ascent + glyph.yOffset;
                float x1 = x0 + glyph.width;
                float y1 = y0 + glyph.height;

                if (drawCount == draws.length) {
                    GlyphDraw[] next = new GlyphDraw[draws.length * 2];
                    System.arraycopy(draws, 0, next, 0, draws.length);
                    draws = next;
                }
                draws[drawCount++] = new GlyphDraw(glyph, x0, y0, x1, y1);

                minX = Math.min(minX, x0);
                minY = Math.min(minY, y0);
                maxX = Math.max(maxX, x1);
                maxY = Math.max(maxY, y1);
            }

            cursorX += glyph.advance;
        }

        GlyphQuad[] visibleQuads = new GlyphQuad[drawCount];
        for (int i = 0; i < drawCount; i++) {
            GlyphDraw draw = draws[i];
            Glyph glyph = draw.glyph;
            visibleQuads[i] = new GlyphQuad(
                draw.x0,
                draw.y0,
                draw.x1,
                draw.y1,
                glyph.textureX / (float)atlasSize,
                glyph.textureY / (float)atlasSize,
                (glyph.textureX + glyph.textureWidth) / (float)atlasSize,
                (glyph.textureY + glyph.textureHeight) / (float)atlasSize
            );
        }

        Bounds bounds = drawCount == 0 ? null : new Bounds(minX, minY, maxX, maxY);
        return new TextLayout(visibleQuads, bounds);
    }

    private Glyph createGlyph(int codePoint) {
        String value = new String(Character.toChars(codePoint));
        GlyphVector logicalVector = javaFont.createGlyphVector(fontRenderContext, value);
        GlyphMetrics metrics = logicalVector.getGlyphMetrics(0);
        float advance = Math.max(0.0F, metrics.getAdvanceX());
        GlyphVector rasterVector = rasterFont.createGlyphVector(rasterFontRenderContext, value);
        Rectangle rasterBounds = rasterVector.getPixelBounds(rasterFontRenderContext, 0.0F, 0.0F);

        if (rasterBounds.width <= 0 || rasterBounds.height <= 0 || Character.isWhitespace(codePoint)) {
            return new Glyph(0, 0, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F, advance, false);
        }

        int textureWidth = rasterBounds.width + rasterPadding * 2;
        int textureHeight = rasterBounds.height + rasterPadding * 2;
        GlyphSlot slot = allocateGlyphSlot(textureWidth, textureHeight);
        if (slot == null) {
            return new Glyph(0, 0, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F, advance, false);
        }

        atlasGraphics.setFont(rasterFont);
        atlasGraphics.setColor(Color.WHITE);
        atlasGraphics.drawGlyphVector(rasterVector, slot.x + rasterPadding - rasterBounds.x, slot.y + rasterPadding - rasterBounds.y);
        textureDirty = true;

        return new Glyph(
            slot.x,
            slot.y,
            textureWidth,
            textureHeight,
            (rasterBounds.x - rasterPadding) / rasterScale,
            (rasterBounds.y - rasterPadding) / rasterScale,
            textureWidth / rasterScale,
            textureHeight / rasterScale,
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
        textureDirty = true;
        return true;
    }

    private void ensureTexture() {
        if (texture != null && textureAtlasSize == atlasSize && !textureDirty) {
            return;
        }

        if (texture == null || textureAtlasSize != atlasSize) {
            if (texture != null) {
                texture.close();
            }
            texture = new DynamicTexture(() -> "nyx-font-atlas-" + TEXTURE_IDS.incrementAndGet(), toNativeImage(atlasImage));
            textureAtlasSize = atlasSize;
            textureDirty = false;
            return;
        }

        texture.setPixels(toNativeImage(atlasImage));
        texture.upload();
        textureDirty = false;
    }

    private static com.mojang.blaze3d.platform.NativeImage toNativeImage(BufferedImage image) {
        com.mojang.blaze3d.platform.NativeImage nativeImage = new com.mojang.blaze3d.platform.NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                nativeImage.setPixel(x, y, image.getRGB(x, y));
            }
        }
        return nativeImage;
    }

    private static BufferedImage createAtlasImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setColor(TRANSPARENT_WHITE);
        graphics.fillRect(0, 0, size, size);
        graphics.dispose();
        return image;
    }

    private Graphics2D createAtlasGraphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setFont(rasterFont);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, antialias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return graphics;
    }

    private GpuSampler fontSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(antialias ? FilterMode.LINEAR : FilterMode.NEAREST);
    }

    private static boolean canRender(String text, int color) {
        return text != null && !text.isEmpty() && ((color >>> 24) & 0xFF) != 0;
    }

    private static ScreenRectangle boundsFor(Bounds bounds, Matrix3x2f pose, ScreenRectangle scissorArea,
                                             Render2DUtility.VertexProjector projector) {
        return Render2DUtility.boundsForProjectedRect(
            bounds.minX,
            bounds.minY,
            bounds.maxX,
            bounds.maxY,
            pose,
            projector,
            scissorArea
        );
    }

    private static int floor(float value) {
        return (int)Math.floor(value);
    }

    private static int ceil(float value) {
        return (int)Math.ceil(value);
    }

    private record Glyph(int textureX, int textureY, int textureWidth, int textureHeight, float xOffset, float yOffset, float width, float height,
                         float advance, boolean visible) {
    }

    private record GlyphSlot(int x, int y) {
    }

    private record GlyphDraw(Glyph glyph, float x0, float y0, float x1, float y1) {
    }

    private record GlyphQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
    }

    private record Bounds(float minX, float minY, float maxX, float maxY) {
    }

    private record TextLayout(GlyphQuad[] quads, Bounds bounds) {
    }

    private record FontTextRenderState(
        TextureSetup textureSetup,
        Matrix3x2f pose,
        GlyphQuad[] quads,
        int color,
        ScreenRectangle scissorArea,
        Render2DUtility.VertexProjector projector,
        ScreenRectangle bounds
    ) implements GuiElementRenderState {
        private FontTextRenderState(TextureSetup textureSetup, Matrix3x2f pose, GlyphQuad[] quads, int color,
                                    ScreenRectangle scissorArea, Bounds bounds, Render2DUtility.VertexProjector projector) {
            this(
                textureSetup,
                pose,
                quads,
                color,
                scissorArea,
                projector,
                bounds == null ? null : boundsFor(bounds, pose, scissorArea, projector)
            );
        }

        @Override
        public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return RenderPipelines.GUI_TEXTURED;
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (GlyphQuad quad : quads) {
                Render2DUtility.addVertexWithProjection(consumer, pose, projector, quad.x0, quad.y0).setUv(quad.u0, quad.v0).setColor(color);
                Render2DUtility.addVertexWithProjection(consumer, pose, projector, quad.x0, quad.y1).setUv(quad.u0, quad.v1).setColor(color);
                Render2DUtility.addVertexWithProjection(consumer, pose, projector, quad.x1, quad.y1).setUv(quad.u1, quad.v1).setColor(color);
                Render2DUtility.addVertexWithProjection(consumer, pose, projector, quad.x1, quad.y0).setUv(quad.u1, quad.v0).setColor(color);
            }
        }
    }
}
