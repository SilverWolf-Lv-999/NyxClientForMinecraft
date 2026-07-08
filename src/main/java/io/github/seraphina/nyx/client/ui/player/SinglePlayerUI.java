package io.github.seraphina.nyx.client.ui.player;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.ui.mainui.MainUI;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public final class SinglePlayerUI extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);

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
    private static final String SINGLE_TITLE = "Single Player";
    private static final String[] MAIN_BUTTON_LABELS = {"Single Player", "Muti Player", "Alt Manager", "Option", "Exit"};

    private final Screen lastScreen;
    private final List<WorldEntry> entries = new ArrayList<>();
    private final List<ActionButton> actionButtons = new ArrayList<>();

    private @Nullable CompletableFuture<List<LevelSummary>> pendingLevels;
    private @Nullable Component loadError;
    private @Nullable String selectedLevelId;
    private int selectedIndex = -1;
    private float scroll;
    private float maxScroll;
    private long lastFrameNanos;
    private float frameSeconds = DEFAULT_FRAME_SECONDS;
    private float transitionProgress;
    private boolean exiting;
    private boolean switchingBack;
    private boolean levelsLoaded;

    public SinglePlayerUI(Screen lastScreen) {
        super(Component.translatable("selectWorld.title"));
        this.lastScreen = lastScreen;
        initActionButtons();
    }

    @Override
    protected void init() {
        super.init();
        this.lastFrameNanos = 0L;
        if (this.pendingLevels == null && !this.levelsLoaded) {
            reloadWorlds();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Render2DUtility.withGuiGraphics(guiGraphics, () -> {
            updateFrameTime();
            updateTransition();
            pollWorldLoad();

            MainUI.renderSharedBackground(this.width, this.height);
            Render2DUtility.drawRect(0.0F, 0.0F, this.width, this.height, Render2DUtility.applyOpacity(0x66000000, 0.32F * transitionProgress));
            renderUserCardTransition();

            PanelBounds mainPanel = mainPanelBounds();
            PanelBounds targetPanel = targetPanelBounds();
            PanelBounds currentPanel = currentPanelBounds(mainPanel, targetPanel);

            renderFlippingPanel(currentPanel, mouseX, mouseY);
            renderTitles(mainPanel, currentPanel);
            layoutActionButtons(targetPanel);
            renderActionButtons(mouseX, mouseY);
            MainUI.renderSharedBackgroundSelector(this.width, this.height, mouseX, mouseY, this.frameSeconds);
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW_MOUSE_BUTTON_LEFT || this.switchingBack) {
            return super.mouseClicked(event, doubleClick);
        }

        if (MainUI.mouseClickedSharedBackgroundSelector(event, this.width, this.height)) {
            return true;
        }

        if (isInteractive()) {
            for (ActionButton button : this.actionButtons) {
                if (button.active() && button.contains(event.x(), event.y())) {
                    playClickSound();
                    button.action.run();
                    return true;
                }
            }

            int row = worldRowAt(event.x(), event.y());
            if (row >= 0) {
                selectWorld(row);
                if (doubleClick) {
                    joinSelectedWorld();
                } else {
                    playClickSound();
                }
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (MainUI.mouseScrolledSharedBackgroundSelector(mouseX, mouseY, scrollY, this.width, this.height)) {
            return true;
        }

        if (isInteractive() && isInsideList(mouseX, mouseY)) {
            this.scroll = clamp(this.scroll - (float)scrollY * SCROLL_STEP, 0.0F, this.maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && MainUI.closeSharedBackgroundSelector()) {
            return true;
        }

        if (event.isEscape()) {
            beginBackTransition();
            return true;
        }

        if (event.isSelection() && isInteractive()) {
            joinSelectedWorld();
            return true;
        }

        return super.keyPressed(event);
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
        closeEntries();
        this.pendingLevels = null;
        this.levelsLoaded = false;
        MainUI.pauseSharedBackgroundPlayback();
        super.removed();
    }

    private void initActionButtons() {
        this.actionButtons.clear();
        this.actionButtons.add(new ActionButton(
            this::selectButtonLabel,
            () -> selectedSummary() != null && selectedSummary().primaryActionActive(),
            this::joinSelectedWorld
        ));
        this.actionButtons.add(new ActionButton("Create", () -> true, this::createWorld));
        this.actionButtons.add(new ActionButton("Edit", () -> selectedSummary() != null && selectedSummary().canEdit(), this::editSelectedWorld));
        this.actionButtons.add(new ActionButton("Delete", () -> selectedSummary() != null && selectedSummary().canDelete(), this::deleteSelectedWorld));
        this.actionButtons.add(new ActionButton("Backup", () -> selectedSummary() != null && selectedSummary().canDelete(), this::backupSelectedWorld));
        this.actionButtons.add(new ActionButton("Recreate", () -> selectedSummary() != null && selectedSummary().canRecreate(), this::recreateSelectedWorld));
        this.actionButtons.add(new ActionButton("Back", () -> true, this::beginBackTransition));
    }

    private String selectButtonLabel() {
        LevelSummary summary = selectedSummary();
        return summary == null ? "Select" : summary.primaryActionMessage().getString();
    }

    private void reloadWorlds() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        closeEntries();
        this.entries.clear();
        this.selectedIndex = -1;
        this.scroll = 0.0F;
        this.maxScroll = 0.0F;
        this.loadError = null;
        this.levelsLoaded = false;

        try {
            LevelStorageSource.LevelCandidates candidates = minecraft.getLevelSource().findLevelCandidates();
            this.pendingLevels = minecraft.getLevelSource().loadLevelSummaries(candidates);
        } catch (LevelStorageException exception) {
            LOGGER.error("Couldn't load level list", exception);
            this.pendingLevels = null;
            this.levelsLoaded = true;
            this.loadError = exception.getMessageComponent();
        }
    }

    private void pollWorldLoad() {
        if (this.pendingLevels == null || !this.pendingLevels.isDone()) {
            return;
        }

        CompletableFuture<List<LevelSummary>> completed = this.pendingLevels;
        this.pendingLevels = null;
        try {
            applyWorlds(completed.join());
        } catch (CancellationException | CompletionException exception) {
            LOGGER.error("Couldn't load level list", exception);
            this.levelsLoaded = true;
            this.loadError = Component.translatable("selectWorld.unable_to_load");
        }
    }

    private void applyWorlds(List<LevelSummary> summaries) {
        closeEntries();
        this.entries.clear();

        for (LevelSummary summary : summaries) {
            this.entries.add(new WorldEntry(summary));
        }

        this.levelsLoaded = true;
        if (this.entries.isEmpty()) {
            this.selectedIndex = -1;
            this.selectedLevelId = null;
        } else {
            this.selectedIndex = selectedIndexForRememberedWorld();
            if (this.selectedIndex < 0) {
                this.selectedIndex = 0;
            }
            this.selectedLevelId = this.entries.get(this.selectedIndex).summary.getLevelId();
        }
    }

    private int selectedIndexForRememberedWorld() {
        if (this.selectedLevelId == null) {
            return -1;
        }

        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).summary.getLevelId().equals(this.selectedLevelId)) {
                return i;
            }
        }
        return -1;
    }

    private void closeEntries() {
        for (WorldEntry entry : this.entries) {
            entry.close();
        }
    }

    private void selectWorld(int index) {
        if (index < 0 || index >= this.entries.size()) {
            return;
        }

        this.selectedIndex = index;
        this.selectedLevelId = this.entries.get(index).summary.getLevelId();
    }

    private @Nullable LevelSummary selectedSummary() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.entries.size()) {
            return null;
        }
        return this.entries.get(this.selectedIndex).summary;
    }

    private void joinSelectedWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        LevelSummary summary = selectedSummary();
        if (minecraft == null || summary == null || !summary.primaryActionActive()) {
            return;
        }

        if (summary instanceof LevelSummary.SymlinkLevelSummary) {
            minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> minecraft.setScreen(this)));
        } else {
            minecraft.createWorldOpenFlows().openWorld(summary.getLevelId(), this::returnToThisScreen);
        }
    }

    private void createWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            CreateWorldScreen.openFresh(minecraft, this::returnToThisScreen);
        }
    }

    private void editSelectedWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        LevelSummary summary = selectedSummary();
        if (minecraft == null || summary == null || !summary.canEdit()) {
            return;
        }

        queueLoadScreen();
        String levelId = summary.getLevelId();
        LevelStorageSource.LevelStorageAccess access;
        try {
            access = minecraft.getLevelSource().validateAndCreateAccess(levelId);
        } catch (IOException exception) {
            net.minecraft.client.gui.components.toasts.SystemToast.onWorldAccessFailure(minecraft, levelId);
            LOGGER.error("Failed to access level {}", levelId, exception);
            reloadWorlds();
            returnToThisScreen();
            return;
        } catch (ContentValidationException exception) {
            LOGGER.warn("{}", exception.getMessage());
            minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> minecraft.setScreen(this)));
            return;
        }

        EditWorldScreen editWorldScreen;
        try {
            editWorldScreen = EditWorldScreen.create(minecraft, access, changed -> {
                access.safeClose();
                reloadWorlds();
                returnToThisScreen();
            });
        } catch (NbtException | ReportedNbtException | IOException exception) {
            access.safeClose();
            net.minecraft.client.gui.components.toasts.SystemToast.onWorldAccessFailure(minecraft, levelId);
            LOGGER.error("Failed to load world data {}", levelId, exception);
            reloadWorlds();
            returnToThisScreen();
            return;
        }

        minecraft.setScreen(editWorldScreen);
    }

    private void deleteSelectedWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        LevelSummary summary = selectedSummary();
        if (minecraft == null || summary == null || !summary.canDelete()) {
            return;
        }

        minecraft.setScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    minecraft.setScreen(new ProgressScreen(true));
                    doDeleteWorld(summary);
                }
                reloadWorlds();
                returnToThisScreen();
            },
            Component.translatable("selectWorld.deleteQuestion"),
            Component.translatable("selectWorld.deleteWarning", summary.getLevelName()),
            Component.translatable("selectWorld.deleteButton"),
            CommonComponents.GUI_CANCEL
        ));
    }

    private void doDeleteWorld(LevelSummary summary) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        String levelId = summary.getLevelId();
        try (LevelStorageSource.LevelStorageAccess access = minecraft.getLevelSource().createAccess(levelId)) {
            access.deleteLevel();
        } catch (IOException exception) {
            net.minecraft.client.gui.components.toasts.SystemToast.onWorldDeleteFailure(minecraft, levelId);
            LOGGER.error("Failed to delete world {}", levelId, exception);
        }
    }

    private void backupSelectedWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        LevelSummary summary = selectedSummary();
        if (minecraft == null || summary == null) {
            return;
        }

        String levelId = summary.getLevelId();
        try (LevelStorageSource.LevelStorageAccess access = minecraft.getLevelSource().validateAndCreateAccess(levelId)) {
            EditWorldScreen.makeBackupAndShowToast(access);
        } catch (IOException exception) {
            net.minecraft.client.gui.components.toasts.SystemToast.onWorldAccessFailure(minecraft, levelId);
            LOGGER.error("Failed to backup world {}", levelId, exception);
        } catch (ContentValidationException exception) {
            LOGGER.warn("{}", exception.getMessage());
            minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> minecraft.setScreen(this)));
        }
    }

    private void recreateSelectedWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        LevelSummary summary = selectedSummary();
        if (minecraft == null || summary == null || !summary.canRecreate()) {
            return;
        }

        queueLoadScreen();
        try (LevelStorageSource.LevelStorageAccess access = minecraft.getLevelSource().validateAndCreateAccess(summary.getLevelId())) {
            Pair<LevelSettings, WorldCreationContext> pair = minecraft.createWorldOpenFlows().recreateWorldData(access);
            LevelSettings levelSettings = pair.getFirst();
            WorldCreationContext worldCreationContext = pair.getSecond();
            Path dataPackPath = CreateWorldScreen.createTempDataPackDirFromExistingWorld(access.getLevelPath(LevelResource.DATAPACK_DIR), minecraft);
            worldCreationContext.validate();
            if (worldCreationContext.options().isOldCustomizedWorld()) {
                minecraft.setScreen(new ConfirmScreen(
                    confirmed -> minecraft.setScreen(confirmed
                        ? CreateWorldScreen.createFromExisting(minecraft, this::returnToThisScreen, levelSettings, worldCreationContext, dataPackPath)
                        : this),
                    Component.translatable("selectWorld.recreate.customized.title"),
                    Component.translatable("selectWorld.recreate.customized.text"),
                    CommonComponents.GUI_PROCEED,
                    CommonComponents.GUI_CANCEL
                ));
            } else {
                minecraft.setScreen(CreateWorldScreen.createFromExisting(minecraft, this::returnToThisScreen, levelSettings, worldCreationContext, dataPackPath));
            }
        } catch (ContentValidationException exception) {
            LOGGER.warn("{}", exception.getMessage());
            minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> minecraft.setScreen(this)));
        } catch (Exception exception) {
            LOGGER.error("Unable to recreate world", exception);
            minecraft.setScreen(new AlertScreen(
                () -> minecraft.setScreen(this),
                Component.translatable("selectWorld.recreate.error.title"),
                Component.translatable("selectWorld.recreate.error.text")
            ));
        }
    }

    private void queueLoadScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreenAndShow(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
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
        float degrees = flipProgress * FLIP_DEGREES;
        float projectionScale = Render2DUtility.verticalFlipScale(degrees);
        if (projectionScale <= FLIP_EDGE_MIN_SCALE) {
            renderFlipEdge(panel);
            return;
        }

        boolean backFace = Render2DUtility.isVerticalFlipBackFace(degrees);
        boolean canBlurSurface = flipProgress <= 0.001F || flipProgress >= 0.999F;
        float faceAlpha = easeOutCubic(clamp((projectionScale - FLIP_EDGE_MIN_SCALE) / (1.0F - FLIP_EDGE_MIN_SCALE), 0.0F, 1.0F));
        Render2DUtility.withVerticalPerspectiveFlip(degrees, panel.centerX(), panel.centerY(), panel.width, FLIP_EDGE_MIN_SCALE, () -> {
            renderPanelSurface(panel, canBlurSurface);
            renderFlipShade(panel, degrees);
            if (backFace) {
                renderWorldArea(panel, mouseX, mouseY, faceAlpha);
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

        FontRenderer singleTitleFont = displayFont(24.0F);
        float singleTitleAlpha = easeOutCubic(phase(0.54F, 0.88F, this.transitionProgress));
        drawCenteredTitle(singleTitleFont, SINGLE_TITLE, currentPanel.centerX(), Math.max(8.0F, currentPanel.y - 38.0F), singleTitleAlpha);
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

    private void renderWorldArea(PanelBounds panel, int mouseX, int mouseY, float faceAlpha) {
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
            if (this.pendingLevels != null || (!this.levelsLoaded && this.loadError == null)) {
                renderCenteredStatus("Loading worlds...", listX, listY, listWidth, listHeight, alpha);
            } else if (this.loadError != null) {
                renderCenteredStatus(this.loadError.getString(), listX, listY, listWidth, listHeight, alpha);
            } else if (this.entries.isEmpty()) {
                renderCenteredStatus("No worlds found", listX, listY, listWidth, listHeight, alpha);
            } else {
                renderWorldRows(listX, listY, listWidth, listHeight, mouseX, mouseY, alpha, listProgress);
            }
        });

        renderScrollBar(listX, listY, listWidth, listHeight, alpha);
    }

    private void renderPanelHeader(PanelBounds panel, float alpha) {
        FontRenderer heading = displayFont(17.0F);
        FontRenderer meta = textFont(10.0F);
        String count = this.entries.size() == 1 ? "1 saved world" : this.entries.size() + " saved worlds";
        float x = panel.x + PANEL_PADDING;
        heading.drawString("Worlds", x, panel.y + 18.0F, Render2DUtility.applyOpacity(TEXT, alpha));
        meta.drawString(count, x, panel.y + 39.0F, Render2DUtility.applyOpacity(TEXT_DIM, alpha));
    }

    private void renderWorldRows(float listX, float listY, float listWidth, float listHeight, int mouseX, int mouseY, float alpha, float listProgress) {
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

            WorldEntry entry = this.entries.get(i);
            LevelSummary summary = entry.summary;
            boolean selected = i == this.selectedIndex;
            boolean disabled = summary.isDisabled();
            boolean hovered = isInteractive() && isInside(mouseX, mouseY, listX, rowY, listWidth, ROW_HEIGHT);
            float visibleWidth = listWidth * rowProgress;
            float rowX = listX + (listWidth - visibleWidth) * 0.5F;
            float rowAlpha = alpha * rowProgress;
            int fill = selected ? ROW_SELECTED : Render2DUtility.mix(disabled ? ROW_DISABLED : ROW_BACKGROUND, ROW_HOVER, hovered ? 1.0F : 0.0F);
            int border = selected ? ACCENT : CONTROL_BORDER;

            Render2DUtility.drawRoundedRect(rowX, rowY, visibleWidth, ROW_HEIGHT, 8.0F, Render2DUtility.applyOpacity(fill, rowAlpha));
            Render2DUtility.drawOutlineRoundedRect(rowX, rowY, visibleWidth, ROW_HEIGHT, 8.0F, 1.0F, Render2DUtility.applyOpacity(border, rowAlpha));

            if (visibleWidth < 78.0F) {
                continue;
            }

            float iconX = rowX + 9.0F;
            float iconY = rowY + (ROW_HEIGHT - ICON_SIZE) * 0.5F;
            renderWorldIcon(entry, iconX, iconY, ICON_SIZE, rowAlpha);

            float textX = iconX + ICON_SIZE + 12.0F;
            float textRight = rowX + visibleWidth - 12.0F;
            float textWidth = Math.max(1.0F, textRight - textX);
            int nameColor = disabled ? TEXT_DISABLED : TEXT;
            nameFont.drawString(trimToWidth(nameFont, summary.getLevelName(), textWidth), textX, rowY + 10.0F,
                Render2DUtility.applyOpacity(nameColor, rowAlpha));
            metaFont.drawString(trimToWidth(metaFont, detailLine(summary), textWidth), textX, rowY + 29.0F,
                Render2DUtility.applyOpacity(TEXT_DIM, rowAlpha));
            infoFont.drawString(trimToWidth(infoFont, summary.getInfo().getString(), textWidth), textX, rowY + 43.0F,
                Render2DUtility.applyOpacity(selected ? 0xFFD7E6FF : TEXT_SUBTLE, rowAlpha));
        }
    }

    private void renderWorldIcon(WorldEntry entry, float x, float y, float size, float alpha) {
        Render2DUtility.drawRoundedRect(x, y, size, size, 6.0F, Render2DUtility.applyOpacity(0xFF252A35, alpha));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            Render2DUtility.drawRoundedTexture(
                minecraft.getTextureManager().getTexture(entry.icon.textureLocation()).getTextureView(),
                x,
                y,
                size,
                size,
                6.0F,
                Render2DUtility.applyOpacity(0xFFFFFFFF, alpha)
            );
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

    private String detailLine(LevelSummary summary) {
        String date = "Never played";
        if (summary.getLastPlayed() != -1L) {
            ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(summary.getLastPlayed()), ZoneId.systemDefault());
            date = DATE_FORMAT.withLocale(Locale.getDefault()).format(time);
        }
        return summary.getLevelId() + " - " + date;
    }

    private int worldRowAt(double mouseX, double mouseY) {
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

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    private final class WorldEntry implements AutoCloseable {
        private final LevelSummary summary;
        private final FaviconTexture icon;
        private @Nullable Path iconFile;

        private WorldEntry(LevelSummary summary) {
            this.summary = summary;
            Minecraft minecraft = Minecraft.getInstance();
            this.icon = FaviconTexture.forWorld(minecraft.getTextureManager(), summary.getLevelId());
            this.iconFile = summary.getIcon();
            validateIconFile();
            loadIcon();
        }

        private void validateIconFile() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || this.iconFile == null) {
                return;
            }

            try {
                BasicFileAttributes attributes = Files.readAttributes(this.iconFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink()) {
                    List<ForbiddenSymlinkInfo> links = minecraft.directoryValidator().validateSymlink(this.iconFile);
                    if (!links.isEmpty()) {
                        this.iconFile = null;
                        return;
                    }
                    attributes = Files.readAttributes(this.iconFile, BasicFileAttributes.class);
                }

                if (!attributes.isRegularFile()) {
                    this.iconFile = null;
                }
            } catch (NoSuchFileException ignored) {
                this.iconFile = null;
            } catch (IOException exception) {
                LOGGER.error("Could not validate world icon {}", this.iconFile, exception);
                this.iconFile = null;
            }
        }

        private void loadIcon() {
            if (this.iconFile != null && Files.isRegularFile(this.iconFile)) {
                try (InputStream input = Files.newInputStream(this.iconFile)) {
                    this.icon.upload(NativeImage.read(input));
                    return;
                } catch (Throwable throwable) {
                    LOGGER.error("Invalid icon for world {}", this.summary.getLevelId(), throwable);
                    this.iconFile = null;
                }
            }

            this.icon.clear();
        }

        @Override
        public void close() {
            if (!this.icon.isClosed()) {
                this.icon.close();
            }
        }
    }

    private final class ActionButton {
        private final Supplier<String> label;
        private final BooleanSupplier active;
        private final Runnable action;
        private float x;
        private float y;
        private float width;
        private float height;
        private float hoverProgress;

        private ActionButton(String label, BooleanSupplier active, Runnable action) {
            this(() -> label, active, action);
        }

        private ActionButton(Supplier<String> label, BooleanSupplier active, Runnable action) {
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
}
