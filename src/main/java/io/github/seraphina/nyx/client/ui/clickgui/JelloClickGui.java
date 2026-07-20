package io.github.seraphina.nyx.client.ui.clickgui;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.manager.ModuleManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.music.LyricLine;
import io.github.seraphina.nyx.client.music.LyricLineProcessor;
import io.github.seraphina.nyx.client.music.MusicPlaybackService;
import io.github.seraphina.nyx.client.music.NeteaseMusicApi;
import io.github.seraphina.nyx.client.music.Playlist;
import io.github.seraphina.nyx.client.music.Song;
import io.github.seraphina.nyx.client.ui.LuaScreen;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.utility.web.WebUtility;
import io.github.seraphina.nyx.client.value.AbstractValue;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ButtonValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import io.github.seraphina.nyx.client.value.impl.KeyBindValue;
import io.github.seraphina.nyx.client.value.impl.StringValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.luaj.vm2.LuaValue;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.seraphina.nyx.client.utility.MathUtility.animateExp;
import static io.github.seraphina.nyx.client.utility.MathUtility.clamp;
import static io.github.seraphina.nyx.client.utility.MathUtility.easeOutCubic;
import static io.github.seraphina.nyx.client.utility.MathUtility.isInsideExclusive;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.glfwGetKeyName;

/**
 * White, square-cornered Jello/Sigma inspired ClickGUI.
 * Lua owns the responsive layout and card animation; this controller owns game state,
 * right-click module selection, value editing and asynchronous NetEase music data.
 */
public final class JelloClickGui extends LuaScreen {
    public static final JelloClickGui INSTANCE = new JelloClickGui();

    private static final List<Category> CATEGORY_ORDER = List.of(
        Category.PLAYER,
        Category.COMBAT,
        Category.MOVEMENT,
        Category.CLIENT,
        Category.OTHER,
        Category.VISUAL
    );

    private static final ExecutorService MUSIC_IO = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Nyx-Jello-Music");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicInteger TEXTURE_IDS = new AtomicInteger();
    private static final long CLOSE_DURATION_NANOS = 190_000_000L;
    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;

    private static final int WHITE_TEXT = 0xFF202228;
    private static final int MUTED_TEXT = 0xFF777B85;
    private static final int CONTROL = 0xFFE7E8EB;
    private static final int CONTROL_ACTIVE = 0xFF17191E;
    private static final int ACCENT = 0xFF292C34;
    private static final int BORDER = 0xFFD9DADE;

    private final Map<Module, Float> enabledProgress = new IdentityHashMap<>();
    private final List<ModuleLayout> renderedModules = new ArrayList<>();
    private final List<SettingLayout> renderedSettings = new ArrayList<>();
    private final Map<String, AlbumTexture> albumTextures = new ConcurrentHashMap<>();
    private final Map<String, AlbumTexture> blurredAlbumTextures = new ConcurrentHashMap<>();
    private final List<Song> recommendedSongs = new ArrayList<>();
    private final List<Song> visibleSongs = new ArrayList<>();
    private final List<Playlist> recommendedPlaylists = new ArrayList<>();
    private final List<Playlist> userPlaylists = new ArrayList<>();
    private final Map<String, Long> musicButtonPressedAt = new LinkedHashMap<>();

    @Nullable
    private Module selectedModule;
    private boolean modalTargetOpen;
    private float modalProgress;
    private boolean detailTargetOpen;
    private float detailProgress;
    private long openedAtNanos;
    private long closingAtNanos;
    private long lastAnimationNanos;
    private float frameSeconds = DEFAULT_FRAME_SECONDS;
    private boolean closing;
    private boolean closingCompleted;

    @Nullable
    private SettingLayout capturedSetting;
    private SettingDrag settingDrag = SettingDrag.NONE;
    @Nullable
    private StringValue editingString;
    @Nullable
    private KeyBindValue editingKeyBind;

    private MusicPage musicPage = MusicPage.RECOMMENDED;
    @Nullable
    private Playlist selectedPlaylist;
    private String musicStatus = "正在加载推荐音乐...";
    private boolean musicLoading;
    private boolean musicInitialized;
    private boolean knownLoggedIn;
    private int homeLoadSerial;
    private int playlistLoadSerial;
    private long lastSongId = Long.MIN_VALUE;
    private float coverProgress;

    private JelloClickGui() {
        super("nyxclient:ui/screen/jello.lua", Component.empty());
    }

    @Override
    protected void init() {
        if (this.openedAtNanos == 0L || this.closing) {
            beginOpenAnimation();
        }
        super.init();
        boolean loggedIn = NeteaseMusicApi.isLoggedIn();
        if (!this.musicInitialized || loggedIn != this.knownLoggedIn) {
            loadMusicHome();
        }
    }

