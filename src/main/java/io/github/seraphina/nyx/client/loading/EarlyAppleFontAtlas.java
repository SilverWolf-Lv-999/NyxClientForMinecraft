package io.github.seraphina.nyx.client.loading;

import net.neoforged.fml.earlydisplay.render.SimpleFont;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL32C;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Locale;

final class EarlyAppleFontAtlas {
    private static final String[] APPLE_FONT_FAMILIES = {
        "SF Pro Display",
        "SF Pro Text",
        "San Francisco Display",
        "San Francisco Text",
        "PingFang SC",
        "Microsoft YaHei UI",
        "Microsoft YaHei",
        "Inter",
        "Segoe UI Variable Display",
        "Segoe UI",
        "Arial"
    };
    private static final int FIRST_GLYPH = 32;
    private static final int LAST_GLYPH = 126;
    private static final int GLYPH_COUNT = LAST_GLYPH - FIRST_GLYPH + 1;
    private static final int ATLAS_SIZE = 1024;
    private static final int GLYPH_PADDING = 3;

    private EarlyAppleFontAtlas() {
    }

    static SimpleFont create(float size) {
        Font font = preferredFont().deriveFont(Font.PLAIN, size);
        BufferedImage atlas = createAtlasImage();
        Graphics2D graphics = createGraphics(atlas, font);
        FontRenderContext context = graphics.getFontRenderContext();
        FontMetrics metrics = graphics.getFontMetrics(font);
        int ascent = metrics.getAscent();
        int descent = metrics.getDescent();
        int lineSpacing = metrics.getHeight();
        SimpleFont.Glyph[] glyphs = new SimpleFont.Glyph[GLYPH_COUNT];

        int nextX = 1;
        int nextY = 1;
        int rowHeight = 0;
        for (int codePoint = FIRST_GLYPH; codePoint <= LAST_GLYPH; codePoint++) {
            String text = Character.toString(codePoint);
            GlyphVector vector = font.createGlyphVector(context, text);
            GlyphMetrics glyphMetrics = vector.getGlyphMetrics(0);
            int advance = Math.max(1, Math.round(glyphMetrics.getAdvanceX()));
            Rectangle bounds = vector.getPixelBounds(context, 0.0F, 0.0F);

            if (Character.isWhitespace(codePoint) || bounds.width <= 0 || bounds.height <= 0) {
                glyphs[codePoint - FIRST_GLYPH] = new SimpleFont.Glyph((char)codePoint, advance, new int[] { 0, 0, 0, 0 }, new float[] { 0.0F, 0.0F, 0.0F, 0.0F });
                continue;
            }

            int textureWidth = bounds.width + GLYPH_PADDING * 2;
            int textureHeight = bounds.height + GLYPH_PADDING * 2;
            if (nextX + textureWidth + 1 > ATLAS_SIZE) {
                nextX = 1;
                nextY += rowHeight + 1;
                rowHeight = 0;
            }
            if (nextY + textureHeight + 1 > ATLAS_SIZE) {
                glyphs[codePoint - FIRST_GLYPH] = new SimpleFont.Glyph((char)codePoint, advance, new int[] { 0, 0, 0, 0 }, new float[] { 0.0F, 0.0F, 0.0F, 0.0F });
                continue;
            }

            graphics.drawGlyphVector(vector, nextX + GLYPH_PADDING - bounds.x, nextY + GLYPH_PADDING - bounds.y);
            int x0 = bounds.x - GLYPH_PADDING;
            int y0 = ascent + bounds.y - GLYPH_PADDING;
            int x1 = x0 + textureWidth;
            int y1 = y0 + textureHeight;
            float u0 = nextX / (float)ATLAS_SIZE;
            float v0 = nextY / (float)ATLAS_SIZE;
            float u1 = (nextX + textureWidth) / (float)ATLAS_SIZE;
            float v1 = (nextY + textureHeight) / (float)ATLAS_SIZE;
            glyphs[codePoint - FIRST_GLYPH] = new SimpleFont.Glyph((char)codePoint, advance, new int[] { x0, y0, x1, y1 }, new float[] { u0, v0, u1, v1 });

            nextX += textureWidth + 1;
            rowHeight = Math.max(rowHeight, textureHeight);
        }
        graphics.dispose();

        int textureId = createTexture(alphaBuffer(atlas));
        return new SimpleFont(lineSpacing, -descent, textureId, codePoint -> {
            if (codePoint < FIRST_GLYPH || codePoint > LAST_GLYPH) {
                return null;
            }
            return glyphs[codePoint - FIRST_GLYPH];
        });
    }

    private static Font preferredFont() {
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] families = environment.getAvailableFontFamilyNames(Locale.ROOT);
        for (String preferred : APPLE_FONT_FAMILIES) {
            for (String family : families) {
                if (family.equalsIgnoreCase(preferred)) {
                    return new Font(family, Font.PLAIN, 18);
                }
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 18);
    }

    private static BufferedImage createAtlasImage() {
        BufferedImage image = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setColor(new Color(255, 255, 255, 0));
        graphics.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE);
        graphics.dispose();
        return image;
    }

    private static Graphics2D createGraphics(BufferedImage image, Font font) {
        Graphics2D graphics = image.createGraphics();
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return graphics;
    }

    private static ByteBuffer alphaBuffer(BufferedImage image) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                buffer.put((byte)((image.getRGB(x, y) >>> 24) & 0xFF));
            }
        }
        buffer.flip();
        return buffer;
    }

    private static int createTexture(ByteBuffer pixels) {
        int textureId = GL32C.glGenTextures();
        GL32C.glBindTexture(GL32C.GL_TEXTURE_2D, textureId);
        GL32C.glTexParameteri(GL32C.GL_TEXTURE_2D, GL32C.GL_TEXTURE_WRAP_S, GL32C.GL_CLAMP_TO_EDGE);
        GL32C.glTexParameteri(GL32C.GL_TEXTURE_2D, GL32C.GL_TEXTURE_WRAP_T, GL32C.GL_CLAMP_TO_EDGE);
        GL32C.glTexParameteri(GL32C.GL_TEXTURE_2D, GL32C.GL_TEXTURE_MIN_FILTER, GL32C.GL_LINEAR);
        GL32C.glTexParameteri(GL32C.GL_TEXTURE_2D, GL32C.GL_TEXTURE_MAG_FILTER, GL32C.GL_LINEAR);
        GL32C.glPixelStorei(GL32C.GL_UNPACK_ALIGNMENT, 1);
        GL32C.glTexImage2D(GL32C.GL_TEXTURE_2D, 0, GL32C.GL_R8, ATLAS_SIZE, ATLAS_SIZE, 0, GL32C.GL_RED, GL32C.GL_UNSIGNED_BYTE, pixels);
        return textureId;
    }
}
