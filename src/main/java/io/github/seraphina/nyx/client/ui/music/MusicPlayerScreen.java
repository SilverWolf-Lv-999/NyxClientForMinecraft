package io.github.seraphina.nyx.client.ui.music;

import io.github.seraphina.nyx.client.music.LyricLine;
import io.github.seraphina.nyx.client.music.LyricLineProcessor;
import io.github.seraphina.nyx.client.music.MusicPlaybackService;
import io.github.seraphina.nyx.client.music.NeteaseMusicApi;
import io.github.seraphina.nyx.client.music.Playlist;
import io.github.seraphina.nyx.client.music.Song;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.github.seraphina.nyx.client.utility.MathUtility.clamp;
import static io.github.seraphina.nyx.client.utility.MathUtility.isInsideExclusive;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class MusicPlayerScreen extends Screen {
    private static final ExecutorService IO = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Nyx-MusicUi");
        thread.setDaemon(true);
        return thread;
    });

    private static final float PANEL_WIDTH = 560.0F;
    private static final float PANEL_HEIGHT = 360.0F;
    private static final float SIDEBAR_WIDTH = 112.0F;
    private static final float PLAYER_HEIGHT = 66.0F;
    private static final int SCREEN_DIM = 0xB005060A;
    private static final int PANEL = 0xFF0D0F15;
    private static final int SIDEBAR = 0xFF11141D;
    private static final int CARD = 0xFF181C28;
    private static final int CARD_HOVER = 0xFF202635;
    private static final int CONTROL = 0xFF10131B;
    private static final int ACCENT = 0xFFFC404A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFA6ADBC;
    private static final int TEXT_DIM = 0xFF697183;
    private static final int BORDER = 0x24FFFFFF;

    private final List<ClickZone> clickZones = new ArrayList<>();
    private final List<Song> homeSongs = new ArrayList<>();
    private final List<Playlist> playlists = new ArrayList<>();
    private final List<Song> visibleSongs = new ArrayList<>();

    private Page page = Page.HOME;
    private Playlist selectedPlaylist;
    private String searchText = "";
    private String statusText = "Loading home data...";
    private boolean searchFocused;
    private boolean loading;
    private float scroll;
    private float maxScroll;
    private float panelX;
    private float panelY;

    public MusicPlayerScreen() {
        super(Component.literal("Music Player"));
    }

    @Override
    protected void init() {
        loadHome();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        Render2DUtility.withGuiGraphics(guiGraphics, () -> {
            clickZones.clear();
            updateMetrics();
            Render2DUtility.drawRect(0.0F, 0.0F, this.width, this.height, SCREEN_DIM);
            Render2DUtility.drawDropShadow(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 12.0F, 0.0F, 18.0F, 30.0F, 0xA0000000);
            Render2DUtility.drawRoundedRect(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 10.0F, PANEL);
            Render2DUtility.drawOutlineRoundedRect(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 10.0F, 1.0F, BORDER);
            Render2DUtility.withClip(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, () -> {
                renderSidebar(guiGraphics, mouseX, mouseY);
                renderContent(guiGraphics, mouseX, mouseY);
                renderPlayer(guiGraphics, mouseX, mouseY);
            });
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        double mouseX = event.x();
        double mouseY = event.y();
        searchFocused = false;
        for (ClickZone zone : List.copyOf(clickZones)) {
            if (isInsideExclusive(mouseX, mouseY, zone.x(), zone.y(), zone.width(), zone.height())) {
                zone.action().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideExclusive(mouseX, mouseY, contentX(), panelY + 58.0F, contentWidth(), contentHeight())) {
            scroll = clamp(scroll - (float)scrollY * 22.0F, 0.0F, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchFocused) {
            switch (event.key()) {
                case GLFW_KEY_BACKSPACE -> {
                    if (!searchText.isEmpty()) {
                        searchText = searchText.substring(0, searchText.offsetByCodePoints(searchText.length(), -1));
                    }
                    return true;
                }
                case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> {
                    runSearch();
                    return true;
                }
                case GLFW_KEY_ESCAPE -> {
                    searchFocused = false;
                    return true;
                }
                default -> {
                    return super.keyPressed(event);
                }
            }
        }

        if (event.isEscape()) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!searchFocused || !event.isAllowedChatCharacter()) {
            return super.charTyped(event);
        }
        searchText += event.codepointAsString();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    private void renderSidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Render2DUtility.drawRect(panelX, panelY, SIDEBAR_WIDTH, PANEL_HEIGHT, SIDEBAR);
        Render2DUtility.drawRect(panelX + SIDEBAR_WIDTH - 1.0F, panelY + 12.0F, 1.0F, PANEL_HEIGHT - 24.0F, BORDER);
        draw(guiGraphics, "Nyx Music", panelX + 16.0F, panelY + 20.0F, TEXT);
        draw(guiGraphics, "Netease Cloud", panelX + 16.0F, panelY + 34.0F, TEXT_DIM);

        float y = panelY + 72.0F;
        navButton(guiGraphics, "Home", Page.HOME, y, mouseX, mouseY);
        y += 34.0F;
        navButton(guiGraphics, "Search", Page.SEARCH, y, mouseX, mouseY);
    }

    private void navButton(GuiGraphics guiGraphics, String label, Page targetPage, float y, int mouseX, int mouseY) {
        boolean active = page == targetPage;
        boolean hovered = isInsideExclusive(mouseX, mouseY, panelX + 10.0F, y, SIDEBAR_WIDTH - 20.0F, 26.0F);
        int fill = active ? 0x2AFC404A : hovered ? 0x12FFFFFF : 0x00000000;
        Render2DUtility.drawRoundedRect(panelX + 10.0F, y, SIDEBAR_WIDTH - 20.0F, 26.0F, 5.0F, fill);
        draw(guiGraphics, label, panelX + 22.0F, y + 9.0F, active ? ACCENT : TEXT_MUTED);
        clickZones.add(new ClickZone(panelX + 10.0F, y, SIDEBAR_WIDTH - 20.0F, 26.0F, () -> {
            page = targetPage;
            selectedPlaylist = null;
            visibleSongs.clear();
            visibleSongs.addAll(targetPage == Page.HOME ? homeSongs : visibleSongs);
            scroll = 0.0F;
        }));
    }

    private void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        float x = contentX();
        float y = panelY + 18.0F;
        draw(guiGraphics, title(), x, y, TEXT);
        draw(guiGraphics, statusText, x, y + 15.0F, loading ? ACCENT : TEXT_DIM);

        if (page == Page.SEARCH) {
            renderSearchBox(guiGraphics, mouseX, mouseY);
        }

        float listY = panelY + 58.0F;
        Render2DUtility.withClip(x, listY, contentWidth(), contentHeight(), () -> renderScrollableContent(guiGraphics, mouseX, mouseY, x, listY));
    }

    private void renderSearchBox(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        float x = contentX() + contentWidth() - 182.0F;
        float y = panelY + 20.0F;
        boolean hovered = isInsideExclusive(mouseX, mouseY, x, y, 174.0F, 24.0F);
        Render2DUtility.drawRoundedRect(x, y, 174.0F, 24.0F, 5.0F, hovered || searchFocused ? CARD_HOVER : CONTROL);
        Render2DUtility.drawOutlineRoundedRect(x, y, 174.0F, 24.0F, 5.0F, 1.0F, searchFocused ? ACCENT : BORDER);
        String text = searchText.isEmpty() && !searchFocused ? "Search song..." : searchText + (searchFocused && System.currentTimeMillis() / 500L % 2L == 0L ? "|" : "");
        draw(guiGraphics, trim(text, 142), x + 8.0F, y + 8.0F, searchText.isEmpty() && !searchFocused ? TEXT_DIM : TEXT);
        draw(guiGraphics, "Go", x + 150.0F, y + 8.0F, ACCENT);
        clickZones.add(new ClickZone(x, y, 136.0F, 24.0F, () -> searchFocused = true));
        clickZones.add(new ClickZone(x + 138.0F, y, 36.0F, 24.0F, this::runSearch));
    }

    private void renderScrollableContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float x, float listY) {
        float y = listY - scroll;
        if (page == Page.HOME && selectedPlaylist == null) {
            y = renderPlaylists(guiGraphics, mouseX, mouseY, x, y);
        }

        if (visibleSongs.isEmpty()) {
            draw(guiGraphics, loading ? "Loading..." : "No songs", x, y + 12.0F, TEXT_DIM);
            maxScroll = 0.0F;
            return;
        }

        for (int i = 0; i < visibleSongs.size(); i++) {
            Song song = visibleSongs.get(i);
            renderSongRow(guiGraphics, song, i, x, y, mouseX, mouseY);
            y += 42.0F;
        }
        maxScroll = Math.max(0.0F, y + scroll - listY - contentHeight() + 10.0F);
    }

    private float renderPlaylists(GuiGraphics guiGraphics, int mouseX, int mouseY, float x, float y) {
        draw(guiGraphics, "Recommended playlists", x, y, TEXT_MUTED);
        y += 16.0F;
        for (int i = 0; i < playlists.size(); i++) {
            Playlist playlist = playlists.get(i);
            float rowX = x + (i % 2) * ((contentWidth() - 10.0F) * 0.5F + 10.0F);
            float rowY = y + (i / 2) * 34.0F;
            boolean hovered = isInsideExclusive(mouseX, mouseY, rowX, rowY, (contentWidth() - 10.0F) * 0.5F, 28.0F);
            Render2DUtility.drawRoundedRect(rowX, rowY, (contentWidth() - 10.0F) * 0.5F, 28.0F, 5.0F, hovered ? CARD_HOVER : CARD);
            draw(guiGraphics, trim(playlist.name(), 150), rowX + 8.0F, rowY + 6.0F, TEXT);
            draw(guiGraphics, formatCount(playlist.playCount()) + " plays", rowX + 8.0F, rowY + 17.0F, TEXT_DIM);
            clickZones.add(new ClickZone(rowX, rowY, (contentWidth() - 10.0F) * 0.5F, 28.0F, () -> loadPlaylist(playlist)));
        }
        return y + ((playlists.size() + 1) / 2) * 34.0F + 14.0F;
    }

    private void renderSongRow(GuiGraphics guiGraphics, Song song, int index, float x, float y, int mouseX, int mouseY) {
        boolean current = song.equals(MusicPlaybackService.INSTANCE.currentSong());
        boolean hovered = isInsideExclusive(mouseX, mouseY, x, y, contentWidth(), 36.0F);
        Render2DUtility.drawRoundedRect(x, y, contentWidth(), 36.0F, 6.0F, current ? 0x24FC404A : hovered ? CARD_HOVER : CARD);
        draw(guiGraphics, String.valueOf(index + 1), x + 8.0F, y + 14.0F, current ? ACCENT : TEXT_DIM);
        draw(guiGraphics, trim(song.name(), 250), x + 30.0F, y + 7.0F, current ? ACCENT : TEXT);
        draw(guiGraphics, trim(song.displayArtist(), 250), x + 30.0F, y + 20.0F, TEXT_DIM);
        draw(guiGraphics, MusicPlaybackService.formatTime(song.duration()), x + contentWidth() - 48.0F, y + 14.0F, TEXT_MUTED);
        clickZones.add(new ClickZone(x, y, contentWidth(), 36.0F, () -> playVisibleSong(song)));
    }

    private void renderPlayer(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        MusicPlaybackService player = MusicPlaybackService.INSTANCE;
        float x = panelX + SIDEBAR_WIDTH;
        float y = panelY + PANEL_HEIGHT - PLAYER_HEIGHT;
        Render2DUtility.drawRect(x, y, PANEL_WIDTH - SIDEBAR_WIDTH, 1.0F, BORDER);
        Render2DUtility.drawRect(x, y + 1.0F, PANEL_WIDTH - SIDEBAR_WIDTH, PLAYER_HEIGHT - 1.0F, 0xDD0B0D12);

        Song currentSong = player.currentSong();
        draw(guiGraphics, currentSong == null ? "No song selected" : trim(currentSong.name(), 210), x + 16.0F, y + 14.0F, TEXT);
        draw(guiGraphics, currentSong == null ? player.status() : trim(currentSong.displayArtist(), 210), x + 16.0F, y + 29.0F, TEXT_DIM);

        float controlsX = x + 250.0F;
        smallButton(guiGraphics, "<<", controlsX, y + 17.0F, mouseX, mouseY, player::playPrevious);
        smallButton(guiGraphics, player.isPlaying() ? "Pause" : "Play", controlsX + 42.0F, y + 17.0F, mouseX, mouseY, player::toggle);
        smallButton(guiGraphics, ">>", controlsX + 104.0F, y + 17.0F, mouseX, mouseY, player::playNext);
        smallButton(guiGraphics, "Stop", controlsX + 146.0F, y + 17.0F, mouseX, mouseY, player::stop);

        float barX = x + 16.0F;
        float barY = y + 52.0F;
        float barW = PANEL_WIDTH - SIDEBAR_WIDTH - 136.0F;
        float progress = player.totalDurationMs() <= 0L ? 0.0F : clamp(player.positionMs() / (float)player.totalDurationMs(), 0.0F, 1.0F);
        Render2DUtility.drawRoundedRect(barX, barY, barW, 3.0F, 1.5F, 0x33FFFFFF);
        Render2DUtility.drawRoundedRect(barX, barY, barW * progress, 3.0F, 1.5F, ACCENT);
        draw(guiGraphics, MusicPlaybackService.formatTime(player.positionMs()) + " / " + MusicPlaybackService.formatTime(player.totalDurationMs()), barX + barW + 8.0F, barY - 4.0F, TEXT_DIM);

        float volumeX = x + PANEL_WIDTH - SIDEBAR_WIDTH - 82.0F;
        float volumeY = y + 38.0F;
        Render2DUtility.drawRoundedRect(volumeX, volumeY, 56.0F, 3.0F, 1.5F, 0x33FFFFFF);
        Render2DUtility.drawRoundedRect(volumeX, volumeY, 56.0F * player.volume(), 3.0F, 1.5F, ACCENT);
        clickZones.add(new ClickZone(volumeX, volumeY - 6.0F, 56.0F, 15.0F, () -> player.setVolume(clamp((mouseX - volumeX) / 56.0F, 0.0F, 1.0F))));

        renderCurrentLyric(guiGraphics, player, x + 250.0F, y + 46.0F);
    }

    private void smallButton(GuiGraphics guiGraphics, String label, float x, float y, int mouseX, int mouseY, Runnable action) {
        float width = "Pause".equals(label) || "Play".equals(label) || "Stop".equals(label) ? 50.0F : 32.0F;
        boolean hovered = isInsideExclusive(mouseX, mouseY, x, y, width, 20.0F);
        Render2DUtility.drawRoundedRect(x, y, width, 20.0F, 5.0F, hovered ? CARD_HOVER : CONTROL);
        drawCentered(guiGraphics, label, x + width * 0.5F, y + 6.0F, "Play".equals(label) || "Pause".equals(label) ? ACCENT : TEXT_MUTED);
        clickZones.add(new ClickZone(x, y, width, 20.0F, action));
    }

    private void renderCurrentLyric(GuiGraphics guiGraphics, MusicPlaybackService player, float x, float y) {
        List<LyricLine> lyrics = player.lyricsSnapshot();
        int index = LyricLineProcessor.currentIndex(lyrics, player.positionMs());
        if (index >= 0 && index < lyrics.size()) {
            draw(guiGraphics, trim(lyrics.get(index).text().isBlank() ? "..." : lyrics.get(index).text(), 190), x, y, ACCENT);
        }
    }

    private void playVisibleSong(Song song) {
        int index = visibleSongs.indexOf(song);
        MusicPlaybackService.INSTANCE.setPlaylist(visibleSongs, Math.max(0, index));
        MusicPlaybackService.INSTANCE.playSong(song);
    }

    private void loadHome() {
        if (!homeSongs.isEmpty() || loading) {
            if (visibleSongs.isEmpty()) {
                visibleSongs.addAll(homeSongs);
            }
            return;
        }
        loading = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                return new HomeData(NeteaseMusicApi.getTopNewSongs(), NeteaseMusicApi.getRecommendPlaylists(6));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, IO).whenComplete((data, throwable) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (throwable != null) {
                statusText = "Failed to load home data";
                return;
            }
            homeSongs.clear();
            homeSongs.addAll(data.songs());
            playlists.clear();
            playlists.addAll(data.playlists());
            if (page == Page.HOME && selectedPlaylist == null) {
                visibleSongs.clear();
                visibleSongs.addAll(homeSongs);
            }
            statusText = "Ready";
        }));
    }

    private void loadPlaylist(Playlist playlist) {
        selectedPlaylist = playlist;
        page = Page.HOME;
        loading = true;
        statusText = "Loading " + playlist.name();
        scroll = 0.0F;
        CompletableFuture.supplyAsync(() -> {
            try {
                return NeteaseMusicApi.getPlaylistDetail(playlist.id());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, IO).whenComplete((songs, throwable) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (throwable != null) {
                statusText = "Failed to load playlist";
                return;
            }
            visibleSongs.clear();
            visibleSongs.addAll(songs);
            statusText = playlist.name();
        }));
    }

    private void runSearch() {
        if (searchText.isBlank()) {
            return;
        }
        page = Page.SEARCH;
        selectedPlaylist = null;
        loading = true;
        statusText = "Searching " + searchText;
        scroll = 0.0F;
        CompletableFuture.supplyAsync(() -> {
            try {
                return NeteaseMusicApi.search(searchText);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, IO).whenComplete((songs, throwable) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (throwable != null) {
                statusText = "Search failed";
                return;
            }
            visibleSongs.clear();
            visibleSongs.addAll(songs);
            statusText = songs.size() + " results";
        }));
    }

    private String title() {
        if (selectedPlaylist != null) {
            return selectedPlaylist.name();
        }
        return page == Page.SEARCH ? "Search" : "Discover";
    }

    private void updateMetrics() {
        panelX = (this.width - PANEL_WIDTH) * 0.5F;
        panelY = (this.height - PANEL_HEIGHT) * 0.5F;
    }

    private float contentX() {
        return panelX + SIDEBAR_WIDTH + 18.0F;
    }

    private float contentWidth() {
        return PANEL_WIDTH - SIDEBAR_WIDTH - 36.0F;
    }

    private float contentHeight() {
        return PANEL_HEIGHT - PLAYER_HEIGHT - 72.0F;
    }

    private void draw(GuiGraphics guiGraphics, String text, float x, float y, int color) {
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, text == null ? "" : text, Math.round(x), Math.round(y), color, false);
    }

    private void drawCentered(GuiGraphics guiGraphics, String text, float centerX, float y, int color) {
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, text, Math.round(centerX - font.width(text) * 0.5F), Math.round(y), color, false);
    }

    private String trim(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        Font font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(suffix))) + suffix;
    }

    private static String formatCount(long count) {
        if (count >= 100_000_000L) {
            return String.format("%.1fB", count / 100_000_000.0F);
        }
        if (count >= 10_000L) {
            return String.format("%.1fW", count / 10_000.0F);
        }
        return String.valueOf(count);
    }

    private enum Page {
        HOME,
        SEARCH
    }

    private record ClickZone(float x, float y, float width, float height, Runnable action) {
    }

    private record HomeData(List<Song> songs, List<Playlist> playlists) {
    }
}
