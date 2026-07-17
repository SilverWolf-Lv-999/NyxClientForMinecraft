package io.github.seraphina.nyx.client.ui.player;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.ui.LuaScreen;
import io.github.seraphina.nyx.client.ui.mainui.MainUI;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.WarningScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

import static io.github.seraphina.nyx.client.utility.MathUtility.animateExp;
import static io.github.seraphina.nyx.client.utility.MathUtility.clamp;
import static io.github.seraphina.nyx.client.utility.MathUtility.easeInCubic;
import static io.github.seraphina.nyx.client.utility.MathUtility.easeInOutCubic;
import static io.github.seraphina.nyx.client.utility.MathUtility.easeOutBack;
import static io.github.seraphina.nyx.client.utility.MathUtility.easeOutCubic;
import static io.github.seraphina.nyx.client.utility.MathUtility.isInside;
import static io.github.seraphina.nyx.client.utility.MathUtility.lerp;
import static io.github.seraphina.nyx.client.utility.MathUtility.phase;
import static io.github.seraphina.nyx.client.utility.MathUtility.stackedContentHeight;
import static io.github.seraphina.nyx.client.utility.MathUtility.stackedItemY;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F5;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public final class MutiPlayerUI extends LuaScreen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadPoolExecutor SERVER_PING_POOL = new ScheduledThreadPoolExecutor(
        5,
        new ThreadFactoryBuilder()
            .setNameFormat("Nyx Server Pinger #%d")
            .setDaemon(true)
            .build()
    );

    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;
    private static final float TRANSITION_SECONDS = 0.92F;
    private static final float HOVER_SPEED = 16.0F;
    private static final float SCROLL_STEP = 38.0F;
    private static final float FLIP_DEGREES = 180.0F;
    private static final float FLIP_EDGE_MIN_SCALE = 0.075F;

    private static final float MAIN_PANEL_MAX_WIDTH = 301.0F;
    private static final float MAIN_PANEL_MIN_WIDTH = 175.0F;
    private static final float MAIN_PANEL_MAX_HEIGHT = 312.0F;
    private static final float MAIN_PANEL_MIN_HEIGHT = 180.0F;
    private static final float PANEL_MAX_WIDTH = 620.0F;
    private static final float PANEL_MIN_WIDTH = 320.0F;
    private static final float PANEL_MAX_HEIGHT = 388.0F;
    private static final float PANEL_MIN_HEIGHT = 184.0F;
    private static final float TARGET_PANEL_WIDTH_SCALE = 0.8F;
    private static final float TARGET_PANEL_HEIGHT_SCALE = 1.1F;
    private static final float PANEL_RADIUS = 18.0F;
    private static final float PANEL_BLUR_RADIUS = 18.0F;
    private static final float PANEL_BORDER_WIDTH = 3.0F;
    private static final float PANEL_PADDING = 16.0F;
    private static final float HEADER_HEIGHT = 58.0F;
    private static final float ROW_HEIGHT = 58.0F;
    private static final float ROW_GAP = 8.0F;
    private static final float ICON_SIZE = 42.0F;
    private static final float ACTION_BUTTON_HEIGHT = 30.0F;
    private static final float ACTION_BUTTON_GAP = 8.0F;
    private static final float ACTION_BUTTON_TOP_GAP = 14.0F;

    private static final float MAIN_BUTTON_HEIGHT = 34.0F;
    private static final float MAIN_BUTTON_MIN_HEIGHT = 28.0F;
    private static final float MAIN_BUTTON_GAP = 10.0F;
    private static final float MAIN_BUTTON_MIN_GAP = 7.0F;
    private static final float MAIN_BUTTON_MAX_INSET = 32.0F;
    private static final float MAIN_BUTTON_MIN_INSET = 18.0F;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFE2E6EF;
    private static final int TEXT_SUBTLE = 0xFFA8AFBE;
    private static final int TEXT_DIM = 0xFF687181;
    private static final int TEXT_DISABLED = 0xFF687181;
    private static final int TITLE_SHADOW = 0xAA000000;
    private static final int PANEL_BLUR = 0xE6FFFFFF;
    private static final int PANEL_BACKGROUND = 0xB80A0C12;
    private static final int PANEL_BORDER = 0x66FFFFFF;
    private static final int ROW_BACKGROUND = 0x9913161E;
    private static final int ROW_HOVER = 0xBB1A1E29;
    private static final int ROW_SELECTED = 0x993D81F7;
    private static final int ROW_DISABLED = 0x77333642;
    private static final int CONTROL_BACKGROUND = 0xAA0E1118;
    private static final int CONTROL_HOVER = 0xD7191D28;
    private static final int CONTROL_BORDER = 0x22FFFFFF;
    private static final int CONTROL_BORDER_HOVER = 0x663D81F7;
    private static final int ACCENT = 0xFF3D81F7;

    private static final String MAIN_TITLE = "Nyx Client";
    private static final String MULTIPLAYER_TITLE = "Muti Player";
    private static final String[] MAIN_BUTTON_LABELS = {"Single Player", "Muti Player", "Alt Manager", "Option", "Exit"};
    private static final Component CANT_RESOLVE_TEXT = Component.translatable("multiplayer.status.cannot_resolve").withStyle(ChatFormatting.RED);
    private static final Component CANT_CONNECT_TEXT = Component.translatable("multiplayer.status.cannot_connect").withStyle(ChatFormatting.RED);

    private final Screen lastScreen;
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final List<ServerEntry> onlineEntries = new ArrayList<>();
    private final List<LanServer> lanServers = new ArrayList<>();
    private final List<ServerEntry> entries = new ArrayList<>();
    private final List<ActionButton> actionButtons = new ArrayList<>();

    private @Nullable ServerList servers;
    private LanServerDetection.LanServerList lanServerList;
    private LanServerDetection.LanServerDetector lanServerDetector;
    private @Nullable ServerData editingServer;
    private @Nullable ServerData editingOriginalServer;
    private @Nullable ServerData deletingServer;
    private @Nullable String selectedKey;
    private int selectedIndex = -1;
    private float scroll;
    private float maxScroll;
    private long lastFrameNanos;
    private float frameSeconds = DEFAULT_FRAME_SECONDS;
    private float transitionProgress;
    private boolean exiting;
    private boolean switchingBack;
    private boolean serversLoaded;

    public MutiPlayerUI(Screen lastScreen) {
        super("nyxclient:ui/screen/multiplayer.lua", Component.translatable("multiplayer.title"));
        this.lastScreen = lastScreen;
        initActionButtons();
    }

    public static void open(Screen lastScreen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !minecraft.allowsMultiplayer()) {
            return;
        }

        if (minecraft.options.skipMultiplayerWarning) {
            minecraft.setScreen(new MutiPlayerUI(lastScreen));
        } else {
            minecraft.setScreen(new MutiPlayerSafetyScreen(lastScreen));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.lastFrameNanos = 0L;
        if (!this.serversLoaded) {
            loadServers();
            startLanDiscovery();
        }
    }

    @Override
    public void tick() {
        super.tick();
        pollLanServers();
        this.pinger.tick();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateFrameTime();
        updateTransition();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.switchingBack) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    @Override
    protected void appendLuaState(Map<String, Object> state) {
        List<Map<String, Object>> servers = new ArrayList<>();
        for (int index = 0; index < this.entries.size(); index++) {
            ServerEntry entry = this.entries.get(index);
            entry.requestPing();
            entry.uploadIconIfChanged();

            Map<String, Object> server = new LinkedHashMap<>();
            server.put("index", index + 1);
            server.put("kind", entry.kind.name().toLowerCase(java.util.Locale.ROOT));
            server.put("title", entry.title());
            server.put("detail", entry.detailLine());
            server.put("status", entry.statusLine());
            server.put("selectable", entry.selectable());
            server.put("selected", index == this.selectedIndex && entry.selectable());
            servers.add(server);
        }

        state.put("servers", servers);
        state.put("loaded", this.serversLoaded);
        state.put("interactive", isInteractive());
        state.put("transition_progress", this.transitionProgress);
        state.put("exiting", this.exiting);
        state.put("can_join", selectedEntry() != null);
        state.put("can_edit", selectedOnlineServer() != null);
        state.put("can_delete", selectedOnlineServer() != null);
    }

    @Override
    protected boolean onLuaAction(String action, LuaValue payload) {
        if (!isInteractive() && !action.equals("back")) {
            return true;
        }

        return switch (action) {
            case "select" -> {
                selectEntry(payload.checkint() - 1);
                yield true;
            }
            case "join" -> {
                joinSelectedServer();
                yield true;
            }
            case "join_index" -> {
                selectEntry(payload.checkint() - 1);
                joinSelectedServer();
                yield true;
            }
            case "direct" -> {
                openDirectJoin();
                yield true;
            }
            case "add" -> {
                openAddServer();
                yield true;
            }
            case "edit" -> {
                openEditServer();
                yield true;
            }
            case "delete" -> {
                openDeleteServer();
                yield true;
            }
            case "refresh" -> {
                refreshServers();
                yield true;
            }
            case "back" -> {
                beginBackTransition();
                yield true;
            }
            default -> false;
        };
    }

    @Override
    protected void renderLuaCustom(String name, LuaValue[] args) {
        if (!name.equals("server_icon") || args.length < 5) {
            return;
        }
        int index = args[0].checkint() - 1;
        if (index < 0 || index >= this.entries.size()) {
            return;
        }
        renderServerIcon(
            this.entries.get(index),
            (float)args[1].checkdouble(),
            (float)args[2].checkdouble(),
            (float)args[3].checkdouble(),
            (float)args[4].checkdouble()
        );
    }

    @Override
    public void onClose() {
        beginBackTransition();
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
    public void removed() {
        stopLanDiscovery();
        this.pinger.removeAll();
        closeOnlineEntries();
        this.entries.clear();
        this.lanServers.clear();
        this.serversLoaded = false;
        MainUI.pauseSharedBackgroundPlayback();
        super.removed();
    }

    private void initActionButtons() {
        this.actionButtons.clear();
        this.actionButtons.add(new ActionButton(
            () -> "Join",
            () -> selectedEntry() != null,
            this::joinSelectedServer
        ));
        this.actionButtons.add(new ActionButton("Direct", () -> true, this::openDirectJoin));
        this.actionButtons.add(new ActionButton("Add", () -> true, this::openAddServer));
        this.actionButtons.add(new ActionButton("Edit", () -> selectedOnlineServer() != null, this::openEditServer));
        this.actionButtons.add(new ActionButton("Delete", () -> selectedOnlineServer() != null, this::openDeleteServer));
        this.actionButtons.add(new ActionButton("Refresh", () -> true, this::refreshServers));
        this.actionButtons.add(new ActionButton("Back", () -> true, this::beginBackTransition));
    }

    private void loadServers() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        closeOnlineEntries();
        this.onlineEntries.clear();
        this.servers = new ServerList(minecraft);
        this.servers.load();
        for (int i = 0; i < this.servers.size(); i++) {
            this.onlineEntries.add(new ServerEntry(EntryKind.ONLINE, this.servers.get(i), null));
        }

        this.serversLoaded = true;
        rebuildEntries(true);
    }

    private void refreshServers() {
        this.pinger.removeAll();
        stopLanDiscovery();
        this.lanServers.clear();
        loadServers();
        startLanDiscovery();
    }

    private void startLanDiscovery() {
        if (this.lanServerDetector != null) {
            return;
        }

        this.lanServerList = new LanServerDetection.LanServerList();
        try {
            this.lanServerDetector = new LanServerDetection.LanServerDetector(this.lanServerList);
            this.lanServerDetector.start();
        } catch (Exception exception) {
            LOGGER.warn("Unable to start LAN server detection: {}", exception.getMessage());
            this.lanServerDetector = null;
        }

        rebuildEntries(true);
    }

    private void stopLanDiscovery() {
        if (this.lanServerDetector != null) {
            this.lanServerDetector.interrupt();
            this.lanServerDetector = null;
        }
        this.lanServerList = null;
    }

    private void pollLanServers() {
        if (this.lanServerList == null) {
            return;
        }

        List<LanServer> dirtyServers = this.lanServerList.takeDirtyServers();
        if (dirtyServers == null) {
            return;
        }

        this.lanServers.clear();
        this.lanServers.addAll(dirtyServers);
        rebuildEntries(true);
    }

    private void rebuildEntries(boolean preserveSelection) {
        String previousKey = preserveSelection ? currentSelectionKey() : null;
        this.entries.clear();
        this.entries.addAll(this.onlineEntries);
        this.entries.add(new ServerEntry(EntryKind.LAN_HEADER, null, null));
        for (LanServer lanServer : this.lanServers) {
            this.entries.add(new ServerEntry(EntryKind.LAN, null, lanServer));
        }

        this.selectedIndex = -1;
        if (previousKey != null) {
            for (int i = 0; i < this.entries.size(); i++) {
                if (this.entries.get(i).selectable() && this.entries.get(i).key().equals(previousKey)) {
                    selectEntry(i);
                    return;
                }
            }
        }

        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).selectable()) {
                selectEntry(i);
                return;
            }
        }

        this.selectedKey = null;
    }

    private @Nullable String currentSelectionKey() {
        ServerEntry entry = selectedEntry();
        if (entry != null) {
            return entry.key();
        }
        return this.selectedKey;
    }

    private void closeOnlineEntries() {
        for (ServerEntry entry : this.onlineEntries) {
            entry.close();
        }
    }

    private void selectEntry(int index) {
        if (index < 0 || index >= this.entries.size() || !this.entries.get(index).selectable()) {
            return;
        }

        this.selectedIndex = index;
        this.selectedKey = this.entries.get(index).key();
    }

    private @Nullable ServerEntry selectedEntry() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.entries.size()) {
            return null;
        }

        ServerEntry entry = this.entries.get(this.selectedIndex);
        return entry.selectable() ? entry : null;
    }

    private @Nullable ServerData selectedOnlineServer() {
        ServerEntry entry = selectedEntry();
        return entry != null && entry.kind == EntryKind.ONLINE ? entry.serverData : null;
    }

    private void joinSelectedServer() {
        ServerEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }

        ServerData serverData = entry.joinData();
        if (serverData != null) {
            join(serverData);
        }
    }

    private void join(ServerData serverData) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(serverData.ip), serverData, false, null);
        }
    }

    private void openDirectJoin() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        this.editingServer = new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
        minecraft.setScreen(new DirectJoinServerScreen(this, this::directJoinCallback, this.editingServer));
    }

    private void directJoinCallback(boolean confirmed) {
        if (!confirmed || this.editingServer == null) {
            this.editingServer = null;
            returnToThisScreen();
            return;
        }

        ServerList serverList = ensureServerList();
        if (serverList == null) {
            this.editingServer = null;
            returnToThisScreen();
            return;
        }

        ServerData serverData = serverList.get(this.editingServer.ip);
        if (serverData == null) {
            serverList.add(this.editingServer, true);
            serverList.save();
            serverData = this.editingServer;
        }

        this.editingServer = null;
        join(serverData);
    }

    private void openAddServer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        this.editingServer = new ServerData("", "", ServerData.Type.OTHER);
        minecraft.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.add.title"), this::addServerCallback, this.editingServer));
    }

    private void addServerCallback(boolean confirmed) {
        if (confirmed && this.editingServer != null) {
            ServerList serverList = ensureServerList();
            if (serverList != null) {
                ServerData serverData = serverList.unhide(this.editingServer.ip);
                if (serverData != null) {
                    serverData.copyNameIconFrom(this.editingServer);
                    this.selectedKey = keyFor(serverData);
                } else {
                    serverList.add(this.editingServer, false);
                    this.selectedKey = keyFor(this.editingServer);
                }
                serverList.save();
                loadServers();
            }
        }

        this.editingServer = null;
        returnToThisScreen();
    }

    private void openEditServer() {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData selected = selectedOnlineServer();
        if (minecraft == null || selected == null) {
            return;
        }

        this.editingOriginalServer = selected;
        this.editingServer = new ServerData(selected.name, selected.ip, ServerData.Type.OTHER);
        this.editingServer.copyFrom(selected);
        minecraft.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.edit.title"), this::editServerCallback, this.editingServer));
    }

    private void editServerCallback(boolean confirmed) {
        if (confirmed && this.editingOriginalServer != null && this.editingServer != null) {
            ServerList serverList = ensureServerList();
            if (serverList != null) {
                this.editingOriginalServer.name = this.editingServer.name;
                this.editingOriginalServer.ip = this.editingServer.ip;
                this.editingOriginalServer.copyFrom(this.editingServer);
                this.selectedKey = keyFor(this.editingOriginalServer);
                serverList.save();
                loadServers();
            }
        }

        this.editingOriginalServer = null;
        this.editingServer = null;
        returnToThisScreen();
    }

    private void openDeleteServer() {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData selected = selectedOnlineServer();
        if (minecraft == null || selected == null) {
            return;
        }

        this.deletingServer = selected;
        minecraft.setScreen(new ConfirmScreen(
            this::deleteServerCallback,
            Component.translatable("selectServer.deleteQuestion"),
            Component.translatable("selectServer.deleteWarning", selected.name),
            Component.translatable("selectServer.deleteButton"),
            CommonComponents.GUI_CANCEL
        ));
    }

    private void deleteServerCallback(boolean confirmed) {
        if (confirmed && this.deletingServer != null) {
            ServerList serverList = ensureServerList();
            if (serverList != null) {
                serverList.remove(this.deletingServer);
                serverList.save();
                this.selectedKey = null;
                loadServers();
            }
        }

        this.deletingServer = null;
        returnToThisScreen();
    }

    private @Nullable ServerList ensureServerList() {
        if (this.servers != null) {
            return this.servers;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }

        this.servers = new ServerList(minecraft);
        this.servers.load();
        return this.servers;
    }

    private void saveServers() {
        if (this.servers != null) {
            this.servers.save();
        }
    }

    private void returnToThisScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(this);
        }
    }

    private void beginBackTransition() {
        if (this.exiting || this.switchingBack) {
            return;
        }
        this.exiting = true;
    }

    private void updateFrameTime() {
        long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) {
            this.frameSeconds = DEFAULT_FRAME_SECONDS;
        } else {
            this.frameSeconds = clamp((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.0F, MAX_FRAME_SECONDS);
        }
        this.lastFrameNanos = now;
    }

    private void updateTransition() {
        float delta = this.frameSeconds / TRANSITION_SECONDS;
        this.transitionProgress = clamp(this.transitionProgress + (this.exiting ? -delta : delta), 0.0F, 1.0F);

        if (this.exiting && this.transitionProgress <= 0.0F && !this.switchingBack) {
            this.switchingBack = true;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.setScreen(this.lastScreen);
            }
        }
    }

    private boolean isInteractive() {
        return !this.exiting && this.transitionProgress >= 0.985F;
    }

    private void renderFlippingPanel(PanelBounds panel, int mouseX, int mouseY) {
        float flipProgress = easeInOutCubic(panelProgress());
        if (flipProgress <= 0.001F) {
            renderPanelSurface(panel, true);
            renderMainButtonGhosts(panel, 1.0F);
            return;
        }
        if (flipProgress >= 0.999F) {
            renderPanelSurface(panel, true);
            renderServerArea(panel, mouseX, mouseY, 1.0F);
            return;
        }

        float degrees = flipProgress * FLIP_DEGREES;
        float projectionScale = Render2DUtility.verticalFlipScale(degrees);
        if (projectionScale <= FLIP_EDGE_MIN_SCALE) {
            renderFlipEdge(panel);
            return;
        }

        boolean backFace = Render2DUtility.isVerticalFlipBackFace(degrees);
        float faceAlpha = easeOutCubic(clamp((projectionScale - FLIP_EDGE_MIN_SCALE) / (1.0F - FLIP_EDGE_MIN_SCALE), 0.0F, 1.0F));
        Render2DUtility.withVerticalPerspectiveFlip(degrees, panel.centerX(), panel.centerY(), panel.width, FLIP_EDGE_MIN_SCALE, () -> {
            renderPanelSurface(panel, false);
            renderFlipShade(panel, degrees);
            if (backFace) {
                Render2DUtility.withHorizontalVertexReflection(panel.centerX(), () ->
                    renderServerArea(panel, mouseX, mouseY, faceAlpha)
                );
            } else {
                renderMainButtonGhosts(panel, faceAlpha);
            }
        });
    }

    private void renderPanelSurface(PanelBounds panel, boolean blurSurface) {
        if (blurSurface) {
            Render2DUtility.drawGaussianBlurredPanel(
                panel.x,
                panel.y,
                panel.width,
                panel.height,
                PANEL_RADIUS,
                PANEL_BLUR_RADIUS,
                PANEL_BLUR,
                PANEL_BACKGROUND,
                PANEL_BORDER_WIDTH,
                PANEL_BORDER
            );
            return;
        }

        Render2DUtility.drawRoundedRect(panel.x, panel.y, panel.width, panel.height, PANEL_RADIUS, PANEL_BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(panel.x, panel.y, panel.width, panel.height, PANEL_RADIUS, PANEL_BORDER_WIDTH, PANEL_BORDER);
    }

    private void renderFlipShade(PanelBounds panel, float degrees) {
        float shadeAlpha = (float)Math.sin(Math.toRadians(degrees)) * 0.34F;
        if (shadeAlpha <= 0.001F) {
            return;
        }

        Render2DUtility.drawRoundedHorizontalGradientRect(
            panel.x,
            panel.y,
            panel.width,
            panel.height,
            PANEL_RADIUS,
            Render2DUtility.applyOpacity(0x99000000, shadeAlpha),
            Render2DUtility.applyOpacity(0x22000000, shadeAlpha)
        );
    }

    private void renderFlipEdge(PanelBounds panel) {
        float edgeWidth = Math.max(2.0F, PANEL_BORDER_WIDTH);
        float x = panel.centerX() - edgeWidth * 0.5F;
        Render2DUtility.drawDropShadow(x, panel.y, edgeWidth, panel.height, edgeWidth * 0.5F, 0.0F, 5.0F, 16.0F, 0x77000000);
        Render2DUtility.drawRoundedRect(x, panel.y, edgeWidth, panel.height, edgeWidth * 0.5F, PANEL_BACKGROUND);
        Render2DUtility.drawRoundedRect(x + edgeWidth * 0.5F - 0.5F, panel.y + PANEL_RADIUS, 1.0F,
            Math.max(0.0F, panel.height - PANEL_RADIUS * 2.0F), 0.5F, PANEL_BORDER);
    }

    private void renderTitles(PanelBounds mainPanel, PanelBounds currentPanel) {
        FontRenderer mainTitleFont = displayFont(26.0F);
        float mainTitleAlpha = 1.0F - easeOutCubic(phase(0.12F, 0.42F, this.transitionProgress));
        drawCenteredTitle(mainTitleFont, MAIN_TITLE, mainPanel.centerX(), Math.max(8.0F, mainPanel.y - 44.0F), mainTitleAlpha);

        FontRenderer multiTitleFont = displayFont(24.0F);
        float multiTitleAlpha = easeOutCubic(phase(0.54F, 0.88F, this.transitionProgress));
        drawCenteredTitle(multiTitleFont, MULTIPLAYER_TITLE, currentPanel.centerX(), Math.max(8.0F, currentPanel.y - 38.0F), multiTitleAlpha);
    }

    private void drawCenteredTitle(FontRenderer font, String text, float centerX, float y, float alpha) {
        if (alpha <= 0.001F) {
            return;
        }

        font.drawCenteredString(text, centerX + 1.0F, y + 1.0F, Render2DUtility.applyOpacity(TITLE_SHADOW, alpha));
        font.drawCenteredString(text, centerX, y, Render2DUtility.applyOpacity(TEXT, alpha));
    }

    private void renderMainButtonGhosts(PanelBounds panel, float alpha) {
        if (alpha <= 0.001F) {
            return;
        }

        float inset = clamp(panel.width * 0.13F, MAIN_BUTTON_MIN_INSET, MAIN_BUTTON_MAX_INSET);
        float buttonWidth = Math.max(1.0F, panel.width - inset * 2.0F);
        float buttonHeight = Math.max(1.0F, clamp(panel.height * 0.11F, MAIN_BUTTON_MIN_HEIGHT, MAIN_BUTTON_HEIGHT));
        float buttonGap = clamp(panel.height * 0.032F, MAIN_BUTTON_MIN_GAP, MAIN_BUTTON_GAP);
        float totalHeight = MAIN_BUTTON_LABELS.length * buttonHeight + (MAIN_BUTTON_LABELS.length - 1) * buttonGap;
        float buttonY = panel.y + (panel.height - totalHeight) * 0.5F;

        for (String label : MAIN_BUTTON_LABELS) {
            float x = panel.centerX() - buttonWidth * 0.5F;
            renderGhostButton(label, x, buttonY, buttonWidth, buttonHeight, alpha);
            buttonY += buttonHeight + buttonGap;
        }
    }

    private void renderGhostButton(String label, float x, float y, float width, float height, float alpha) {
        if (width <= 1.0F || height <= 1.0F || alpha <= 0.001F) {
            return;
        }

        Render2DUtility.drawDropShadow(x, y, width, height, 8.0F, 0.0F, 5.0F, 12.0F, Render2DUtility.applyOpacity(0x55000000, alpha * 0.55F));
        Render2DUtility.drawRoundedRect(x, y, width, height, 8.0F, Render2DUtility.applyOpacity(CONTROL_BACKGROUND, alpha));
        Render2DUtility.drawOutlineRoundedRect(x, y, width, height, 8.0F, 1.0F, Render2DUtility.applyOpacity(CONTROL_BORDER, alpha));

        FontRenderer font = textFont(12.0F);
        font.drawCenteredString(
            trimToWidth(font, label, width - 18.0F),
            x + width * 0.5F,
            y + (height - font.getLineHeight()) * 0.5F,
            Render2DUtility.applyOpacity(TEXT_MUTED, alpha)
        );
    }

    private void renderServerArea(PanelBounds panel, int mouseX, int mouseY, float faceAlpha) {
        if (faceAlpha <= 0.001F) {
            return;
        }

        float listProgress = listProgress();
        if (listProgress <= 0.001F) {
            return;
        }

        float alpha = easeOutCubic(listProgress) * faceAlpha;
        renderPanelHeader(panel, alpha);

        float listX = panel.x + PANEL_PADDING;
        float listY = panel.y + HEADER_HEIGHT;
        float listWidth = panel.width - PANEL_PADDING * 2.0F;
        float listHeight = Math.max(0.0F, panel.height - HEADER_HEIGHT - PANEL_PADDING);
        updateScrollLimit(listHeight);

        Render2DUtility.withClip(listX, listY, listWidth, listHeight, () -> {
            if (!this.serversLoaded) {
                renderCenteredStatus("Loading servers...", listX, listY, listWidth, listHeight, alpha);
            } else if (this.entries.isEmpty()) {
                renderCenteredStatus("No servers found", listX, listY, listWidth, listHeight, alpha);
            } else {
                renderServerRows(listX, listY, listWidth, listHeight, mouseX, mouseY, alpha, listProgress);
            }
        });

        renderScrollBar(listX, listY, listWidth, listHeight, alpha);
    }

    private void renderPanelHeader(PanelBounds panel, float alpha) {
        FontRenderer heading = displayFont(17.0F);
        FontRenderer meta = textFont(10.0F);
        int savedCount = this.onlineEntries.size();
        int lanCount = this.lanServers.size();
        String count = savedCount + (savedCount == 1 ? " saved server" : " saved servers") + " - " + lanCount + " LAN";
        float x = panel.x + PANEL_PADDING;
        heading.drawString("Servers", x, panel.y + 18.0F, Render2DUtility.applyOpacity(TEXT, alpha));
        meta.drawString(trimToWidth(meta, count, panel.width - PANEL_PADDING * 2.0F), x, panel.y + 39.0F, Render2DUtility.applyOpacity(TEXT_DIM, alpha));
    }

    private void renderServerRows(float listX, float listY, float listWidth, float listHeight, int mouseX, int mouseY, float alpha, float listProgress) {
        FontRenderer nameFont = textFont(12.0F);
        FontRenderer metaFont = textFont(9.0F);
        FontRenderer infoFont = textFont(9.0F);
        for (int i = 0; i < this.entries.size(); i++) {
            float rowY = stackedItemY(listY, i, ROW_HEIGHT, ROW_GAP, this.scroll);
            if (rowY > listY + listHeight || rowY + ROW_HEIGHT < listY) {
                continue;
            }

            float rowProgress = clamp(listProgress * 1.18F - i * 0.045F, 0.0F, 1.0F);
            rowProgress = easeOutCubic(rowProgress);
            if (rowProgress <= 0.001F) {
                continue;
            }

            ServerEntry entry = this.entries.get(i);
            entry.requestPing();
            entry.uploadIconIfChanged();
            boolean selected = i == this.selectedIndex && entry.selectable();
            boolean selectable = entry.selectable();
            boolean hovered = isInteractive() && selectable && isInside(mouseX, mouseY, listX, rowY, listWidth, ROW_HEIGHT);
            float visibleWidth = listWidth * rowProgress;
            float rowX = listX + (listWidth - visibleWidth) * 0.5F;
            float rowAlpha = alpha * rowProgress;
            int baseFill = selectable ? ROW_BACKGROUND : ROW_DISABLED;
            int fill = selected ? ROW_SELECTED : Render2DUtility.mix(baseFill, ROW_HOVER, hovered ? 1.0F : 0.0F);
            int border = selected ? ACCENT : CONTROL_BORDER;

            Render2DUtility.drawRoundedRect(rowX, rowY, visibleWidth, ROW_HEIGHT, 8.0F, Render2DUtility.applyOpacity(fill, rowAlpha));
            Render2DUtility.drawOutlineRoundedRect(rowX, rowY, visibleWidth, ROW_HEIGHT, 8.0F, 1.0F, Render2DUtility.applyOpacity(border, rowAlpha));

            if (visibleWidth < 78.0F) {
                continue;
            }

            float iconX = rowX + 9.0F;
            float iconY = rowY + (ROW_HEIGHT - ICON_SIZE) * 0.5F;
            renderServerIcon(entry, iconX, iconY, ICON_SIZE, rowAlpha);

            float textX = iconX + ICON_SIZE + 12.0F;
            float textRight = rowX + visibleWidth - 12.0F;
            float textWidth = Math.max(1.0F, textRight - textX);
            int nameColor = selectable ? TEXT : TEXT_DISABLED;
            nameFont.drawString(trimToWidth(nameFont, entry.title(), textWidth), textX, rowY + 10.0F,
                Render2DUtility.applyOpacity(nameColor, rowAlpha));
            metaFont.drawString(trimToWidth(metaFont, entry.detailLine(), textWidth), textX, rowY + 29.0F,
                Render2DUtility.applyOpacity(TEXT_DIM, rowAlpha));
            infoFont.drawString(trimToWidth(infoFont, entry.statusLine(), textWidth), textX, rowY + 43.0F,
                Render2DUtility.applyOpacity(selected ? 0xFFD7E6FF : TEXT_SUBTLE, rowAlpha));
        }
    }

    private void renderServerIcon(ServerEntry entry, float x, float y, float size, float alpha) {
        Render2DUtility.drawRoundedRect(x, y, size, size, 6.0F, Render2DUtility.applyOpacity(0xFF252A35, alpha));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && entry.icon != null) {
            Render2DUtility.drawRoundedTexture(
                minecraft.getTextureManager().getTexture(entry.icon.textureLocation()).getTextureView(),
                x,
                y,
                size,
                size,
                6.0F,
                Render2DUtility.applyOpacity(0xFFFFFFFF, alpha)
            );
        } else {
            FontRenderer font = textFont(entry.kind == EntryKind.LAN_HEADER ? 10.0F : 11.0F);
            font.drawCenteredString(entry.iconLabel(), x + size * 0.5F, y + (size - font.getLineHeight()) * 0.5F,
                Render2DUtility.applyOpacity(entry.selectable() ? TEXT_MUTED : TEXT_DIM, alpha));
        }
        Render2DUtility.drawOutlineRoundedRect(x, y, size, size, 6.0F, 1.0F, Render2DUtility.applyOpacity(0x44FFFFFF, alpha));
    }

    private void renderCenteredStatus(String message, float x, float y, float width, float height, float alpha) {
        FontRenderer font = textFont(12.0F);
        font.drawCenteredString(
            trimToWidth(font, message, width - 24.0F),
            x + width * 0.5F,
            y + (height - font.getLineHeight()) * 0.5F,
            Render2DUtility.applyOpacity(TEXT_DIM, alpha)
        );
    }

    private void renderScrollBar(float listX, float listY, float listWidth, float listHeight, float alpha) {
        if (this.maxScroll <= 0.001F || this.entries.isEmpty()) {
            return;
        }

        float contentHeight = stackedContentHeight(this.entries.size(), ROW_HEIGHT, ROW_GAP);
        float barHeight = clamp(listHeight * (listHeight / Math.max(listHeight, contentHeight)), 24.0F, listHeight);
        float barY = listY + (listHeight - barHeight) * (this.scroll / this.maxScroll);
        Render2DUtility.drawRoundedRect(
            listX + listWidth - 4.0F,
            barY,
            3.0F,
            barHeight,
            1.5F,
            Render2DUtility.applyOpacity(0x66FFFFFF, alpha)
        );
    }

    private void renderUserCardTransition() {
        float cardWidth = MainUI.sharedUserCardWidth(this.width);
        float hiddenOffset = -cardWidth - 28.0F;
        float offsetX;
        float alpha;

        if (this.exiting) {
            float progress = phase(0.0F, 0.42F, 1.0F - this.transitionProgress);
            float eased = easeOutBack(progress);
            offsetX = lerp(hiddenOffset, 0.0F, eased);
            alpha = clamp(progress * 1.4F, 0.0F, 1.0F);
        } else {
            float progress = phase(0.0F, 0.42F, this.transitionProgress);
            if (progress <= 0.22F) {
                offsetX = lerp(0.0F, 12.0F, easeOutCubic(progress / 0.22F));
            } else {
                offsetX = lerp(12.0F, hiddenOffset, easeInCubic((progress - 0.22F) / 0.78F));
            }
            alpha = 1.0F - easeOutCubic(phase(0.52F, 1.0F, progress));
        }

        if (alpha > 0.001F) {
            MainUI.renderSharedUserCard(this.width, this.height, offsetX, alpha);
        }
    }

    private void layoutActionButtons(PanelBounds panel) {
        if (this.actionButtons.size() < 7) {
            return;
        }

        float y = panel.y + panel.height + ACTION_BUTTON_TOP_GAP;
        float rowOneWidth = (panel.width - ACTION_BUTTON_GAP * 3.0F) / 4.0F;
        float rowTwoWidth = (panel.width - ACTION_BUTTON_GAP * 2.0F) / 3.0F;

        for (int i = 0; i < 4; i++) {
            ActionButton button = this.actionButtons.get(i);
            button.setBounds(panel.x + i * (rowOneWidth + ACTION_BUTTON_GAP), y, rowOneWidth, ACTION_BUTTON_HEIGHT);
        }

        float rowTwoY = y + ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP;
        for (int i = 4; i < this.actionButtons.size(); i++) {
            ActionButton button = this.actionButtons.get(i);
            int column = i - 4;
            button.setBounds(panel.x + column * (rowTwoWidth + ACTION_BUTTON_GAP), rowTwoY, rowTwoWidth, ACTION_BUTTON_HEIGHT);
        }
    }

    private void renderActionButtons(int mouseX, int mouseY) {
        float alpha;
        float scale;
        if (this.exiting) {
            float exit = 1.0F - this.transitionProgress;
            if (exit < 0.18F) {
                scale = 1.0F + 0.08F * easeOutCubic(exit / 0.18F);
                alpha = 1.0F;
            } else {
                float shrink = easeInCubic(phase(0.18F, 0.50F, exit));
                scale = 1.08F * (1.0F - shrink);
                alpha = 1.0F - shrink;
            }
        } else {
            float buttonProgress = easeOutBack(phase(0.72F, 1.0F, this.transitionProgress));
            scale = 0.86F + 0.14F * buttonProgress;
            alpha = buttonProgress;
        }

        if (alpha <= 0.001F || scale <= 0.001F) {
            return;
        }

        for (ActionButton button : this.actionButtons) {
            renderActionButton(button, mouseX, mouseY, alpha, scale);
        }
    }

    private void renderActionButton(ActionButton button, int mouseX, int mouseY, float alpha, float scale) {
        boolean active = button.active();
        boolean hovered = isInteractive() && active && button.contains(mouseX, mouseY);
        button.hoverProgress = animateExp(button.hoverProgress, hovered ? 1.0F : 0.0F, HOVER_SPEED, this.frameSeconds);

        float opacity = alpha * (active ? 1.0F : 0.48F);
        int fill = Render2DUtility.mix(CONTROL_BACKGROUND, CONTROL_HOVER, button.hoverProgress);
        int border = Render2DUtility.mix(CONTROL_BORDER, CONTROL_BORDER_HOVER, button.hoverProgress);
        int color = active ? Render2DUtility.mix(TEXT_MUTED, TEXT, button.hoverProgress) : TEXT_DISABLED;

        Render2DUtility.withScale(scale, scale, button.centerX(), button.centerY(), () -> {
            Render2DUtility.drawDropShadow(button.x, button.y, button.width, button.height, 8.0F, 0.0F, 5.0F, 12.0F,
                Render2DUtility.applyOpacity(0x55000000, opacity * (0.55F + button.hoverProgress * 0.45F)));
            Render2DUtility.drawRoundedRect(button.x, button.y, button.width, button.height, 8.0F, Render2DUtility.applyOpacity(fill, opacity));
            Render2DUtility.drawOutlineRoundedRect(button.x, button.y, button.width, button.height, 8.0F, 1.0F,
                Render2DUtility.applyOpacity(border, opacity));

            FontRenderer font = textFont(11.0F);
            font.drawCenteredString(
                trimToWidth(font, button.label(), button.width - 16.0F),
                button.centerX(),
                button.y + (button.height - font.getLineHeight()) * 0.5F,
                Render2DUtility.applyOpacity(color, opacity)
            );
        });
    }

    private int serverRowAt(double mouseX, double mouseY) {
        PanelBounds panel = currentPanelBounds(mainPanelBounds(), targetPanelBounds());
        float listX = panel.x + PANEL_PADDING;
        float listY = panel.y + HEADER_HEIGHT;
        float listWidth = panel.width - PANEL_PADDING * 2.0F;
        float listHeight = Math.max(0.0F, panel.height - HEADER_HEIGHT - PANEL_PADDING);
        if (!isInside(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
            return -1;
        }

        for (int i = 0; i < this.entries.size(); i++) {
            float rowY = stackedItemY(listY, i, ROW_HEIGHT, ROW_GAP, this.scroll);
            if (isInside(mouseX, mouseY, listX, rowY, listWidth, ROW_HEIGHT)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        PanelBounds panel = currentPanelBounds(mainPanelBounds(), targetPanelBounds());
        float listX = panel.x + PANEL_PADDING;
        float listY = panel.y + HEADER_HEIGHT;
        float listWidth = panel.width - PANEL_PADDING * 2.0F;
        float listHeight = Math.max(0.0F, panel.height - HEADER_HEIGHT - PANEL_PADDING);
        return isInside(mouseX, mouseY, listX, listY, listWidth, listHeight);
    }

    private void updateScrollLimit(float listHeight) {
        this.maxScroll = Math.max(0.0F, stackedContentHeight(this.entries.size(), ROW_HEIGHT, ROW_GAP) - listHeight);
        this.scroll = clamp(this.scroll, 0.0F, this.maxScroll);
    }

    private PanelBounds mainPanelBounds() {
        float maxWidth = Math.max(1.0F, Math.min(MAIN_PANEL_MAX_WIDTH, this.width - 32.0F));
        float minWidth = Math.min(MAIN_PANEL_MIN_WIDTH, maxWidth);
        float panelWidth = clamp(this.width * 0.336F, minWidth, maxWidth);

        float maxHeight = Math.max(1.0F, Math.min(MAIN_PANEL_MAX_HEIGHT, this.height - 32.0F));
        float minHeight = Math.min(MAIN_PANEL_MIN_HEIGHT, maxHeight);
        float panelHeight = clamp(this.height * 0.432F, minHeight, maxHeight);

        return new PanelBounds((this.width - panelWidth) * 0.5F, (this.height - panelHeight) * 0.5F, panelWidth, panelHeight);
    }

    private PanelBounds targetPanelBounds() {
        float maxWidth = Math.max(1.0F, Math.min(PANEL_MAX_WIDTH, this.width - 42.0F));
        float minWidth = Math.min(PANEL_MIN_WIDTH, maxWidth);
        float basePanelWidth = clamp(this.width * 0.74F, minWidth, maxWidth);
        float panelWidth = Math.max(1.0F, basePanelWidth * TARGET_PANEL_WIDTH_SCALE);

        float controlsHeight = ACTION_BUTTON_HEIGHT * 2.0F + ACTION_BUTTON_GAP;
        float availableHeight = Math.max(1.0F, this.height - controlsHeight - ACTION_BUTTON_TOP_GAP - 76.0F);
        float maxHeight = Math.max(1.0F, Math.min(PANEL_MAX_HEIGHT, availableHeight));
        float minHeight = Math.min(PANEL_MIN_HEIGHT, maxHeight);
        float basePanelHeight = clamp(this.height * 0.58F, minHeight, maxHeight);
        float panelHeight = Math.min(availableHeight, basePanelHeight * TARGET_PANEL_HEIGHT_SCALE);
        float y = Math.max(44.0F, (this.height - panelHeight - controlsHeight - ACTION_BUTTON_TOP_GAP - 18.0F) * 0.5F);

        return new PanelBounds((this.width - panelWidth) * 0.5F, y, panelWidth, panelHeight);
    }

    private PanelBounds currentPanelBounds(PanelBounds from, PanelBounds to) {
        float progress = easeInOutCubic(panelProgress());
        return new PanelBounds(
            lerp(from.x, to.x, progress),
            lerp(from.y, to.y, progress),
            lerp(from.width, to.width, progress),
            lerp(from.height, to.height, progress)
        );
    }

    private float panelProgress() {
        return phase(0.24F, 0.74F, this.transitionProgress);
    }

    private float listProgress() {
        return phase(0.56F, 0.96F, this.transitionProgress);
    }

    private void playClickSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private FontRenderer displayFont(float size) {
        return FontManager.getAppleDisplayRenderer(size);
    }

    private FontRenderer textFont(float size) {
        return FontManager.getAppleTextRenderer(size);
    }

    private String trimToWidth(FontRenderer renderer, String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) {
            return "";
        }
        if (renderer.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        float suffixWidth = renderer.getStringWidth(suffix);
        if (suffixWidth > maxWidth) {
            return "";
        }

        int end = text.length();
        while (end > 0 && renderer.getStringWidth(text.substring(0, end)) + suffixWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + suffix;
    }

    private static String keyFor(ServerData serverData) {
        return EntryKind.ONLINE.name() + ":" + serverData.ip + "\u0000" + serverData.name;
    }

    private static String componentString(@Nullable Component component) {
        return component == null ? "" : component.getString();
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    private final class ServerEntry implements AutoCloseable {
        private final EntryKind kind;
        private final @Nullable ServerData serverData;
        private final @Nullable LanServer lanServer;
        private final @Nullable FaviconTexture icon;
        private @Nullable byte[] lastIconBytes;

        private ServerEntry(EntryKind kind, @Nullable ServerData serverData, @Nullable LanServer lanServer) {
            this.kind = kind;
            this.serverData = serverData;
            this.lanServer = lanServer;
            Minecraft minecraft = Minecraft.getInstance();
            this.icon = kind == EntryKind.ONLINE && minecraft != null && serverData != null
                ? FaviconTexture.forServer(minecraft.getTextureManager(), serverData.ip)
                : null;
        }

        private boolean selectable() {
            return this.kind != EntryKind.LAN_HEADER;
        }

        private String key() {
            if (this.kind == EntryKind.ONLINE && this.serverData != null) {
                return keyFor(this.serverData);
            }
            if (this.kind == EntryKind.LAN && this.lanServer != null) {
                return EntryKind.LAN.name() + ":" + this.lanServer.getAddress();
            }
            return EntryKind.LAN_HEADER.name();
        }

        private @Nullable ServerData joinData() {
            if (this.kind == EntryKind.ONLINE) {
                return this.serverData;
            }
            if (this.kind == EntryKind.LAN && this.lanServer != null) {
                return new ServerData(this.lanServer.getMotd(), this.lanServer.getAddress(), ServerData.Type.LAN);
            }
            return null;
        }

        private String title() {
            if (this.kind == EntryKind.ONLINE && this.serverData != null) {
                return this.serverData.name == null || this.serverData.name.isBlank() ? I18n.get("selectServer.defaultName") : this.serverData.name;
            }
            if (this.kind == EntryKind.LAN && this.lanServer != null) {
                return this.lanServer.getMotd();
            }
            return "LAN Servers";
        }

        private String detailLine() {
            Minecraft minecraft = Minecraft.getInstance();
            boolean hideAddress = minecraft != null && minecraft.options.hideServerAddress;
            if (this.kind == EntryKind.ONLINE && this.serverData != null) {
                return hideAddress ? I18n.get("selectServer.hiddenAddress") : this.serverData.ip;
            }
            if (this.kind == EntryKind.LAN && this.lanServer != null) {
                return hideAddress ? I18n.get("selectServer.hiddenAddress") : this.lanServer.getAddress();
            }
            return "Scanning for local games";
        }

        private String statusLine() {
            if (this.kind == EntryKind.ONLINE && this.serverData != null) {
                return switch (this.serverData.state()) {
                    case INITIAL, PINGING -> "Pinging...";
                    case UNREACHABLE -> {
                        String message = componentString(this.serverData.motd);
                        yield message.isBlank() ? "No connection" : message;
                    }
                    case INCOMPATIBLE -> {
                        String version = componentString(this.serverData.version);
                        yield version.isBlank() ? "Incompatible" : "Incompatible - " + version;
                    }
                    case SUCCESSFUL -> {
                        String status = componentString(this.serverData.status);
                        String motd = componentString(this.serverData.motd);
                        yield status.isBlank() ? (motd.isBlank() ? "Online" : motd) : status;
                    }
                };
            }
            if (this.kind == EntryKind.LAN) {
                return "LAN Server";
            }
            return MutiPlayerUI.this.lanServers.isEmpty() ? "Searching..." : MutiPlayerUI.this.lanServers.size() + " found";
        }

        private String iconLabel() {
            return switch (this.kind) {
                case ONLINE -> "SRV";
                case LAN -> "LAN";
                case LAN_HEADER -> "...";
            };
        }

        private void requestPing() {
            if (this.kind != EntryKind.ONLINE || this.serverData == null || this.serverData.state() != ServerData.State.INITIAL) {
                return;
            }

            this.serverData.setState(ServerData.State.PINGING);
            this.serverData.motd = CommonComponents.EMPTY;
            this.serverData.status = CommonComponents.EMPTY;
            SERVER_PING_POOL.submit(() -> {
                try {
                    Minecraft minecraft = Minecraft.getInstance();
                    MutiPlayerUI.this.pinger.pingServer(
                        this.serverData,
                        () -> {
                            Minecraft client = Minecraft.getInstance();
                            if (client != null) {
                                client.execute(MutiPlayerUI.this::saveServers);
                            }
                        },
                        () -> this.serverData.setState(
                            this.serverData.protocol == SharedConstants.getCurrentVersion().protocolVersion()
                                ? ServerData.State.SUCCESSFUL
                                : ServerData.State.INCOMPATIBLE
                        ),
                        EventLoopGroupHolder.remote(minecraft != null && minecraft.options.useNativeTransport())
                    );
                } catch (UnknownHostException exception) {
                    this.serverData.setState(ServerData.State.UNREACHABLE);
                    this.serverData.motd = CANT_RESOLVE_TEXT;
                    this.serverData.status = CommonComponents.EMPTY;
                } catch (Exception exception) {
                    this.serverData.setState(ServerData.State.UNREACHABLE);
                    this.serverData.motd = CANT_CONNECT_TEXT;
                    this.serverData.status = CommonComponents.EMPTY;
                    LOGGER.debug("Failed to ping server {}", this.serverData.ip, exception);
                }
            });
        }

        private void uploadIconIfChanged() {
            if (this.icon == null || this.serverData == null) {
                return;
            }

            byte[] iconBytes = this.serverData.getIconBytes();
            if (Arrays.equals(iconBytes, this.lastIconBytes)) {
                return;
            }

            if (iconBytes == null) {
                this.icon.clear();
                this.lastIconBytes = null;
                return;
            }

            try {
                this.icon.upload(NativeImage.read(iconBytes));
                this.lastIconBytes = iconBytes;
            } catch (Throwable throwable) {
                LOGGER.error("Invalid icon for server {} ({})", this.serverData.name, this.serverData.ip, throwable);
                this.serverData.setIconBytes(null);
                this.icon.clear();
                this.lastIconBytes = null;
                saveServers();
            }
        }

        @Override
        public void close() {
            if (this.icon != null && !this.icon.isClosed()) {
                this.icon.close();
            }
        }
    }

    private enum EntryKind {
        ONLINE,
        LAN_HEADER,
        LAN
    }

    private final class ActionButton {
        private final java.util.function.Supplier<String> label;
        private final java.util.function.BooleanSupplier active;
        private final Runnable action;
        private float x;
        private float y;
        private float width;
        private float height;
        private float hoverProgress;

        private ActionButton(String label, java.util.function.BooleanSupplier active, Runnable action) {
            this(() -> label, active, action);
        }

        private ActionButton(java.util.function.Supplier<String> label, java.util.function.BooleanSupplier active, Runnable action) {
            this.label = label;
            this.active = active;
            this.action = action;
        }

        private void setBounds(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private String label() {
            return this.label.get();
        }

        private boolean active() {
            return this.active.getAsBoolean();
        }

        private boolean contains(double mouseX, double mouseY) {
            return isInside(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }

        private float centerX() {
            return this.x + this.width * 0.5F;
        }

        private float centerY() {
            return this.y + this.height * 0.5F;
        }
    }

    private record PanelBounds(float x, float y, float width, float height) {
        private float centerX() {
            return this.x + this.width * 0.5F;
        }

        private float centerY() {
            return this.y + this.height * 0.5F;
        }
    }

    private static final class MutiPlayerSafetyScreen extends WarningScreen {
        private static final Component TITLE = Component.translatable("multiplayerWarning.header").withStyle(ChatFormatting.BOLD);
        private static final Component CONTENT = Component.translatable("multiplayerWarning.message");
        private static final Component CHECK = Component.translatable("multiplayerWarning.check");
        private static final Component NARRATION = TITLE.copy().append("\n").append(CONTENT);

        private final Screen previous;

        private MutiPlayerSafetyScreen(Screen previous) {
            super(TITLE, CONTENT, CHECK, NARRATION);
            this.previous = previous;
        }

        @Override
        protected Layout addFooterButtons() {
            LinearLayout layout = LinearLayout.horizontal().spacing(8);
            layout.addChild(Button.builder(CommonComponents.GUI_PROCEED, button -> {
                if (this.stopShowing != null && this.stopShowing.selected()) {
                    this.minecraft.options.skipMultiplayerWarning = true;
                    this.minecraft.options.save();
                }

                this.minecraft.setScreen(new MutiPlayerUI(this.previous));
            }).build());
            layout.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).build());
            return layout;
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.previous);
        }
    }
}
