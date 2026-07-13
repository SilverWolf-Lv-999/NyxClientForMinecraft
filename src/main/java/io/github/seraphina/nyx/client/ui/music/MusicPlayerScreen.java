package io.github.seraphina.nyx.client.ui.music;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.seraphina.nyx.client.music.LyricLine;
import io.github.seraphina.nyx.client.music.LyricLineProcessor;
import io.github.seraphina.nyx.client.music.MusicPlaybackService;
import io.github.seraphina.nyx.client.music.MusicPlaybackService.PlaybackMode;
import io.github.seraphina.nyx.client.music.NeteaseMusicApi;
import io.github.seraphina.nyx.client.music.Playlist;
import io.github.seraphina.nyx.client.music.Song;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.utility.web.WebUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final AtomicInteger TEXTURE_IDS = new AtomicInteger();

    private static final float PANEL_WIDTH = 640.0F;
    private static final float PANEL_HEIGHT = 420.0F;
    private static final float SIDEBAR_WIDTH = 128.0F;
    private static final float PLAYER_HEIGHT = 92.0F;
    private static final int SCREEN_DIM = 0xB005060A;
    private static final int PANEL = 0xFF0C0D11;
    private static final int SIDEBAR = 0xCC090A0E;
    private static final int CARD = 0xFF14161D;
    private static final int CARD_HOVER = 0xFF1A1E2A;
    private static final int CONTROL = 0xFF0C0D11;
    private static final int ACCENT = 0xFF57C7FF;
    private static final int ACCENT_DARK = 0xFF3D81F7;
    private static final int ACCENT_ALT = 0xFFFF6373;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFA0A5B5;
    private static final int TEXT_DIM = 0xFF697183;
    private static final int BORDER = 0x22FFFFFF;
    private static final int BORDER_SOFT = 0x10FFFFFF;
    private static final int TRACK = 0x66000000;
    private static final float TITLE_FONT_SIZE = 12.0F;
    private static final float BODY_FONT_SIZE = 8.5F;
    private static final float META_FONT_SIZE = 7.5F;

    private final List<ClickZone> clickZones = new ArrayList<>();
    private final List<Song> homeSongs = new ArrayList<>();
    private final List<Song> searchSongs = new ArrayList<>();
    private final List<Playlist> playlists = new ArrayList<>();
    private final List<Playlist> userPlaylists = new ArrayList<>();
    private final List<Song> visibleSongs = new ArrayList<>();
    private final Map<String, AlbumTexture> albumTextures = new ConcurrentHashMap<>();
    private final Map<IconButton, Float> buttonHoverProgress = new HashMap<>();

    private Page page = Page.HOME;
    private InputField focusedField = InputField.NONE;
    private Playlist selectedPlaylist;
    private String searchText = "";
    private String phoneText = "";
    private String passwordText = "";
    private String statusText = "Loading home data...";
    private boolean loading;
    private float scroll;
    private float maxScroll;
    private float panelX;
    private float panelY;
    private boolean draggingVolume;

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
        focusedField = InputField.NONE;
        List<ClickZone> zones = List.copyOf(clickZones);
        for (int i = zones.size() - 1; i >= 0; i--) {
            ClickZone zone = zones.get(i);
            if (isInsideExclusive(mouseX, mouseY, zone.x(), zone.y(), zone.width(), zone.height())) {
                zone.action().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && draggingVolume) {
            draggingVolume = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && draggingVolume) {
            setVolumeFromMouse(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideExclusive(mouseX, mouseY, contentX(), contentListY(), contentWidth(), contentHeight())) {
            scroll = clamp(scroll - (float)scrollY * 22.0F, 0.0F, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (focusedField != InputField.NONE) {
            switch (event.key()) {
                case GLFW_KEY_BACKSPACE -> {
                    deleteFocusedCharacter();
                    return true;
                }
                case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> {
                    submitFocusedField();
                    return true;
                }
                case GLFW_KEY_ESCAPE -> {
                    focusedField = InputField.NONE;
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
        if (focusedField == InputField.NONE || !event.isAllowedChatCharacter()) {
            return super.charTyped(event);
        }
        appendFocusedText(event.codepointAsString());
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
        Render2DUtility.drawRoundedHorizontalGradientRect(panelX + 16.0F, panelY + 20.0F, 24.0F, 24.0F, 7.0F, ACCENT, ACCENT_DARK);
        drawIcon(Icon.MUSIC, panelX + 22.0F, panelY + 26.0F, 12.0F, 12.0F, TEXT);
        drawTitle("Nyx Music", panelX + 48.0F, panelY + 19.0F, TEXT);
        drawMeta("Netease", panelX + 48.0F, panelY + 35.0F, TEXT_DIM);

        float y = panelY + 72.0F;
        navButton(guiGraphics, Icon.HOME, "Home", Page.HOME, y, mouseX, mouseY);
        y += 34.0F;
        navButton(guiGraphics, Icon.SEARCH, "Search", Page.SEARCH, y, mouseX, mouseY);
        y += 34.0F;
        navButton(guiGraphics, Icon.LIST, "My Lists", Page.MY, y, mouseX, mouseY);
    }

    private void navButton(GuiGraphics guiGraphics, Icon icon, String label, Page targetPage, float y, int mouseX, int mouseY) {
        boolean active = page == targetPage;
        boolean hovered = isInsideExclusive(mouseX, mouseY, panelX + 10.0F, y, SIDEBAR_WIDTH - 20.0F, 26.0F);
        int fill = active ? 0x2AFC404A : hovered ? 0x12FFFFFF : 0x00000000;
        if (active) {
            fill = 0x1E57C7FF;
        }
        Render2DUtility.drawRoundedRect(panelX + 10.0F, y, SIDEBAR_WIDTH - 20.0F, 26.0F, 5.0F, fill);
        drawIcon(icon, panelX + 21.0F, y + 8.0F, 10.0F, 10.0F, active ? ACCENT : TEXT_MUTED);
        draw(label, panelX + 38.0F, y + 8.0F, active ? ACCENT : TEXT_MUTED);
        clickZones.add(new ClickZone(panelX + 10.0F, y, SIDEBAR_WIDTH - 20.0F, 26.0F, () -> {
            page = targetPage;
            focusedField = InputField.NONE;
            selectedPlaylist = null;
            visibleSongs.clear();
            if (targetPage == Page.HOME) {
                visibleSongs.addAll(homeSongs);
            } else if (targetPage == Page.SEARCH) {
                visibleSongs.addAll(searchSongs);
            } else if (NeteaseMusicApi.isLoggedIn() && userPlaylists.isEmpty() && !loading) {
                loadUserPlaylists();
                return;
            }
            scroll = 0.0F;
            statusText = switch (targetPage) {
                case HOME -> "Ready";
                case SEARCH -> searchStatus();
                case MY -> accountStatus();
            };
        }));
    }

    private void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        float x = contentX();
        float y = panelY + 18.0F;
        drawTitle(title(), x, y - 1.0F, TEXT);
        drawMeta(statusText, x, y + 16.0F, loading ? ACCENT : TEXT_DIM);

        if (page == Page.SEARCH) {
            renderSearchBox(guiGraphics, mouseX, mouseY);
        }
        if (page == Page.MY) {
            if (!NeteaseMusicApi.isLoggedIn()) {
                renderLoginForm(guiGraphics, mouseX, mouseY);
                return;
            }
            renderAccountBar(guiGraphics, mouseX, mouseY);
        }

        float listY = contentListY();
        Render2DUtility.withClip(x, listY, contentWidth(), contentHeight(), () -> renderScrollableContent(guiGraphics, mouseX, mouseY, x, listY));
    }

    private void renderSearchBox(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        float x = contentX() + contentWidth() - 182.0F;
        float y = panelY + 20.0F;
        boolean hovered = isInsideExclusive(mouseX, mouseY, x, y, 174.0F, 24.0F);
        boolean focused = focusedField == InputField.SEARCH;
        Render2DUtility.drawRoundedRect(x, y, 174.0F, 24.0F, 5.0F, hovered || focused ? CARD_HOVER : CONTROL);
        Render2DUtility.drawOutlineRoundedRect(x, y, 174.0F, 24.0F, 5.0F, 1.0F, focused ? ACCENT : BORDER);
        String text = searchText.isEmpty() && !focused ? "Search song..." : searchText + cursor(focused);
        drawIcon(Icon.SEARCH, x + 8.0F, y + 7.0F, 10.0F, 10.0F, focused ? ACCENT : TEXT_DIM);
        draw(trim(text, 116), x + 24.0F, y + 7.5F, searchText.isEmpty() && !focused ? TEXT_DIM : TEXT);
        drawIcon(Icon.ARROW_RIGHT, x + 151.0F, y + 7.0F, 10.0F, 10.0F, ACCENT);
        clickZones.add(new ClickZone(x, y, 136.0F, 24.0F, () -> focusedField = InputField.SEARCH));
        clickZones.add(new ClickZone(x + 138.0F, y, 36.0F, 24.0F, this::runSearch));
    }

    private void renderLoginForm(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        float x = contentX();
        float y = panelY + 70.0F;
        float width = 260.0F;
        Render2DUtility.drawRoundedRect(x, y, width, 142.0F, 7.0F, CARD);
        Render2DUtility.drawOutlineRoundedRect(x, y, width, 142.0F, 7.0F, 1.0F, BORDER_SOFT);
        drawTitle("Netease Login", x + 14.0F, y + 13.0F, TEXT);
        drawMeta("Login to load your playlists", x + 14.0F, y + 31.0F, TEXT_DIM);
        renderInputField(InputField.PHONE, "Phone", phoneText, x + 14.0F, y + 52.0F, 232.0F, false, mouseX, mouseY);
        renderInputField(InputField.PASSWORD, "Password", passwordText, x + 14.0F, y + 84.0F, 232.0F, true, mouseX, mouseY);
        textButton("Login", x + 14.0F, y + 116.0F, 76.0F, 20.0F, loading ? TEXT_DIM : TEXT, mouseX, mouseY, () -> {
            if (!loading) {
                login();
            }
        });
    }

    private void renderAccountBar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        float x = contentX();
        float y = panelY + 54.0F;
        Render2DUtility.drawRoundedRect(x, y, contentWidth(), 28.0F, 6.0F, CARD);
        Render2DUtility.drawOutlineRoundedRect(x, y, contentWidth(), 28.0F, 6.0F, 1.0F, BORDER_SOFT);

        NeteaseMusicApi.LoginSession session = NeteaseMusicApi.currentSession();
        String name = session == null || session.nickname().isBlank() ? "Netease Account" : session.nickname();
        draw(trim(name, 210.0F), x + 10.0F, y + 6.0F, TEXT);
        drawMeta(userPlaylists.size() + " playlists", x + 10.0F, y + 18.0F, TEXT_DIM);
        textButton("Refresh", x + contentWidth() - 132.0F, y + 5.0F, 60.0F, 18.0F, TEXT_MUTED, mouseX, mouseY, () -> {
            if (!loading) {
                loadUserPlaylists();
            }
        });
        textButton("Logout", x + contentWidth() - 66.0F, y + 5.0F, 56.0F, 18.0F, ACCENT_ALT, mouseX, mouseY, this::logout);
    }

    private void renderInputField(InputField field, String placeholder, String value, float x, float y, float width, boolean password, int mouseX, int mouseY) {
        boolean focused = focusedField == field;
        boolean hovered = isInsideExclusive(mouseX, mouseY, x, y, width, 24.0F);
        Render2DUtility.drawRoundedRect(x, y, width, 24.0F, 5.0F, hovered || focused ? CARD_HOVER : CONTROL);
        Render2DUtility.drawOutlineRoundedRect(x, y, width, 24.0F, 5.0F, 1.0F, focused ? ACCENT : BORDER);
        String visible = password ? "*".repeat(value.codePointCount(0, value.length())) : value;
        String text = value.isEmpty() && !focused ? placeholder : visible + cursor(focused);
        draw(trim(text, width - 16.0F), x + 8.0F, y + 7.5F, value.isEmpty() && !focused ? TEXT_DIM : TEXT);
        clickZones.add(new ClickZone(x, y, width, 24.0F, () -> focusedField = field));
    }

    private void textButton(String label, float x, float y, float width, float height, int color, int mouseX, int mouseY, Runnable action) {
        boolean hovered = isInsideExclusive(mouseX, mouseY, x, y, width, height);
        Render2DUtility.drawRoundedRect(x, y, width, height, 5.0F, hovered ? CARD_HOVER : CONTROL);
        Render2DUtility.drawOutlineRoundedRect(x, y, width, height, 5.0F, 1.0F, hovered ? ACCENT : BORDER_SOFT);
        draw(trim(label, width - 12.0F), x + 8.0F, y + (height - BODY_FONT_SIZE) * 0.5F - 0.5F, color);
        clickZones.add(new ClickZone(x, y, width, height, action));
    }

    private void renderScrollableContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float x, float listY) {
        float y = listY - scroll;
        if (page == Page.HOME && selectedPlaylist == null) {
            y = renderPlaylists(guiGraphics, mouseX, mouseY, x, y, playlists, "Recommended playlists");
        } else if (page == Page.MY && selectedPlaylist == null) {
            y = renderPlaylists(guiGraphics, mouseX, mouseY, x, y, userPlaylists, "My playlists");
        }

        if (visibleSongs.isEmpty()) {
            String emptyText = selectedPlaylist == null && (page == Page.HOME || page == Page.MY) ? "Select a playlist" : "No songs";
            draw(loading ? "Loading..." : emptyText, x, y + 12.0F, TEXT_DIM);
            maxScroll = Math.max(0.0F, y + scroll - listY - contentHeight() + 10.0F);
            return;
        }

        for (int i = 0; i < visibleSongs.size(); i++) {
            Song song = visibleSongs.get(i);
            renderSongRow(guiGraphics, song, i, x, y, mouseX, mouseY);
            y += 42.0F;
        }
        maxScroll = Math.max(0.0F, y + scroll - listY - contentHeight() + 10.0F);
    }

    private float renderPlaylists(GuiGraphics guiGraphics, int mouseX, int mouseY, float x, float y, List<Playlist> source, String label) {
        draw(label, x, y, TEXT_MUTED);
        y += 16.0F;
        for (int i = 0; i < source.size(); i++) {
            Playlist playlist = source.get(i);
            float rowX = x + (i % 2) * ((contentWidth() - 10.0F) * 0.5F + 10.0F);
            float rowY = y + (i / 2) * 34.0F;
            boolean hovered = isInsideExclusive(mouseX, mouseY, rowX, rowY, (contentWidth() - 10.0F) * 0.5F, 28.0F);
            float rowW = (contentWidth() - 10.0F) * 0.5F;
            Render2DUtility.drawRoundedRect(rowX, rowY, rowW, 28.0F, 5.0F, hovered ? CARD_HOVER : CARD);
            renderCover(playlist.coverUrl(), rowX + 4.0F, rowY + 4.0F, 20.0F, 4.0F);
            draw(trim(playlist.name(), rowW - 38.0F), rowX + 31.0F, rowY + 5.0F, TEXT);
            drawMeta(formatCount(playlist.playCount()) + " plays", rowX + 31.0F, rowY + 17.5F, TEXT_DIM);
            addScrollableClickZone(rowX, rowY, (contentWidth() - 10.0F) * 0.5F, 28.0F, () -> loadPlaylist(playlist));
        }
        return y + ((source.size() + 1) / 2) * 34.0F + 14.0F;
    }

    private void renderSongRow(GuiGraphics guiGraphics, Song song, int index, float x, float y, int mouseX, int mouseY) {
        boolean current = song.equals(MusicPlaybackService.INSTANCE.currentSong());
        boolean hovered = isInsideExclusive(mouseX, mouseY, x, y, contentWidth(), 36.0F);
        Render2DUtility.drawRoundedRect(x, y, contentWidth(), 36.0F, 6.0F, current ? 0x1E57C7FF : hovered ? CARD_HOVER : CARD);
        renderCover(song.image(), x + 6.0F, y + 5.0F, 26.0F, 5.0F);
        if (current) {
            Render2DUtility.drawRoundedRect(x + 2.0F, y + 8.0F, 2.0F, 20.0F, 1.0F, ACCENT);
        }
        draw(trim(song.name(), 250), x + 40.0F, y + 6.0F, current ? ACCENT : TEXT);
        drawMeta(trim(song.displayArtist(), 250), x + 40.0F, y + 20.0F, TEXT_DIM);
        drawMeta(MusicPlaybackService.formatTime(song.duration()), x + contentWidth() - 48.0F, y + 14.0F, TEXT_MUTED);
        addScrollableClickZone(x, y, contentWidth(), 36.0F, () -> playVisibleSong(song));
    }

    private void renderPlayer(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        MusicPlaybackService player = MusicPlaybackService.INSTANCE;
        float x = panelX + SIDEBAR_WIDTH;
        float y = panelY + PANEL_HEIGHT - PLAYER_HEIGHT;
        clickZones.add(new ClickZone(x, y, PANEL_WIDTH - SIDEBAR_WIDTH, PLAYER_HEIGHT, () -> {
        }));
        Render2DUtility.drawRect(x, y, PANEL_WIDTH - SIDEBAR_WIDTH, 1.0F, BORDER);
        Render2DUtility.drawRect(x, y + 1.0F, PANEL_WIDTH - SIDEBAR_WIDTH, PLAYER_HEIGHT - 1.0F, 0xDD0B0D12);

        Song currentSong = player.currentSong();
        renderCover(currentSong == null ? "" : currentSong.image(), x + 16.0F, y + 14.0F, 48.0F, 8.0F);
        draw(currentSong == null ? "No song selected" : trim(currentSong.name(), 205), x + 74.0F, y + 17.0F, TEXT);
        drawMeta(currentSong == null ? player.status() : trim(currentSong.displayArtist(), 205), x + 74.0F, y + 33.0F, TEXT_DIM);

        float controlsX = x + 286.0F;
        iconButton(guiGraphics, Icon.PREVIOUS, controlsX, y + 17.0F, 24.0F, mouseX, mouseY, player::playPrevious);
        iconButton(guiGraphics, player.isPlaying() ? Icon.PAUSE : Icon.PLAY, controlsX + 32.0F, y + 12.0F, 34.0F, mouseX, mouseY, player::toggle);
        iconButton(guiGraphics, Icon.NEXT, controlsX + 74.0F, y + 17.0F, 24.0F, mouseX, mouseY, player::playNext);
        iconButton(guiGraphics, Icon.STOP, controlsX + 106.0F, y + 17.0F, 24.0F, mouseX, mouseY, player::stop);
        modeButton(guiGraphics, player, controlsX + 138.0F, y + 17.0F, mouseX, mouseY);

        float barX = x + 74.0F;
        float barY = y + 61.0F;
        float barW = PANEL_WIDTH - SIDEBAR_WIDTH - 172.0F;
        float progress = player.totalDurationMs() <= 0L ? 0.0F : clamp(player.positionMs() / (float)player.totalDurationMs(), 0.0F, 1.0F);
        Render2DUtility.drawRoundedRect(barX, barY, barW, 4.0F, 2.0F, TRACK);
        Render2DUtility.drawRoundedHorizontalGradientRect(barX, barY, barW * progress, 4.0F, 2.0F, ACCENT, ACCENT_DARK);
        Render2DUtility.drawCircle(barX + barW * progress, barY + 2.0F, 3.0F, TEXT);
        drawMeta(MusicPlaybackService.formatTime(player.positionMs()) + " / " + MusicPlaybackService.formatTime(player.totalDurationMs()), barX + barW + 8.0F, barY - 4.0F, TEXT_DIM);

        float volumeX = x + 322.0F;
        float volumeY = y + 73.0F;
        drawIcon(Icon.VOLUME, volumeX, volumeY - 4.0F, 11.0F, 11.0F, TEXT_DIM);
        renderVolumeSlider(guiGraphics, volumeX + 17.0F, volumeY, 112.0F, mouseX, mouseY);

        renderCurrentLyric(guiGraphics, player, x + 16.0F, y + 72.0F);
    }

    private void iconButton(GuiGraphics guiGraphics, Icon icon, float x, float y, float size, int mouseX, int mouseY, Runnable action) {
        boolean hovered = isInsideExclusive(mouseX, mouseY, x, y, size, size);
        IconButton key = new IconButton(icon, Math.round(x), Math.round(y));
        float hover = buttonHoverProgress.getOrDefault(key, 0.0F);
        hover += ((hovered ? 1.0F : 0.0F) - hover) * 0.35F;
        buttonHoverProgress.put(key, hover);
        int fill = Render2DUtility.mix(CONTROL, CARD_HOVER, hover);
        Render2DUtility.drawRoundedRect(x, y, size, size, Math.min(7.0F, size * 0.5F), fill);
        Render2DUtility.drawOutlineRoundedRect(x, y, size, size, Math.min(7.0F, size * 0.5F), 1.0F, hovered ? ACCENT : BORDER_SOFT);
        drawIcon(icon, x + size * 0.5F - 5.0F, y + size * 0.5F - 5.0F, 10.0F, 10.0F, icon == Icon.PLAY || icon == Icon.PAUSE ? ACCENT : TEXT_MUTED);
        clickZones.add(new ClickZone(x, y, size, size, action));
    }

    private void modeButton(GuiGraphics guiGraphics, MusicPlaybackService player, float x, float y, int mouseX, int mouseY) {
        PlaybackMode mode = player.playbackMode();
        boolean hovered = isInsideExclusive(mouseX, mouseY, x, y, 62.0F, 24.0F);
        Render2DUtility.drawRoundedRect(x, y, 62.0F, 24.0F, 6.0F, hovered ? CARD_HOVER : CONTROL);
        Render2DUtility.drawOutlineRoundedRect(x, y, 62.0F, 24.0F, 6.0F, 1.0F, hovered ? ACCENT : BORDER_SOFT);
        drawIcon(modeIcon(mode), x + 8.0F, y + 7.0F, 10.0F, 10.0F, ACCENT);
        draw(mode.label(), x + 24.0F, y + 7.5F, TEXT_MUTED);
        clickZones.add(new ClickZone(x, y, 62.0F, 24.0F, player::cyclePlaybackMode));
    }

    private void renderVolumeSlider(GuiGraphics guiGraphics, float x, float y, float width, int mouseX, int mouseY) {
        MusicPlaybackService player = MusicPlaybackService.INSTANCE;
        boolean hovered = isInsideExclusive(mouseX, mouseY, x - 4.0F, y - 7.0F, width + 8.0F, 16.0F);
        Render2DUtility.drawRoundedRect(x, y, width, 4.0F, 2.0F, TRACK);
        Render2DUtility.drawRoundedHorizontalGradientRect(x, y, width * player.volume(), 4.0F, 2.0F, ACCENT_ALT, ACCENT);
        Render2DUtility.drawCircle(x + width * player.volume(), y + 2.0F, draggingVolume || hovered ? 4.0F : 3.0F, TEXT);
        drawMeta(Math.round(player.volume() * 100.0F) + "%", x + width + 7.0F, y - 4.0F, TEXT_DIM);
        clickZones.add(new ClickZone(x - 4.0F, y - 7.0F, width + 8.0F, 16.0F, () -> {
            draggingVolume = true;
            setVolumeFromMouse(mouseX);
        }));
    }

    private void setVolumeFromMouse(double mouseX) {
        float volumeX = panelX + SIDEBAR_WIDTH + 322.0F + 17.0F;
        MusicPlaybackService.INSTANCE.setVolume(clamp((float)((mouseX - volumeX) / 112.0F), 0.0F, 1.0F));
    }

    private void addScrollableClickZone(float x, float y, float width, float height, Runnable action) {
        float clipX = contentX();
        float clipY = contentListY();
        float clippedX = Math.max(x, clipX);
        float clippedY = Math.max(y, clipY);
        float clippedRight = Math.min(x + width, clipX + contentWidth());
        float clippedBottom = Math.min(y + height, clipY + contentHeight());
        if (clippedRight <= clippedX || clippedBottom <= clippedY) {
            return;
        }
        clickZones.add(new ClickZone(clippedX, clippedY, clippedRight - clippedX, clippedBottom - clippedY, action));
    }

    private void renderCurrentLyric(GuiGraphics guiGraphics, MusicPlaybackService player, float x, float y) {
        List<LyricLine> lyrics = player.lyricsSnapshot();
        int index = LyricLineProcessor.currentIndex(lyrics, player.positionMs());
        if (index >= 0 && index < lyrics.size()) {
            draw(trim(lyrics.get(index).text().isBlank() ? "..." : lyrics.get(index).text(), 260), x, y, ACCENT);
        }
    }

    private void playVisibleSong(Song song) {
        int index = visibleSongs.indexOf(song);
        MusicPlaybackService.INSTANCE.setPlaylist(visibleSongs, Math.max(0, index));
        MusicPlaybackService.INSTANCE.playSong(song);
    }

    private void login() {
        if (phoneText.isBlank() || passwordText.isBlank() || loading) {
            statusText = "Enter phone and password";
            return;
        }

        loading = true;
        statusText = "Logging in...";
        CompletableFuture.supplyAsync(() -> {
            try {
                NeteaseMusicApi.LoginSession session = NeteaseMusicApi.loginCellphone(phoneText, passwordText);
                return new UserData(session, NeteaseMusicApi.getUserPlaylists());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, IO).whenComplete((data, throwable) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (throwable != null) {
                statusText = failureText(throwable, "Login failed");
                return;
            }
            passwordText = "";
            focusedField = InputField.NONE;
            page = Page.MY;
            selectedPlaylist = null;
            userPlaylists.clear();
            userPlaylists.addAll(data.playlists());
            visibleSongs.clear();
            scroll = 0.0F;
            statusText = data.session().nickname() + " · " + userPlaylists.size() + " playlists";
        }));
    }

    private void logout() {
        NeteaseMusicApi.logout();
        focusedField = InputField.NONE;
        selectedPlaylist = null;
        userPlaylists.clear();
        visibleSongs.clear();
        passwordText = "";
        scroll = 0.0F;
        statusText = "Logged out";
    }

    private void loadUserPlaylists() {
        if (!NeteaseMusicApi.isLoggedIn() || loading) {
            return;
        }

        loading = true;
        statusText = "Loading your playlists";
        CompletableFuture.supplyAsync(() -> {
            try {
                return NeteaseMusicApi.getUserPlaylists();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, IO).whenComplete((loadedPlaylists, throwable) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (throwable != null) {
                statusText = failureText(throwable, "Failed to load playlists");
                return;
            }
            selectedPlaylist = null;
            userPlaylists.clear();
            userPlaylists.addAll(loadedPlaylists);
            visibleSongs.clear();
            scroll = 0.0F;
            statusText = accountStatus();
        }));
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
        focusedField = InputField.NONE;
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
            searchSongs.clear();
            searchSongs.addAll(songs);
            visibleSongs.clear();
            visibleSongs.addAll(songs);
            statusText = songs.size() + " results";
        }));
    }

    private String searchStatus() {
        if (searchSongs.isEmpty()) {
            return searchText.isBlank() ? "Type a keyword" : "Press Enter to search";
        }
        return searchSongs.size() + " results";
    }

    private String accountStatus() {
        NeteaseMusicApi.LoginSession session = NeteaseMusicApi.currentSession();
        if (session == null) {
            return "Login required";
        }
        String name = session.nickname().isBlank() ? "Netease" : session.nickname();
        return name + " · " + userPlaylists.size() + " playlists";
    }

    private void appendFocusedText(String value) {
        switch (focusedField) {
            case SEARCH -> searchText += value;
            case PHONE -> phoneText += value;
            case PASSWORD -> passwordText += value;
            case NONE -> {
            }
        }
    }

    private void deleteFocusedCharacter() {
        switch (focusedField) {
            case SEARCH -> searchText = deleteLastCodePoint(searchText);
            case PHONE -> phoneText = deleteLastCodePoint(phoneText);
            case PASSWORD -> passwordText = deleteLastCodePoint(passwordText);
            case NONE -> {
            }
        }
    }

    private void submitFocusedField() {
        switch (focusedField) {
            case SEARCH -> runSearch();
            case PHONE, PASSWORD -> login();
            case NONE -> {
            }
        }
    }

    private String cursor(boolean focused) {
        return focused && System.currentTimeMillis() / 500L % 2L == 0L ? "|" : "";
    }

    private static String deleteLastCodePoint(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, text.offsetByCodePoints(text.length(), -1));
    }

    private static String failureText(Throwable throwable, String fallback) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return fallback + ": " + message;
    }

    private void renderCover(String url, float x, float y, float size, float radius) {
        Render2DUtility.drawRoundedRect(x, y, size, size, radius, CONTROL);
        if (url == null || url.isBlank()) {
            drawCoverPlaceholder(x, y, size, radius);
            return;
        }

        AlbumTexture albumTexture = albumTextures.computeIfAbsent(url, this::requestAlbumTexture);
        if (albumTexture.texture != null) {
            Render2DUtility.drawRoundedTexture(albumTexture.texture.getTextureView(), x, y, size, size, radius);
            Render2DUtility.drawOutlineRoundedRect(x, y, size, size, radius, 1.0F, BORDER_SOFT);
            return;
        }

        drawCoverPlaceholder(x, y, size, radius);
    }

    private void drawCoverPlaceholder(float x, float y, float size, float radius) {
        Render2DUtility.drawRoundedHorizontalGradientRect(x, y, size, size, radius, 0xFF181B24, 0xFF10131B);
        Render2DUtility.drawCircle(x + size * 0.5F, y + size * 0.5F, size * 0.27F, 0x2257C7FF);
        drawIcon(Icon.MUSIC, x + size * 0.5F - size * 0.16F, y + size * 0.5F - size * 0.16F, size * 0.32F, size * 0.32F, ACCENT);
        Render2DUtility.drawOutlineRoundedRect(x, y, size, size, radius, 1.0F, BORDER_SOFT);
    }

    private AlbumTexture requestAlbumTexture(String url) {
        AlbumTexture albumTexture = new AlbumTexture();
        CompletableFuture.supplyAsync(() -> downloadAlbumTexture(url), IO).whenComplete((image, throwable) -> {
            if (throwable != null || image == null) {
                albumTexture.failed = true;
                return;
            }
            Minecraft.getInstance().execute(() -> albumTexture.texture = new DynamicTexture(
                () -> "nyx-music-cover-" + TEXTURE_IDS.incrementAndGet(),
                toNativeImage(image)
            ));
        });
        return albumTexture;
    }

    @Nullable
    private static BufferedImage downloadAlbumTexture(String url) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(WebUtility.getBytes(url)));
            if (image == null) {
                return null;
            }
            return scaleImage(image, 96);
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

    private Icon modeIcon(PlaybackMode mode) {
        return switch (mode) {
            case LOOP -> Icon.REPEAT;
            case LIST -> Icon.LIST;
            case RANDOM -> Icon.SHUFFLE;
        };
    }

    private void drawIcon(Icon icon, float x, float y, float width, float height, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        Render2DUtility.drawTexture(minecraft.getTextureManager().getTexture(icon.texture()).getTextureView(), x, y, width, height, color);
    }

    private String title() {
        if (selectedPlaylist != null) {
            return selectedPlaylist.name();
        }
        return switch (page) {
            case HOME -> "Discover";
            case SEARCH -> "Search";
            case MY -> "My Playlists";
        };
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

    private float contentListY() {
        return page == Page.MY && NeteaseMusicApi.isLoggedIn() ? panelY + 88.0F : panelY + 58.0F;
    }

    private float contentHeight() {
        return PANEL_HEIGHT - PLAYER_HEIGHT - (contentListY() - panelY) - 14.0F;
    }

    private void draw(String text, float x, float y, int color) {
        textFont().drawString(text == null ? "" : text, x, y, color);
    }

    private void drawTitle(String text, float x, float y, int color) {
        titleFont().drawString(text == null ? "" : text, x, y, color);
    }

    private void drawMeta(String text, float x, float y, int color) {
        metaFont().drawString(text == null ? "" : text, x, y, color);
    }

    private String trim(String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) {
            return "";
        }
        FontRenderer font = textFont();
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        float suffixWidth = font.getStringWidth(suffix);
        if (suffixWidth >= maxWidth) {
            return "";
        }

        int end = text.length();
        while (end > 0 && font.getStringWidth(text.substring(0, end)) + suffixWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + suffix;
    }

    private FontRenderer titleFont() {
        return FontManager.getAppleDisplayRenderer(TITLE_FONT_SIZE);
    }

    private FontRenderer textFont() {
        return FontManager.getAppleTextRenderer(BODY_FONT_SIZE);
    }

    private FontRenderer metaFont() {
        return FontManager.getAppleTextRenderer(META_FONT_SIZE);
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
        SEARCH,
        MY
    }

    private enum InputField {
        NONE,
        SEARCH,
        PHONE,
        PASSWORD
    }

    private enum Icon {
        MUSIC("music_note"),
        HOME("home"),
        SEARCH("search"),
        ARROW_RIGHT("arrow_right"),
        PLAY("play"),
        PAUSE("pause"),
        PREVIOUS("previous"),
        NEXT("next"),
        STOP("stop"),
        VOLUME("volume"),
        REPEAT("repeat"),
        LIST("list"),
        SHUFFLE("shuffle");

        private final Identifier texture;

        Icon(String fileName) {
            this.texture = Identifier.fromNamespaceAndPath("nyxclient", "ui/icon/music/" + fileName + ".png");
        }

        private Identifier texture() {
            return texture;
        }
    }

    private static final class AlbumTexture {
        @Nullable
        private DynamicTexture texture;
        private boolean failed;
    }

    private record IconButton(Icon icon, int x, int y) {
    }

    private record ClickZone(float x, float y, float width, float height, Runnable action) {
    }

    private record HomeData(List<Song> songs, List<Playlist> playlists) {
    }

    private record UserData(NeteaseMusicApi.LoginSession session, List<Playlist> playlists) {
    }
}
