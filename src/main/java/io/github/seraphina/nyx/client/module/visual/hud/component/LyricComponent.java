package io.github.seraphina.nyx.client.module.visual.hud.component;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.manager.HUDManager;
import io.github.seraphina.nyx.client.music.LyricLine;
import io.github.seraphina.nyx.client.music.LyricLineProcessor;
import io.github.seraphina.nyx.client.music.MusicPlaybackService;
import io.github.seraphina.nyx.client.music.Song;
import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import io.github.seraphina.nyx.client.ui.UIComponent;
import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.utility.web.WebUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class LyricComponent implements UIComponent<HUD> {
    private static final String ID = "lyric";
    private static final String EMPTY_LINE = "...";
    private static final float WIDTH_MIN = 80.0F;
    private static final float WIDTH_MAX = 320.0F;
    private static final float HEIGHT = 50.0F;
    private static final float SONG_INFO_WIDTH = 74.0F;
    private static final float DIVIDER_WIDTH = 1.0F;
    private static final float DIVIDER_GAP = 6.0F;
    private static final float COVER_SIZE = 25.0F;
    private static final float COVER_RADIUS = 5.0F;
    private static final float COVER_Y = 6.0F;
    private static final float HORIZONTAL_PADDING = 10.0F;
    private static final float VERTICAL_PADDING = 7.0F;
    private static final float LINE_SPACING = 15.0F;
    private static final float RADIUS = 6.0F;
    private static final float FADE_SPEED = 8.0F;
    private static final float SCROLL_SPEED = 11.0F;
    private static final int COVER_SIZE_PIXELS = 96;
    private static final int BACKGROUND = 0xCC0C0D11;
    private static final int BORDER = 0x22FFFFFF;
    private static final int DIVIDER = 0x26FFFFFF;
    private static final int COVER_BACKGROUND_START = 0xFF181B24;
    private static final int COVER_BACKGROUND_END = 0xFF10131B;
    private static final int COVER_ACCENT = 0xFF57C7FF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xCCFFFFFF;
    private static final int SHADOW = 0x80000000;
    private static final Identifier MUSIC_NOTE_ICON = Identifier.fromNamespaceAndPath("nyxclient", "ui/icon/music/music_note.png");
    private static final ExecutorService IO = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Nyx-LyricCover");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicInteger TEXTURE_IDS = new AtomicInteger();

    private final Map<String, AlbumTexture> albumTextures = new ConcurrentHashMap<>();
    private float visibilityProgress;
    private float lyricPosition = Float.NaN;
    private long lastFrameNanos;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public float getDefaultX() {
        if (mc.getWindow() == null) {
            return 8.0F;
        }
        return (mc.getWindow().getGuiScaledWidth() - width(font(), lyricState())) * 0.5F;
    }

    @Override
    public float getDefaultY() {
        if (mc.getWindow() == null) {
            return 80.0F;
        }
        return Math.max(8.0F, mc.getWindow().getGuiScaledHeight() - HEIGHT - 58.0F);
    }

    @Override
    public void render(GuiGraphics graphics, float partialTicks, float scale) {
        FontRenderer font = font();
        LyricState state = lyricState();
        float frameSeconds = frameSeconds();
        boolean hasMusic = state.hasMusic();

        visibilityProgress = MathUtility.animateExp(visibilityProgress, hasMusic ? 1.0F : 0.0F, FADE_SPEED, frameSeconds);
        if (!hasMusic && visibilityProgress <= 0.001F) {
            visibilityProgress = 0.0F;
            lyricPosition = Float.NaN;
            return;
        }

        int targetIndex = state.currentIndex();
        if (Float.isNaN(lyricPosition) || Math.abs(lyricPosition - targetIndex) > 3.0F) {
            lyricPosition = targetIndex;
        } else {
            lyricPosition = MathUtility.animateExp(lyricPosition, targetIndex, SCROLL_SPEED, frameSeconds);
        }

        float opacity = MathUtility.easeOutCubic(visibilityProgress);
        float width = width(font, state);
        float lyricX = lyricX();
        float lyricWidth = lyricWidth(font, state);

        Render2DUtility.drawDropShadow(0.0F, 0.0F, width, HEIGHT, RADIUS, 0.0F, 0.0F, 10.0F,
            Render2DUtility.applyOpacity(SHADOW, opacity));
        Render2DUtility.drawRoundedRect(0.0F, 0.0F, width, HEIGHT, RADIUS,
            Render2DUtility.applyOpacity(BACKGROUND, opacity));
        Render2DUtility.drawOutlineRoundedRect(0.0F, 0.0F, width, HEIGHT, RADIUS, 1.0F,
            Render2DUtility.applyOpacity(BORDER, opacity));
        renderSongInfo(font, state.song(), opacity);
        Render2DUtility.drawRect(SONG_INFO_WIDTH, VERTICAL_PADDING, DIVIDER_WIDTH, HEIGHT - VERTICAL_PADDING * 2.0F,
            Render2DUtility.applyOpacity(DIVIDER, opacity));

        AABB bounds = HUDManager.getDisplayBounds(this);
        Render2DUtility.withClip(
            (float)bounds.minX + lyricX * scale,
            (float)bounds.minY,
            lyricWidth * scale,
            (float)bounds.getYsize(),
            () -> renderLyrics(font, state, lyricX, lyricWidth, opacity)
        );
    }

    @Override
    public AABB getBoundingBox() {
        FontRenderer font = font();
        return new AABB(0.0D, 0.0D, 0.0D, width(font, lyricState()), HEIGHT, 1.0D);
    }

    @Override
    public boolean isVisible() {
        return HUD.INSTANCE.lyric.getValue();
    }

    private FontRenderer font() {
        return FontManager.getAppleTextRenderer(12.0F);
    }

    private LyricState lyricState() {
        MusicPlaybackService player = MusicPlaybackService.INSTANCE;
        Song song = player.currentSong();
        boolean hasMusic = song != null && player.isPlaying();

        List<LyricLine> lyrics = player.lyricsSnapshot();
        if (lyrics.isEmpty()) {
            return new LyricState(hasMusic, song, List.of(), 0);
        }

        int index = LyricLineProcessor.currentIndex(lyrics, player.positionMs());
        int currentIndex = Math.max(0, Math.min(index, lyrics.size() - 1));
        return new LyricState(hasMusic, song, lyrics, currentIndex);
    }

    private void renderSongInfo(FontRenderer font, Song song, float opacity) {
        float coverX = (SONG_INFO_WIDTH - COVER_SIZE) * 0.5F;
        renderCover(song == null ? null : song.image(), coverX, COVER_Y, opacity);

        String name = songName(song);
        float maxNameWidth = SONG_INFO_WIDTH - HORIZONTAL_PADDING;
        String text = trimToWidth(font, name, maxNameWidth);
        float nameY = HEIGHT - VERTICAL_PADDING - font.getLineHeight() + 0.5F;
        font.drawCenteredString(text, SONG_INFO_WIDTH * 0.5F, nameY, Render2DUtility.applyOpacity(TEXT_MUTED, opacity));
    }

    private void renderCover(String url, float x, float y, float opacity) {
        if (url == null || url.isBlank()) {
            drawCoverPlaceholder(x, y, opacity);
            return;
        }

        AlbumTexture albumTexture = albumTextures.computeIfAbsent(url, this::requestAlbumTexture);
        if (albumTexture.texture != null) {
            Render2DUtility.drawRoundedTexture(albumTexture.texture.getTextureView(), x, y, COVER_SIZE, COVER_SIZE, COVER_RADIUS,
                Render2DUtility.applyOpacity(0xFFFFFFFF, opacity));
            Render2DUtility.drawOutlineRoundedRect(x, y, COVER_SIZE, COVER_SIZE, COVER_RADIUS, 1.0F,
                Render2DUtility.applyOpacity(BORDER, opacity));
            return;
        }

        drawCoverPlaceholder(x, y, opacity);
    }

    private void drawCoverPlaceholder(float x, float y, float opacity) {
        Render2DUtility.drawRoundedHorizontalGradientRect(x, y, COVER_SIZE, COVER_SIZE, COVER_RADIUS,
            Render2DUtility.applyOpacity(COVER_BACKGROUND_START, opacity),
            Render2DUtility.applyOpacity(COVER_BACKGROUND_END, opacity));
        Render2DUtility.drawCircle(x + COVER_SIZE * 0.5F, y + COVER_SIZE * 0.5F, COVER_SIZE * 0.27F,
            Render2DUtility.applyOpacity(0x2257C7FF, opacity));
        drawMusicIcon(x + COVER_SIZE * 0.34F, y + COVER_SIZE * 0.34F, COVER_SIZE * 0.32F, opacity);
        Render2DUtility.drawOutlineRoundedRect(x, y, COVER_SIZE, COVER_SIZE, COVER_RADIUS, 1.0F,
            Render2DUtility.applyOpacity(BORDER, opacity));
    }

    private void drawMusicIcon(float x, float y, float size, float opacity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        Render2DUtility.drawTexture(minecraft.getTextureManager().getTexture(MUSIC_NOTE_ICON).getTextureView(), x, y, size, size,
            Render2DUtility.applyOpacity(COVER_ACCENT, opacity));
    }

    private AlbumTexture requestAlbumTexture(String url) {
        AlbumTexture albumTexture = new AlbumTexture();
        CompletableFuture.supplyAsync(() -> downloadAlbumTexture(url), IO).whenComplete((image, throwable) -> {
            if (throwable != null || image == null) {
                albumTexture.failed = true;
                return;
            }
            Minecraft.getInstance().execute(() -> albumTexture.texture = new DynamicTexture(
                () -> "nyx-lyric-cover-" + TEXTURE_IDS.incrementAndGet(),
                toNativeImage(image)
            ));
        });
        return albumTexture;
    }

    private void renderLyrics(FontRenderer font, LyricState state, float x, float width, float opacity) {
        float centerY = (HEIGHT - font.getLineHeight()) * 0.5F - 0.5F;
        int centerIndex = Math.round(lyricPosition);
        int firstIndex = centerIndex - 2;
        int lastIndex = centerIndex + 2;

        if (state.lyrics().isEmpty()) {
            font.drawCenteredString(EMPTY_LINE, x + width * 0.5F, centerY, Render2DUtility.applyOpacity(TEXT, opacity));
            return;
        }

        for (int index = firstIndex; index <= lastIndex; index++) {
            if (index < 0 || index >= state.lyrics().size()) {
                continue;
            }

            float rowOffset = index - lyricPosition;
            float y = centerY + rowOffset * LINE_SPACING;
            if (y < VERTICAL_PADDING - font.getLineHeight() || y > HEIGHT - VERTICAL_PADDING) {
                continue;
            }

            float distance = Math.abs(rowOffset);
            float lineOpacity = opacity * MathUtility.clamp(1.0F - distance * 0.36F, 0.0F, 1.0F);
            int baseColor = distance <= 0.5F ? TEXT : TEXT_MUTED;
            String text = trimToWidth(font, lyricText(state.lyrics().get(index)), width - HORIZONTAL_PADDING * 2.0F);
            font.drawCenteredString(text, x + width * 0.5F, y, Render2DUtility.applyOpacity(baseColor, lineOpacity));
        }
    }

    private float width(FontRenderer font, LyricState state) {
        return lyricX() + lyricWidth(font, state);
    }

    private float lyricWidth(FontRenderer font, LyricState state) {
        float textWidth = font.getStringWidth(EMPTY_LINE);
        if (!state.lyrics().isEmpty()) {
            int centerIndex = state.currentIndex();
            for (int index = centerIndex - 1; index <= centerIndex + 1; index++) {
                if (index >= 0 && index < state.lyrics().size()) {
                    String text = trimToWidth(font, lyricText(state.lyrics().get(index)), WIDTH_MAX - HORIZONTAL_PADDING * 2.0F);
                    textWidth = Math.max(textWidth, font.getStringWidth(text));
                }
            }
        }
        return MathUtility.clamp(textWidth + HORIZONTAL_PADDING * 2.0F, WIDTH_MIN, WIDTH_MAX);
    }

    private static float lyricX() {
        return SONG_INFO_WIDTH + DIVIDER_WIDTH + DIVIDER_GAP;
    }

    private float frameSeconds() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 0.0F;
        }

        float seconds = (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        return MathUtility.clamp(seconds, 0.0F, 0.1F);
    }

    private static String lyricText(LyricLine line) {
        String text = line.text();
        return text == null || text.isBlank() ? EMPTY_LINE : text.strip();
    }

    private static String songName(Song song) {
        if (song == null || song.name() == null || song.name().isBlank()) {
            return EMPTY_LINE;
        }
        return song.name().strip();
    }

    private static String trimToWidth(FontRenderer font, String text, float maxWidth) {
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        float ellipsisWidth = font.getStringWidth(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }

        int end = text.length();
        while (end > 0 && font.getStringWidth(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    private static BufferedImage downloadAlbumTexture(String url) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(WebUtility.getBytes(url)));
            if (image == null) {
                return null;
            }
            return scaleImage(image, COVER_SIZE_PIXELS);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage scaleImage(BufferedImage source, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                nativeImage.setPixel(x, y, image.getRGB(x, y));
            }
        }
        return nativeImage;
    }

    private static final class AlbumTexture {
        private DynamicTexture texture;
        private boolean failed;
    }

    private record LyricState(boolean hasMusic, Song song, List<LyricLine> lyrics, int currentIndex) {
    }
}