    public void beginOpenAnimation() {
        this.openedAtNanos = System.nanoTime();
        this.closingAtNanos = 0L;
        this.lastAnimationNanos = 0L;
        this.closing = false;
        this.closingCompleted = false;
        this.modalTargetOpen = false;
        this.modalProgress = 0.0F;
        this.selectedModule = null;
        this.detailTargetOpen = false;
        this.detailProgress = 0.0F;
        this.capturedSetting = null;
        this.settingDrag = SettingDrag.NONE;
        this.editingString = null;
        this.editingKeyBind = null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateAnimations();
        if (finishClosingIfNeeded()) {
            return;
        }
        this.renderedModules.clear();
        this.renderedSettings.clear();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void appendLuaState(Map<String, Object> state) {
        long now = System.nanoTime();
        float closeProgress = this.closing
            ? clamp((now - this.closingAtNanos) / (float)CLOSE_DURATION_NANOS, 0.0F, 1.0F)
            : 0.0F;
        float closeEase = easeOutCubic(closeProgress);
        float openAge = Math.max(0.0F, (now - this.openedAtNanos) / 1_000_000_000.0F);

        state.put("open_age", openAge);
        state.put("closing", this.closing);
        state.put("screen_visibility", 1.0F - closeEase);
        state.put("screen_scale", 1.0F + closeEase * 0.045F);
        state.put("interactive", !this.closing && openAge >= 0.30F && this.modalProgress < 0.01F);
        state.put("modal_blocking", this.selectedModule != null);
        state.put("modal_target_open", this.modalTargetOpen);
        state.put("modal_progress", this.modalProgress);
        state.put("modal_scale", modalScale());
        state.put("detail_target_open", this.detailTargetOpen);
        state.put("detail_progress", this.detailProgress);

        List<Map<String, Object>> categoryStates = new ArrayList<>();
        for (Category category : CATEGORY_ORDER) {
            Map<String, Object> categoryState = new LinkedHashMap<>();
            categoryState.put("id", category.name().toLowerCase(Locale.ROOT));
            categoryState.put("label", categoryLabel(category));
            List<Map<String, Object>> modules = new ArrayList<>();
            for (Module module : ModuleManager.getModules(category)) {
                Map<String, Object> moduleState = new LinkedHashMap<>();
                moduleState.put("index", moduleIndex(module));
                moduleState.put("name", module.getName());
                moduleState.put("description", module.getDescription());
                moduleState.put("enabled", module.isEnabled());
                moduleState.put("enabled_progress", this.enabledProgress.getOrDefault(module, module.isEnabled() ? 1.0F : 0.0F));
                modules.add(moduleState);
            }
            categoryState.put("modules", modules);
            categoryState.put("count", modules.size());
            categoryStates.add(categoryState);
        }
        state.put("categories", categoryStates);

        appendModalState(state);
        appendMusicState(state, now);
    }

    private void appendModalState(Map<String, Object> state) {
        Module module = this.selectedModule;
        if (module == null) {
            state.put("modal", Map.of());
            return;
        }

        Map<String, Object> modal = new LinkedHashMap<>();
        modal.put("module_index", moduleIndex(module));
        modal.put("name", module.getName());
        modal.put("description", module.getDescription());
        List<Map<String, Object>> values = new ArrayList<>();
        float contentHeight = 0.0F;
        List<AbstractValue<?>> moduleValues = module.getValues();
        for (int index = 0; index < moduleValues.size(); index++) {
            AbstractValue<?> value = moduleValues.get(index);
            if (!value.isVisible()) {
                continue;
            }
            float height = settingHeight(value);
            Map<String, Object> valueState = new LinkedHashMap<>();
            valueState.put("index", index + 1);
            valueState.put("height", height);
            values.add(valueState);
            contentHeight += height + 1.0F;
        }
        modal.put("values", values);
        modal.put("content_height", Math.max(0.0F, contentHeight - 1.0F));
        state.put("modal", modal);
    }

    private void appendMusicState(Map<String, Object> state, long now) {
        MusicPlaybackService player = MusicPlaybackService.INSTANCE;
        Song song = player.currentSong();
        NeteaseMusicApi.LoginSession session = NeteaseMusicApi.currentSession();

        Map<String, Object> music = new LinkedHashMap<>();
        music.put("page", this.musicPage.name().toLowerCase(Locale.ROOT));
        music.put("page_title", musicPageTitle());
        music.put("loading", this.musicLoading);
        music.put("status", this.musicStatus);
        music.put("logged_in", NeteaseMusicApi.isLoggedIn());
        music.put("account_name", session == null ? "网易云音乐" : session.nickname());

        List<Map<String, Object>> playlistStates = new ArrayList<>();
        for (int index = 0; index < this.userPlaylists.size(); index++) {
            Playlist playlist = this.userPlaylists.get(index);
            Map<String, Object> playlistState = new LinkedHashMap<>();
            playlistState.put("index", index + 1);
            playlistState.put("name", playlist.name());
            playlistState.put("selected", playlist.equals(this.selectedPlaylist));
            playlistStates.add(playlistState);
        }
        music.put("playlists", playlistStates);

        List<Map<String, Object>> recommendedPlaylistStates = new ArrayList<>();
        for (int index = 0; index < this.recommendedPlaylists.size(); index++) {
            Playlist playlist = this.recommendedPlaylists.get(index);
            Map<String, Object> playlistState = new LinkedHashMap<>();
            playlistState.put("index", index + 1);
            playlistState.put("name", playlist.name());
            playlistState.put("cover", playlist.coverUrl());
            recommendedPlaylistStates.add(playlistState);
        }
        music.put("recommended_playlists", recommendedPlaylistStates);

        List<Map<String, Object>> songStates = new ArrayList<>();
        for (int index = 0; index < this.visibleSongs.size(); index++) {
            Song visibleSong = this.visibleSongs.get(index);
            Map<String, Object> songState = new LinkedHashMap<>();
            songState.put("index", index + 1);
            songState.put("name", visibleSong.name());
            songState.put("artist", visibleSong.displayArtist());
            songState.put("cover", visibleSong.image());
            songState.put("duration", MusicPlaybackService.formatTime(visibleSong.duration()));
            songState.put("current", visibleSong.equals(song));
            songStates.add(songState);
        }
        music.put("songs", songStates);

        music.put("has_song", song != null);
        music.put("song_name", song == null ? "暂无音乐" : song.name());
        music.put("artist", song == null ? player.status() : song.displayArtist());
        music.put("cover", song == null ? "" : song.image());
        music.put("cover_progress", this.coverProgress);
        music.put("playing", player.isPlaying());
        music.put("progress", player.totalDurationMs() <= 0L
            ? 0.0F
            : clamp(player.positionMs() / (float)player.totalDurationMs(), 0.0F, 1.0F));
        music.put("position_label", MusicPlaybackService.formatTime(player.positionMs()));
        music.put("duration_label", MusicPlaybackService.formatTime(player.totalDurationMs()));
        music.put("volume", player.volume());
        music.put("volume_label", Math.round(player.volume() * 100.0F) + "%");
        music.put("previous_scale", musicButtonScale("previous", now));
        music.put("toggle_scale", musicButtonScale("toggle", now));
        music.put("next_scale", musicButtonScale("next", now));

        List<LyricLine> lyrics = player.lyricsSnapshot();
        int currentLyric = LyricLineProcessor.currentIndex(lyrics, player.positionMs());
        List<Map<String, Object>> lyricStates = new ArrayList<>();
        if (!lyrics.isEmpty()) {
            int center = Math.max(0, Math.min(currentLyric, lyrics.size() - 1));
            int start = Math.max(0, center - 4);
            int end = Math.min(lyrics.size(), center + 5);
            for (int index = start; index < end; index++) {
                Map<String, Object> lyricState = new LinkedHashMap<>();
                lyricState.put("text", lyrics.get(index).text().isBlank() ? "..." : lyrics.get(index).text());
                lyricState.put("current", index == currentLyric);
                lyricStates.add(lyricState);
            }
        }
        music.put("lyrics", lyricStates);
        music.put("lyric_empty", lyrics.isEmpty());
        state.put("music", music);
    }

    @Override
    protected boolean onLuaAction(String action, LuaValue payload) {
        MusicPlaybackService player = MusicPlaybackService.INSTANCE;
        return switch (action) {
            case "modal_close" -> {
                closeModal();
                yield true;
            }
            case "music_page" -> {
                switchMusicPage(payload.optjstring("recommended"));
                yield true;
            }
            case "music_playlist" -> {
                int index = payload.optint(-1) - 1;
                if (index >= 0 && index < this.userPlaylists.size()) {
                    loadPlaylist(this.userPlaylists.get(index), MusicPage.PLAYLIST);
                }
                yield true;
            }
            case "music_recommended_playlist" -> {
                int index = payload.optint(-1) - 1;
                if (index >= 0 && index < this.recommendedPlaylists.size()) {
                    loadPlaylist(this.recommendedPlaylists.get(index), MusicPage.PLAYLIST);
                }
                yield true;
            }
            case "music_song" -> {
                int index = payload.optint(-1) - 1;
                if (index >= 0 && index < this.visibleSongs.size()) {
                    player.setPlaylist(this.visibleSongs, index);
                    player.playSong(this.visibleSongs.get(index));
                }
                yield true;
            }
            case "music_previous" -> {
                pressMusicButton("previous");
                player.playPrevious();
                yield true;
            }
            case "music_toggle" -> {
                pressMusicButton("toggle");
                player.toggle();
                yield true;
            }
            case "music_next" -> {
                pressMusicButton("next");
                player.playNext();
                yield true;
            }
            case "music_progress" -> {
                setHorizontalMusicValue(payload, player::seekTo);
                yield true;
            }
            case "music_volume" -> {
                setVerticalMusicValue(payload, player::setVolume);
                yield true;
            }
            case "music_detail" -> {
                if (player.currentSong() != null) {
                    this.detailTargetOpen = true;
                }
                yield true;
            }
            case "music_detail_close" -> {
                this.detailTargetOpen = false;
                yield true;
            }
            case "music_refresh" -> {
                loadMusicHome();
                yield true;
            }
            case "block" -> true;
            default -> false;
        };
    }

    @Override
    protected void renderLuaCustom(String name, LuaValue[] args) {
        switch (name) {
            case "module_bounds" -> registerModuleBounds(args);
            case "setting" -> renderSetting(args);
            case "cover" -> renderCover(args);
            case "blurred_cover" -> renderBlurredCover(args);
            case "icon" -> renderIcon(args);
            case "song_title" -> renderSongTitle(args);
            default -> {
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.closing) {
            return true;
        }
        if (this.selectedModule != null) {
            SettingLayout setting = settingAt(event.x(), event.y());
            if (setting != null && handleSettingClick(setting, event.x(), event.y(), event.button())) {
                return true;
            }
            if (event.button() == GLFW_MOUSE_BUTTON_LEFT) {
                super.mouseClicked(event, doubleClick);
            }
            return true;
        }
        ModuleLayout module = moduleAt(event.x(), event.y());
        if (module != null) {
            if (event.button() == GLFW_MOUSE_BUTTON_LEFT) {
                module.module().toggle();
                return true;
            }
            if (event.button() == GLFW_MOUSE_BUTTON_RIGHT) {
                openModal(module.module());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = this.capturedSetting != null;
        this.capturedSetting = null;
        this.settingDrag = SettingDrag.NONE;
        return super.mouseReleased(event) || handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.capturedSetting != null && event.button() == GLFW_MOUSE_BUTTON_LEFT) {
            updateCapturedSetting(event.x(), event.y());
            return true;
        }
        if (this.selectedModule != null && event.button() != GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.selectedModule != null) {
            for (SettingLayout layout : this.renderedSettings) {
                if (isInsideExclusive(mouseX, mouseY, layout.clipX, layout.clipY, layout.clipWidth, layout.clipHeight)) {
                    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.editingKeyBind != null) {
            if (event.isEscape()) {
                this.editingKeyBind = null;
                return true;
            }
            if (event.key() == GLFW_KEY_BACKSPACE || event.key() == GLFW_KEY_DELETE) {
                this.editingKeyBind.setValue(-1);
            } else {
                this.editingKeyBind.setValue(event.key());
            }
            this.editingKeyBind = null;
            return true;
        }
        if (this.editingString != null && handleStringKey(this.editingString, event)) {
            return true;
        }
        if (event.isEscape()) {
            if (this.detailTargetOpen || this.detailProgress > 0.01F) {
                this.detailTargetOpen = false;
            } else if (this.selectedModule != null) {
                closeModal();
            } else {
                beginCloseAnimation();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.editingString != null && event.isAllowedChatCharacter()) {
            this.editingString.setValue(safeString(this.editingString) + event.codepointAsString());
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (this.detailTargetOpen || this.detailProgress > 0.01F) {
            this.detailTargetOpen = false;
        } else if (this.selectedModule != null) {
            closeModal();
        } else {
            beginCloseAnimation();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
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

    @Override
    public void removed() {
        this.openedAtNanos = 0L;
        this.closing = false;
        this.closingCompleted = false;
        this.capturedSetting = null;
        this.settingDrag = SettingDrag.NONE;
        this.editingString = null;
        this.editingKeyBind = null;
        super.removed();
    }

    private void updateAnimations() {
        long now = System.nanoTime();
        if (this.lastAnimationNanos == 0L) {
            this.frameSeconds = DEFAULT_FRAME_SECONDS;
        } else {
            this.frameSeconds = clamp((now - this.lastAnimationNanos) / 1_000_000_000.0F, 0.0F, MAX_FRAME_SECONDS);
        }
        this.lastAnimationNanos = now;
        for (Module module : ModuleManager.getModules()) {
            float current = this.enabledProgress.getOrDefault(module, module.isEnabled() ? 1.0F : 0.0F);
            this.enabledProgress.put(module, animateExp(current, module.isEnabled() ? 1.0F : 0.0F, 18.0F, this.frameSeconds));
        }
        this.modalProgress = animateExp(this.modalProgress, this.modalTargetOpen ? 1.0F : 0.0F, 22.0F, this.frameSeconds);
        if (!this.modalTargetOpen && this.modalProgress < 0.008F) {
            this.modalProgress = 0.0F;
            this.selectedModule = null;
            this.editingString = null;
            this.editingKeyBind = null;
        }
        this.detailProgress = animateExp(this.detailProgress, this.detailTargetOpen ? 1.0F : 0.0F, 18.0F, this.frameSeconds);
        if (!this.detailTargetOpen && this.detailProgress < 0.006F) {
            this.detailProgress = 0.0F;
        }
        Song song = MusicPlaybackService.INSTANCE.currentSong();
        long songId = song == null ? Long.MIN_VALUE : song.id();
        if (songId != this.lastSongId) {
            this.lastSongId = songId;
            this.coverProgress = 0.0F;
        }
        this.coverProgress = animateExp(this.coverProgress, song == null ? 0.0F : 1.0F, 13.0F, this.frameSeconds);
    }

    private float modalScale() {
        float eased = easeOutCubic(clamp(this.modalProgress, 0.0F, 1.0F));
        return this.modalTargetOpen ? 0.82F + eased * 0.18F : 1.0F + (1.0F - eased) * 0.05F;
    }

    private void beginCloseAnimation() {
        if (this.closing) {
            return;
        }
        this.closing = true;
        this.closingCompleted = false;
        this.closingAtNanos = System.nanoTime();
        this.capturedSetting = null;
        this.settingDrag = SettingDrag.NONE;
        this.editingString = null;
        this.editingKeyBind = null;
    }

    private boolean finishClosingIfNeeded() {
        if (!this.closing || this.closingCompleted || System.nanoTime() - this.closingAtNanos < CLOSE_DURATION_NANOS) {
            return false;
        }
        this.closingCompleted = true;
        if (this.minecraft != null && this.minecraft.screen == this) {
            this.minecraft.setScreen(null);
        }
        return true;
    }

    private void openModal(Module module) {
        this.selectedModule = module;
        this.modalTargetOpen = true;
        this.modalProgress = 0.0F;
        this.editingString = null;
        this.editingKeyBind = null;
        resetLuaScroll("jello:modal");
    }

    private void closeModal() {
        this.modalTargetOpen = false;
        this.capturedSetting = null;
        this.settingDrag = SettingDrag.NONE;
        this.editingString = null;
        this.editingKeyBind = null;
    }

    private void registerModuleBounds(LuaValue[] args) {
        Module module = moduleAt(luaInt(args, 0, -1));
        if (module == null || !luaBoolean(args, 6, false)) {
            return;
        }
        float x = luaFloat(args, 1);
        float y = luaFloat(args, 2);
        float width = luaFloat(args, 3);
        float height = luaFloat(args, 4);
        float clipY = luaFloat(args, 5);
        float clipHeight = luaFloat(args, 7, height);
        float visibleTop = Math.max(y, clipY);
        float visibleBottom = Math.min(y + height, clipY + clipHeight);
        if (visibleBottom > visibleTop) {
            this.renderedModules.add(new ModuleLayout(module, x, visibleTop, width, visibleBottom - visibleTop));
        }
    }

    private void renderSetting(LuaValue[] args) {
        Module module = moduleAt(luaInt(args, 0, -1));
        if (module == null) {
            return;
        }
        AbstractValue<?> value = valueAt(module, luaInt(args, 1, -1));
        if (value == null || !value.isVisible()) {
            return;
        }
        float x = luaFloat(args, 2);
        float y = luaFloat(args, 3);
        float width = luaFloat(args, 4);
        float height = luaFloat(args, 5, settingHeight(value));
        float clipY = luaFloat(args, 6, y);
        float clipHeight = luaFloat(args, 7, height);
        boolean interactive = luaBoolean(args, 8, false);
        SettingLayout layout = new SettingLayout(value, x, y, width, height, x, clipY, width, clipHeight);
        renderSettingValue(layout);
        float visibleTop = Math.max(y, clipY);
        float visibleBottom = Math.min(y + height, clipY + clipHeight);
        if (interactive && visibleBottom > visibleTop) {
            layout.visibleY = visibleTop;
            layout.visibleHeight = visibleBottom - visibleTop;
            this.renderedSettings.add(layout);
        }
    }

    private void renderSettingValue(SettingLayout layout) {
        AbstractValue<?> value = layout.value;
        FontRenderer labelFont = FontManager.getAppleTextRenderer(10.0F);
        FontRenderer valueFont = FontManager.getAppleTextRenderer(9.0F);
        float x = layout.x + 14.0F;
        float right = layout.x + layout.width - 14.0F;
        labelFont.drawString(trim(labelFont, value.getDisplayName(), Math.max(30.0F, layout.width * 0.48F)), x, layout.y + 10.0F, WHITE_TEXT);

        if (value instanceof BoolValue boolValue) {
            layout.primaryX = right - 34.0F;
            layout.primaryY = layout.y + 9.0F;
            layout.primaryWidth = 34.0F;
            layout.primaryHeight = 16.0F;
            Render2DUtility.drawRect(layout.primaryX, layout.primaryY, layout.primaryWidth, layout.primaryHeight,
                boolValue.getValue() ? CONTROL_ACTIVE : CONTROL);
            Render2DUtility.drawRect(boolValue.getValue() ? layout.primaryX + 19.0F : layout.primaryX + 3.0F,
                layout.primaryY + 3.0F, 12.0F, 10.0F, boolValue.getValue() ? 0xFFFFFFFF : 0xFF9A9DA5);
            return;
        }
        if (value instanceof IntValue intValue && !(value instanceof KeyBindValue)) {
            renderNumberSetting(layout, intValue.getValue(), intValue.getMin(), intValue.getMax(),
                intValue.getValue() + (intValue.isPercentageMode() ? "%" : ""));
            return;
        }
        if (value instanceof DoubleValue doubleValue) {
            String text = Math.abs(doubleValue.getStep()) >= 1.0D
                ? String.format(Locale.ROOT, "%.0f", doubleValue.getValue())
                : String.format(Locale.ROOT, "%.2f", doubleValue.getValue());
            renderNumberSetting(layout, doubleValue.getValue(), doubleValue.getMin(), doubleValue.getMax(),
                text + (doubleValue.isPercentageMode() ? "%" : ""));
            return;
        }
        if (value instanceof EnumValue<?> enumValue) {
            String current = enumValue.getValue().toString();
            float boxWidth = Math.min(layout.width * 0.42F, Math.max(66.0F, valueFont.getStringWidth(current) + 20.0F));
            layout.primaryX = right - boxWidth;
            layout.primaryY = layout.y + 7.0F;
            layout.primaryWidth = boxWidth;
            layout.primaryHeight = 20.0F;
            drawControlBox(layout.primaryX, layout.primaryY, layout.primaryWidth, layout.primaryHeight,
                trim(valueFont, current, boxWidth - 16.0F), valueFont, false);
            return;
        }
        if (value instanceof StringValue stringValue) {
            layout.primaryX = layout.x + Math.max(92.0F, layout.width * 0.38F);
            layout.primaryY = layout.y + 7.0F;
            layout.primaryWidth = right - layout.primaryX;
            layout.primaryHeight = 22.0F;
            drawControlBox(layout.primaryX, layout.primaryY, layout.primaryWidth, layout.primaryHeight,
                trimTail(valueFont, safeString(stringValue), layout.primaryWidth - 14.0F), valueFont, stringValue == this.editingString);
            return;
        }
        if (value instanceof KeyBindValue keyBindValue) {
            String keyName = keyBindValue == this.editingKeyBind ? "按下按键..." : keyName(keyBindValue.getValue());
            float boxWidth = Math.min(layout.width * 0.42F, Math.max(82.0F, valueFont.getStringWidth(keyName) + 18.0F));
            layout.primaryX = right - boxWidth;
            layout.primaryY = layout.y + 7.0F;
            layout.primaryWidth = boxWidth;
            layout.primaryHeight = 20.0F;
            drawControlBox(layout.primaryX, layout.primaryY, layout.primaryWidth, layout.primaryHeight,
                trim(valueFont, keyName, boxWidth - 12.0F), valueFont, keyBindValue == this.editingKeyBind);
            return;
        }
        if (value instanceof ButtonValue) {
            layout.primaryX = right - 60.0F;
            layout.primaryY = layout.y + 7.0F;
            layout.primaryWidth = 60.0F;
            layout.primaryHeight = 20.0F;
            drawControlBox(layout.primaryX, layout.primaryY, layout.primaryWidth, layout.primaryHeight, "运行", valueFont, false);
            return;
        }
        if (value instanceof ColorValue colorValue) {
            renderColorSetting(layout, colorValue);
            return;
        }
        String text = String.valueOf(value.getValue());
        float boxWidth = Math.min(layout.width * 0.46F, Math.max(72.0F, valueFont.getStringWidth(text) + 16.0F));
        layout.primaryX = right - boxWidth;
        layout.primaryY = layout.y + 7.0F;
        layout.primaryWidth = boxWidth;
        layout.primaryHeight = 20.0F;
        drawControlBox(layout.primaryX, layout.primaryY, layout.primaryWidth, layout.primaryHeight,
            trim(valueFont, text, boxWidth - 12.0F), valueFont, false);
    }

    private void renderNumberSetting(SettingLayout layout, double value, double min, double max, String display) {
        FontRenderer font = FontManager.getAppleTextRenderer(9.0F);
        float textWidth = Math.max(44.0F, font.getStringWidth(display) + 10.0F);
        float x = layout.x + 14.0F;
        float right = layout.x + layout.width - 14.0F;
        layout.primaryX = x;
        layout.primaryY = layout.y + layout.height - 11.0F;
        layout.primaryWidth = Math.max(30.0F, right - textWidth - 10.0F - x);
        layout.primaryHeight = 5.0F;
        float progress = max == min ? 0.0F : clamp((float)((value - min) / (max - min)), 0.0F, 1.0F);
        Render2DUtility.drawRect(layout.primaryX, layout.primaryY, layout.primaryWidth, 3.0F, CONTROL);
        Render2DUtility.drawRect(layout.primaryX, layout.primaryY, layout.primaryWidth * progress, 3.0F, CONTROL_ACTIVE);
        Render2DUtility.drawRect(layout.primaryX + layout.primaryWidth * progress - 2.0F, layout.primaryY - 2.0F, 4.0F, 7.0F, ACCENT);
        font.drawCenteredString(display, right - textWidth * 0.5F, layout.y + layout.height - 16.0F, MUTED_TEXT);
    }

    private void renderColorSetting(SettingLayout layout, ColorValue colorValue) {
        Color color = colorValue.getValue();
        float x = layout.x + 14.0F;
        float right = layout.x + layout.width - 14.0F;
        float previewSize = 20.0F;
        Render2DUtility.drawRect(right - previewSize, layout.y + 7.0F, previewSize, previewSize,
            Render2DUtility.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()));
        drawBorder(right - previewSize, layout.y + 7.0F, previewSize, previewSize, 0xFFBFC1C7);
        layout.primaryX = x;
        layout.primaryY = layout.y + 34.0F;
        layout.primaryWidth = layout.width - 28.0F;
        layout.primaryHeight = 7.0F;
        int[] rainbow = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
        float segment = layout.primaryWidth / (rainbow.length - 1);
        for (int index = 0; index < rainbow.length - 1; index++) {
            Render2DUtility.drawHorizontalGradientRect(layout.primaryX + segment * index, layout.primaryY,
                segment + 0.5F, layout.primaryHeight, rainbow[index], rainbow[index + 1]);
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        Render2DUtility.drawRect(layout.primaryX + layout.primaryWidth * hsb[0] - 1.0F,
            layout.primaryY - 2.0F, 3.0F, layout.primaryHeight + 4.0F, 0xFFFFFFFF);
        if (colorValue.isAllowAlpha()) {
            layout.secondaryX = x;
            layout.secondaryY = layout.y + 50.0F;
            layout.secondaryWidth = layout.primaryWidth;
            layout.secondaryHeight = 6.0F;
            int transparent = Render2DUtility.rgba(color.getRed(), color.getGreen(), color.getBlue(), 0);
            int opaque = Render2DUtility.rgba(color.getRed(), color.getGreen(), color.getBlue(), 255);
            Render2DUtility.drawHorizontalGradientRect(layout.secondaryX, layout.secondaryY,
                layout.secondaryWidth, layout.secondaryHeight, transparent, opaque);
            float alpha = color.getAlpha() / 255.0F;
            Render2DUtility.drawRect(layout.secondaryX + layout.secondaryWidth * alpha - 1.0F,
                layout.secondaryY - 2.0F, 3.0F, layout.secondaryHeight + 4.0F, CONTROL_ACTIVE);
        }
    }

    private void drawControlBox(float x, float y, float width, float height, String text, FontRenderer font, boolean focused) {
        Render2DUtility.drawRect(x, y, width, height, focused ? 0xFFD8DADE : 0xFFF0F1F3);
        drawBorder(x, y, width, height, focused ? 0xFF8D919A : BORDER);
        font.drawCenteredString(text, x + width * 0.5F, y + (height - font.getLineHeight()) * 0.5F,
            focused ? WHITE_TEXT : MUTED_TEXT);
    }

    private static void drawBorder(float x, float y, float width, float height, int color) {
        Render2DUtility.drawRect(x, y, width, 1.0F, color);
        Render2DUtility.drawRect(x, y + height - 1.0F, width, 1.0F, color);
        Render2DUtility.drawRect(x, y, 1.0F, height, color);
        Render2DUtility.drawRect(x + width - 1.0F, y, 1.0F, height, color);
    }

    private boolean handleSettingClick(SettingLayout layout, double mouseX, double mouseY, int button) {
        AbstractValue<?> value = layout.value;
        if (value instanceof BoolValue boolValue && button == GLFW_MOUSE_BUTTON_LEFT) {
            boolValue.setValue(!boolValue.getValue());
            clearValueEditors();
            return true;
        }
        if (value instanceof IntValue && !(value instanceof KeyBindValue)
            && button == GLFW_MOUSE_BUTTON_LEFT && layout.insidePrimary(mouseX, mouseY, 5.0F)) {
            captureSetting(layout, SettingDrag.NUMBER, mouseX, mouseY);
            return true;
        }
        if (value instanceof DoubleValue
            && button == GLFW_MOUSE_BUTTON_LEFT && layout.insidePrimary(mouseX, mouseY, 5.0F)) {
            captureSetting(layout, SettingDrag.NUMBER, mouseX, mouseY);
            return true;
        }
        if (value instanceof EnumValue<?> enumValue && layout.insidePrimary(mouseX, mouseY, 0.0F)) {
            cycleEnum(enumValue, button == GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
            clearValueEditors();
            return true;
        }
        if (value instanceof StringValue stringValue && button == GLFW_MOUSE_BUTTON_LEFT
            && layout.insidePrimary(mouseX, mouseY, 0.0F)) {
            this.editingString = stringValue;
            this.editingKeyBind = null;
            return true;
        }
        if (value instanceof KeyBindValue keyBindValue && button == GLFW_MOUSE_BUTTON_LEFT
            && layout.insidePrimary(mouseX, mouseY, 0.0F)) {
            this.editingKeyBind = keyBindValue;
            this.editingString = null;
            return true;
        }
        if (value instanceof ButtonValue buttonValue && layout.insidePrimary(mouseX, mouseY, 0.0F)) {
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                buttonValue.rightClick();
            } else if (button == GLFW_MOUSE_BUTTON_LEFT) {
                buttonValue.press();
            }
            clearValueEditors();
            return true;
        }
        if (value instanceof ColorValue && button == GLFW_MOUSE_BUTTON_LEFT) {
            if (layout.insidePrimary(mouseX, mouseY, 3.0F)) {
                captureSetting(layout, SettingDrag.COLOR_HUE, mouseX, mouseY);
                return true;
            }
            if (layout.insideSecondary(mouseX, mouseY, 3.0F)) {
                captureSetting(layout, SettingDrag.COLOR_ALPHA, mouseX, mouseY);
                return true;
            }
        }
        clearValueEditors();
        return isInsideExclusive(mouseX, mouseY, layout.x, layout.visibleY, layout.width, layout.visibleHeight);
    }

    private void captureSetting(SettingLayout layout, SettingDrag drag, double mouseX, double mouseY) {
        this.capturedSetting = layout;
        this.settingDrag = drag;
        clearValueEditors();
        updateCapturedSetting(mouseX, mouseY);
    }

    private void updateCapturedSetting(double mouseX, double mouseY) {
        SettingLayout layout = this.capturedSetting;
        if (layout == null) {
            return;
        }
        switch (this.settingDrag) {
            case NUMBER -> updateNumberValue(layout, mouseX);
            case COLOR_HUE -> updateColorHue(layout, mouseX);
            case COLOR_ALPHA -> updateColorAlpha(layout, mouseX);
            case NONE -> {
            }
        }
    }

    private void updateNumberValue(SettingLayout layout, double mouseX) {
        float progress = clamp((float)((mouseX - layout.primaryX) / Math.max(1.0F, layout.primaryWidth)), 0.0F, 1.0F);
        if (layout.value instanceof IntValue intValue) {
            int step = Math.max(1, Math.abs(intValue.getStep()));
            int range = intValue.getMax() - intValue.getMin();
            intValue.setValue(intValue.getMin() + Math.round(progress * range / step) * step);
        } else if (layout.value instanceof DoubleValue doubleValue) {
            double raw = doubleValue.getMin() + (doubleValue.getMax() - doubleValue.getMin()) * progress;
            double step = Math.abs(doubleValue.getStep());
            if (step > 0.0D) {
                raw = doubleValue.getMin() + Math.round((raw - doubleValue.getMin()) / step) * step;
            }
            doubleValue.setValue(raw);
        }
    }

    private void updateColorHue(SettingLayout layout, double mouseX) {
        if (!(layout.value instanceof ColorValue colorValue)) {
            return;
        }
        Color current = colorValue.getValue();
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        float hue = clamp((float)((mouseX - layout.primaryX) / Math.max(1.0F, layout.primaryWidth)), 0.0F, 1.0F);
        int rgb = Color.HSBtoRGB(hue, Math.max(0.45F, hsb[1]), Math.max(0.72F, hsb[2]));
        colorValue.setValue(new Color((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF, current.getAlpha()));
    }

    private void updateColorAlpha(SettingLayout layout, double mouseX) {
        if (!(layout.value instanceof ColorValue colorValue) || !colorValue.isAllowAlpha()) {
            return;
        }
        Color current = colorValue.getValue();
        int alpha = Math.round(clamp((float)((mouseX - layout.secondaryX) / Math.max(1.0F, layout.secondaryWidth)), 0.0F, 1.0F) * 255.0F);
        colorValue.setValue(new Color(current.getRed(), current.getGreen(), current.getBlue(), alpha));
    }

    private void clearValueEditors() {
        this.editingString = null;
        this.editingKeyBind = null;
    }

    private boolean handleStringKey(StringValue value, KeyEvent event) {
        if (event.isEscape() || event.key() == GLFW_KEY_ENTER || event.key() == GLFW_KEY_KP_ENTER) {
            this.editingString = null;
            return true;
        }
        if (event.isPaste()) {
            String clipboard = this.minecraft == null ? "" : this.minecraft.keyboardHandler.getClipboard();
            value.setValue(safeString(value) + clipboard);
            return true;
        }
        if (event.isSelectAll()) {
            value.setValue("");
            return true;
        }
        if (event.key() == GLFW_KEY_BACKSPACE || event.key() == GLFW_KEY_DELETE) {
            value.setValue(deleteLastCodePoint(safeString(value)));
            return true;
        }
        return false;
    }

    private void switchMusicPage(String id) {
        if ("favorites".equalsIgnoreCase(id)) {
            if (!NeteaseMusicApi.isLoggedIn() || this.userPlaylists.isEmpty()) {
                this.musicPage = MusicPage.FAVORITES;
                this.selectedPlaylist = null;
                this.visibleSongs.clear();
                this.musicStatus = "登录网易云后可读取喜欢的音乐";
                return;
            }
            loadPlaylist(this.userPlaylists.getFirst(), MusicPage.FAVORITES);
            return;
        }
        this.musicPage = MusicPage.RECOMMENDED;
        this.selectedPlaylist = null;
        this.visibleSongs.clear();
        this.visibleSongs.addAll(this.recommendedSongs);
        this.musicStatus = this.visibleSongs.isEmpty() ? "暂无推荐音乐" : "为你推荐 " + this.visibleSongs.size() + " 首歌曲";
        resetLuaScroll("jello:music:songs");
    }

    private void loadMusicHome() {
        int serial = ++this.homeLoadSerial;
        this.musicLoading = true;
        this.musicStatus = "正在加载推荐音乐...";
        CompletableFuture.supplyAsync(() -> {
            if (!NeteaseMusicApi.isLoggedIn() && NeteaseMusicApi.hasSavedSession()) {
                try {
                    NeteaseMusicApi.restoreSession();
                } catch (Exception ignored) {
                }
            }
            List<Song> songs = safeLoadSongs();
            List<Playlist> recommendations = safeLoadRecommendedPlaylists();
            List<Playlist> user = NeteaseMusicApi.isLoggedIn() ? safeLoadUserPlaylists() : List.of();
            return new MusicHomeData(songs, recommendations, user, NeteaseMusicApi.isLoggedIn());
        }, MUSIC_IO).whenComplete((data, throwable) -> Minecraft.getInstance().execute(() -> {
            if (serial != this.homeLoadSerial) {
                return;
            }
            this.musicLoading = false;
            this.musicInitialized = true;
            if (throwable != null || data == null) {
                this.musicStatus = "音乐数据加载失败";
                return;
            }
            this.knownLoggedIn = data.loggedIn();
            this.recommendedSongs.clear();
            this.recommendedSongs.addAll(data.songs());
            this.recommendedPlaylists.clear();
            this.recommendedPlaylists.addAll(data.recommendedPlaylists());
            this.userPlaylists.clear();
            this.userPlaylists.addAll(data.userPlaylists());
            if (this.musicPage == MusicPage.RECOMMENDED) {
                this.visibleSongs.clear();
                this.visibleSongs.addAll(this.recommendedSongs);
                this.musicStatus = this.visibleSongs.isEmpty() ? "暂无推荐音乐" : "为你推荐 " + this.visibleSongs.size() + " 首歌曲";
            }
        }));
    }

    private void loadPlaylist(Playlist playlist, MusicPage targetPage) {
        int serial = ++this.playlistLoadSerial;
        this.musicPage = targetPage;
        this.selectedPlaylist = playlist;
        this.musicLoading = true;
        this.musicStatus = "正在加载 " + playlist.name() + "...";
        resetLuaScroll("jello:music:songs");
        CompletableFuture.supplyAsync(() -> {
            try {
                return NeteaseMusicApi.getPlaylistDetail(playlist.id());
            } catch (Exception exception) {
                return List.<Song>of();
            }
        }, MUSIC_IO).whenComplete((songs, throwable) -> Minecraft.getInstance().execute(() -> {
            if (serial != this.playlistLoadSerial) {
                return;
            }
            this.musicLoading = false;
            this.visibleSongs.clear();
            if (throwable == null && songs != null) {
                this.visibleSongs.addAll(songs);
            }
            this.musicStatus = this.visibleSongs.isEmpty()
                ? "歌单中没有可播放歌曲"
                : playlist.name() + " · " + this.visibleSongs.size() + " 首";
        }));
    }

    private static List<Song> safeLoadSongs() {
        try {
            return NeteaseMusicApi.getTopNewSongs();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<Playlist> safeLoadRecommendedPlaylists() {
        try {
            return NeteaseMusicApi.getRecommendPlaylists(4);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<Playlist> safeLoadUserPlaylists() {
        try {
            return NeteaseMusicApi.getUserPlaylists();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String musicPageTitle() {
        return switch (this.musicPage) {
            case RECOMMENDED -> "推荐音乐";
            case FAVORITES -> "喜欢的音乐";
            case PLAYLIST -> this.selectedPlaylist == null ? "我的歌单" : this.selectedPlaylist.name();
        };
    }

    private void pressMusicButton(String name) {
        this.musicButtonPressedAt.put(name, System.nanoTime());
    }

    private float musicButtonScale(String name, long now) {
        Long started = this.musicButtonPressedAt.get(name);
        if (started == null) {
            return 1.0F;
        }
        float seconds = Math.max(0.0F, (now - started) / 1_000_000_000.0F);
        if (seconds > 0.48F) {
            this.musicButtonPressedAt.remove(name);
            return 1.0F;
        }
        return 1.0F - 0.12F * (float)Math.exp(-9.5F * seconds) * (float)Math.cos(25.0F * seconds);
    }

    private void setHorizontalMusicValue(LuaValue payload, FloatConsumer consumer) {
        float x = (float)payload.get("x").optdouble(0.0D);
        float width = (float)payload.get("width").optdouble(0.0D);
        if (width > 0.0F) {
            consumer.accept(clamp((luaMouseX() - x) / width, 0.0F, 1.0F));
        }
    }

    private void setVerticalMusicValue(LuaValue payload, FloatConsumer consumer) {
        float y = (float)payload.get("y").optdouble(0.0D);
        float height = (float)payload.get("height").optdouble(0.0D);
        if (height > 0.0F) {
            consumer.accept(1.0F - clamp((luaMouseY() - y) / height, 0.0F, 1.0F));
        }
    }

    private void renderCover(LuaValue[] args) {
        String url = luaString(args, 0, "");
        float x = luaFloat(args, 1);
        float y = luaFloat(args, 2);
        float width = luaFloat(args, 3);
        float height = luaFloat(args, 4, width);
        float alpha = luaFloat(args, 5, 1.0F);
        Render2DUtility.drawRect(x, y, width, height, Render2DUtility.applyOpacity(0xFF1B1D23, alpha));
        if (url.isBlank()) {
            drawIcon(Icon.MUSIC, x + width * 0.34F, y + height * 0.34F, width * 0.32F, height * 0.32F,
                Render2DUtility.applyOpacity(0xFFB9BDC8, alpha));
            return;
        }
        AlbumTexture texture = this.albumTextures.computeIfAbsent(url, key -> requestAlbumTexture(key, 192, false));
        if (texture.texture != null) {
            Render2DUtility.drawTexture(texture.texture.getTextureView(), x, y, width, height,
                Render2DUtility.applyOpacity(0xFFFFFFFF, alpha));
        }
    }

    private void renderBlurredCover(LuaValue[] args) {
        String url = luaString(args, 0, "");
        float x = luaFloat(args, 1);
        float y = luaFloat(args, 2);
        float width = luaFloat(args, 3);
        float height = luaFloat(args, 4);
        float alpha = luaFloat(args, 5, 1.0F);
        if (url.isBlank()) {
            Render2DUtility.drawHorizontalGradientRect(x, y, width, height,
                Render2DUtility.applyOpacity(0xFF17191F, alpha), Render2DUtility.applyOpacity(0xFF0D0F13, alpha));
            return;
        }
        AlbumTexture texture = this.blurredAlbumTextures.computeIfAbsent(url, key -> requestAlbumTexture(key, 256, true));
        if (texture.texture == null) {
            Render2DUtility.drawRect(x, y, width, height, Render2DUtility.applyOpacity(0xFF101217, alpha));
            return;
        }
        Render2DUtility.withClip(x, y, width, height, () -> {
            float textureSize = Math.max(width, height);
            float textureY = y - (textureSize - height) * 0.5F;
            Render2DUtility.drawTexture(texture.texture.getTextureView(), x, textureY, width, textureSize,
                Render2DUtility.applyOpacity(0xFFFFFFFF, alpha));
        });
    }

    private void renderIcon(LuaValue[] args) {
        try {
            Icon icon = Icon.valueOf(luaString(args, 0, "music").toUpperCase(Locale.ROOT));
            drawIcon(icon, luaFloat(args, 1), luaFloat(args, 2), luaFloat(args, 3), luaFloat(args, 4), luaInt(args, 5, 0xFFFFFFFF));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void renderSongTitle(LuaValue[] args) {
        String text = luaString(args, 0, "");
        float x = luaFloat(args, 1);
        float y = luaFloat(args, 2);
        float width = luaFloat(args, 3);
        float size = luaFloat(args, 4, 9.0F);
        int color = luaInt(args, 5, 0xFFFFFFFF);
        FontRenderer font = FontManager.getAppleTextRenderer(size);
        List<String> lines = wrapTwoLines(font, text, width);
        for (int index = 0; index < lines.size(); index++) {
            font.drawCenteredString(lines.get(index), x + width * 0.5F, y + index * (font.getLineHeight() + 1.0F), color);
        }
    }

    private void drawIcon(Icon icon, float x, float y, float width, float height, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        Render2DUtility.drawTexture(minecraft.getTextureManager().getTexture(icon.texture).getTextureView(), x, y, width, height, color);
    }

    private AlbumTexture requestAlbumTexture(String url, int size, boolean blurred) {
        AlbumTexture target = new AlbumTexture();
        CompletableFuture.supplyAsync(() -> downloadAlbumTexture(url, size, blurred), MUSIC_IO).whenComplete((image, throwable) -> {
            if (throwable != null || image == null) {
                target.failed = true;
                return;
            }
            Minecraft.getInstance().execute(() -> target.texture = new DynamicTexture(
                () -> "nyx-jello-cover-" + TEXTURE_IDS.incrementAndGet(), toNativeImage(image)));
        });
        return target;
    }

    @Nullable
    private static BufferedImage downloadAlbumTexture(String url, int size, boolean blurred) {
        try {
            byte[] bytes = url.startsWith("data:image/") ? decodeDataImage(url) : WebUtility.getBytes(url);
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) {
                return null;
            }
            BufferedImage scaled = scaleImage(source, size);
            return blurred ? gaussianBlur(scaled, 10) : scaled;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] decodeDataImage(String url) {
        int comma = url.indexOf(',');
        return comma < 0 ? new byte[0] : Base64.getDecoder().decode(url.substring(comma + 1));
    }

    private static BufferedImage scaleImage(BufferedImage source, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float scale = Math.max(size / (float)source.getWidth(), size / (float)source.getHeight());
            int drawWidth = Math.round(source.getWidth() * scale);
            int drawHeight = Math.round(source.getHeight() * scale);
            graphics.drawImage(source, (size - drawWidth) / 2, (size - drawHeight) / 2, drawWidth, drawHeight, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage gaussianBlur(BufferedImage source, int radius) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] input = source.getRGB(0, 0, width, height, null, 0, width);
        int[] horizontal = new int[input.length];
        int[] output = new int[input.length];
        float sigma = Math.max(1.0F, radius / 2.5F);
        float[] kernel = new float[radius * 2 + 1];
        float sum = 0.0F;
        for (int index = -radius; index <= radius; index++) {
            float value = (float)Math.exp(-(index * index) / (2.0F * sigma * sigma));
            kernel[index + radius] = value;
            sum += value;
        }
        for (int index = 0; index < kernel.length; index++) {
            kernel[index] /= sum;
        }
        blurPass(input, horizontal, width, height, kernel, radius, true);
        blurPass(horizontal, output, width, height, kernel, radius, false);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, output, 0, width);
        return image;
    }

    private static void blurPass(int[] input, int[] output, int width, int height, float[] kernel, int radius, boolean horizontal) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float alpha = 0.0F;
                float red = 0.0F;
                float green = 0.0F;
                float blue = 0.0F;
                for (int offset = -radius; offset <= radius; offset++) {
                    int sampleX = horizontal ? Math.max(0, Math.min(width - 1, x + offset)) : x;
                    int sampleY = horizontal ? y : Math.max(0, Math.min(height - 1, y + offset));
                    int color = input[sampleY * width + sampleX];
                    float weight = kernel[offset + radius];
                    alpha += ((color >>> 24) & 0xFF) * weight;
                    red += ((color >>> 16) & 0xFF) * weight;
                    green += ((color >>> 8) & 0xFF) * weight;
                    blue += (color & 0xFF) * weight;
                }
                output[y * width + x] = Math.round(alpha) << 24 | Math.round(red) << 16
                    | Math.round(green) << 8 | Math.round(blue);
            }
        }
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

    @Nullable
    private ModuleLayout moduleAt(double mouseX, double mouseY) {
        for (int index = this.renderedModules.size() - 1; index >= 0; index--) {
            ModuleLayout layout = this.renderedModules.get(index);
            if (isInsideExclusive(mouseX, mouseY, layout.x, layout.y, layout.width, layout.height)) {
                return layout;
            }
        }
        return null;
    }

    @Nullable
    private SettingLayout settingAt(double mouseX, double mouseY) {
        for (int index = this.renderedSettings.size() - 1; index >= 0; index--) {
            SettingLayout layout = this.renderedSettings.get(index);
            if (isInsideExclusive(mouseX, mouseY, layout.x, layout.visibleY, layout.width, layout.visibleHeight)) {
                return layout;
            }
        }
        return null;
    }

    private static void cycleEnum(EnumValue<?> value, int direction) {
        Enum<?>[] modes = value.getModes();
        if (modes.length == 0) {
            return;
        }
        int index = value.getModeIndex();
        setEnumValue(value, modes[Math.floorMod(index + direction, modes.length)]);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnumValue(EnumValue value, Enum<?> next) {
        value.setValue(next);
    }

    private static float settingHeight(AbstractValue<?> value) {
        if (value instanceof ColorValue) {
            return 68.0F;
        }
        if (value instanceof IntValue && !(value instanceof KeyBindValue) || value instanceof DoubleValue) {
            return 42.0F;
        }
        return 34.0F;
    }

    private static int moduleIndex(Module target) {
        List<Module> modules = List.copyOf(ModuleManager.getModules());
        int index = modules.indexOf(target);
        return index < 0 ? -1 : index + 1;
    }

    @Nullable
    private static Module moduleAt(int index) {
        List<Module> modules = List.copyOf(ModuleManager.getModules());
        return index <= 0 || index > modules.size() ? null : modules.get(index - 1);
    }

    @Nullable
    private static AbstractValue<?> valueAt(Module module, int index) {
        List<AbstractValue<?>> values = module.getValues();
        return index <= 0 || index > values.size() ? null : values.get(index - 1);
    }

    private static String categoryLabel(Category category) {
        String name = category.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String keyName(int key) {
        if (key < 0) {
            return "未绑定";
        }
        String name = glfwGetKeyName(key, 0);
        return name == null || name.isBlank() ? "Key " + key : name.toUpperCase(Locale.ROOT);
    }

    private static String safeString(StringValue value) {
        return value.getValue() == null ? "" : value.getValue();
    }

    private static String deleteLastCodePoint(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, text.offsetByCodePoints(text.length(), -1));
    }

    private static List<String> wrapTwoLines(FontRenderer font, String text, float width) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        if (font.getStringWidth(text) <= width) {
            return List.of(text);
        }
        int split = 0;
        int index = 0;
        while (index < text.length()) {
            int next = text.offsetByCodePoints(index, 1);
            if (font.getStringWidth(text.substring(0, next)) > width) {
                break;
            }
            split = next;
            index = next;
        }
        if (split <= 0) {
            return List.of(trim(font, text, width));
        }
        return List.of(text.substring(0, split), trim(font, text.substring(split), width));
    }

    private static String trim(FontRenderer font, String text, float width) {
        if (text == null || text.isEmpty() || width <= 0.0F) {
            return "";
        }
        if (font.getStringWidth(text) <= width) {
            return text;
        }
        String suffix = "...";
        int end = text.length();
        while (end > 0 && font.getStringWidth(text.substring(0, end)) + font.getStringWidth(suffix) > width) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + suffix;
    }

    private static String trimTail(FontRenderer font, String text, float width) {
        if (text == null || text.isEmpty() || font.getStringWidth(text) <= width) {
            return text == null ? "" : text;
        }
        int start = 0;
        while (start < text.length() && font.getStringWidth("..." + text.substring(start)) > width) {
            start++;
        }
        return "..." + text.substring(Math.min(start, text.length()));
    }

    private static float luaFloat(LuaValue[] args, int index) {
        return luaFloat(args, index, 0.0F);
    }

    private static float luaFloat(LuaValue[] args, int index, float fallback) {
        return index >= 0 && index < args.length ? (float)args[index].optdouble(fallback) : fallback;
    }

    private static int luaInt(LuaValue[] args, int index, int fallback) {
        return index >= 0 && index < args.length ? args[index].optint(fallback) : fallback;
    }

    private static boolean luaBoolean(LuaValue[] args, int index, boolean fallback) {
        return index >= 0 && index < args.length ? args[index].optboolean(fallback) : fallback;
    }

    private static String luaString(LuaValue[] args, int index, String fallback) {
        return index >= 0 && index < args.length ? args[index].optjstring(fallback) : fallback;
    }

    private enum MusicPage {
        RECOMMENDED,
        FAVORITES,
        PLAYLIST
    }

    private enum SettingDrag {
        NONE,
        NUMBER,
        COLOR_HUE,
        COLOR_ALPHA
    }

    private enum Icon {
        MUSIC("music_note"),
        HOME("home"),
        LIST("list"),
        PLAY("play"),
        PAUSE("pause"),
        PREVIOUS("previous"),
        NEXT("next"),
        VOLUME("volume"),
        REPEAT("repeat"),
        SHUFFLE("shuffle");

        private final Identifier texture;

        Icon(String fileName) {
            this.texture = Identifier.fromNamespaceAndPath("nyxclient", "ui/icon/music/" + fileName + ".png");
        }
    }

    @FunctionalInterface
    private interface FloatConsumer {
        void accept(float value);
    }

    private static final class AlbumTexture {
        @Nullable
        private DynamicTexture texture;
        private boolean failed;
    }

    private record ModuleLayout(Module module, float x, float y, float width, float height) {
    }

    private static final class SettingLayout {
        private final AbstractValue<?> value;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float clipX;
        private final float clipY;
        private final float clipWidth;
        private final float clipHeight;
        private float visibleY;
        private float visibleHeight;
        private float primaryX;
        private float primaryY;
        private float primaryWidth;
        private float primaryHeight;
        private float secondaryX;
        private float secondaryY;
        private float secondaryWidth;
        private float secondaryHeight;

        private SettingLayout(AbstractValue<?> value, float x, float y, float width, float height,
                              float clipX, float clipY, float clipWidth, float clipHeight) {
            this.value = value;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.clipX = clipX;
            this.clipY = clipY;
            this.clipWidth = clipWidth;
            this.clipHeight = clipHeight;
        }

        private boolean insidePrimary(double mouseX, double mouseY, float padding) {
            return isInsideExclusive(mouseX, mouseY, this.primaryX - padding, this.primaryY - padding,
                this.primaryWidth + padding * 2.0F, this.primaryHeight + padding * 2.0F);
        }

        private boolean insideSecondary(double mouseX, double mouseY, float padding) {
            return this.secondaryWidth > 0.0F && isInsideExclusive(mouseX, mouseY,
                this.secondaryX - padding, this.secondaryY - padding,
                this.secondaryWidth + padding * 2.0F, this.secondaryHeight + padding * 2.0F);
        }
    }

    private record MusicHomeData(
        List<Song> songs,
        List<Playlist> recommendedPlaylists,
        List<Playlist> userPlaylists,
        boolean loggedIn
    ) {
    }
}
