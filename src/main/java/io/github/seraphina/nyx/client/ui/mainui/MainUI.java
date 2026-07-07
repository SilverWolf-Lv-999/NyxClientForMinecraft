package io.github.seraphina.nyx.client.ui.mainui;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.manager.PathManager;
import io.github.seraphina.nyx.client.ui.mainui.background.BackgroundLibrary;
import io.github.seraphina.nyx.client.ui.mainui.background.BackgroundMedia;
import io.github.seraphina.nyx.client.ui.mainui.button.MainUIButton;
import io.github.seraphina.nyx.client.ui.player.MutiPlayerUI;
import io.github.seraphina.nyx.client.ui.player.SinglePlayerUI;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.utility.web.MicrosoftUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.seraphina.nyx.client.utility.MathUtility.animateLinear;
import static io.github.seraphina.nyx.client.utility.MathUtility.clamp;
import static io.github.seraphina.nyx.client.utility.MathUtility.easeOutCubic;
import static io.github.seraphina.nyx.client.utility.MathUtility.isInside;
import static io.github.seraphina.nyx.client.utility.MathUtility.stackedContentHeight;
import static io.github.seraphina.nyx.client.utility.MathUtility.stackedItemY;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public final class MainUI extends Screen {
    private static final Identifier SETTINGS_ICON = Identifier.fromNamespaceAndPath("nyxclient", "ui/icon/settings.png");
    private static final float SETTINGS_BUTTON_SIZE = 34.0F;
    private static final float SETTINGS_BUTTON_MARGIN = 14.0F;
    private static final float PANEL_MAX_WIDTH = 334.0F;
    private static final float PANEL_MIN_WIDTH = 236.0F;
    private static final float PANEL_HEADER_HEIGHT = 74.0F;
    private static final float PANEL_PADDING = 16.0F;
    private static final float ROW_HEIGHT = 72.0F;
    private static final float ROW_GAP = 10.0F;
    private static final float THUMBNAIL_WIDTH = 92.0F;
    private static final float THUMBNAIL_HEIGHT = 52.0F;
    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;
    private static final float PANEL_ANIMATION_SPEED = 14.0F;
    private static final float SCROLL_STEP = 34.0F;
    private static final float CENTER_PANEL_MAX_WIDTH = 301.0F;
    private static final float CENTER_PANEL_MIN_WIDTH = 175.0F;
    private static final float CENTER_PANEL_MAX_HEIGHT = 312.0F;
    private static final float CENTER_PANEL_MIN_HEIGHT = 180.0F;
    private static final float CENTER_PANEL_RADIUS = 18.0F;
    private static final float CENTER_PANEL_BLUR_RADIUS = 18.0F;
    private static final float CENTER_PANEL_BORDER_WIDTH = 3.0F;
    private static final float CENTER_PANEL_TITLE_SIZE = 26.0F;
    private static final float CENTER_PANEL_TITLE_GAP = 18.0F;
    private static final String CENTER_PANEL_TITLE = "Nyx Client";
    private static final float CENTER_BUTTON_HEIGHT = 34.0F;
    private static final float CENTER_BUTTON_MIN_HEIGHT = 28.0F;
    private static final float CENTER_BUTTON_GAP = 10.0F;
    private static final float CENTER_BUTTON_MIN_GAP = 7.0F;
    private static final float CENTER_BUTTON_MAX_INSET = 32.0F;
    private static final float CENTER_BUTTON_MIN_INSET = 18.0F;
    private static final float USER_CARD_SCALE = 0.7F;
    private static final float USER_CARD_MARGIN = 14.0F;
    private static final float USER_CARD_MAX_WIDTH = 236.0F * USER_CARD_SCALE;
    private static final float USER_CARD_MIN_WIDTH = 154.0F * USER_CARD_SCALE;
    private static final float USER_CARD_HEIGHT = 54.0F * USER_CARD_SCALE;
    private static final float USER_CARD_RADIUS = 12.0F * USER_CARD_SCALE;
    private static final float USER_CARD_BLUR_RADIUS = 12.0F * USER_CARD_SCALE;
    private static final float USER_CARD_PADDING = 10.0F * USER_CARD_SCALE;
    private static final float USER_AVATAR_SIZE = 34.0F * USER_CARD_SCALE;
    private static final float USER_NAME_GAP = 12.0F * USER_CARD_SCALE;
    private static final float USER_NAME_SIZE = 12.0F * USER_CARD_SCALE;
    private static final int AVATAR_TEXTURE_SIZE = 128;
    private static final int AVATAR_SUPERSAMPLE = 4;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFA8AFBE;
    private static final int TEXT_DIM = 0xFF687181;
    private static final int CENTER_PANEL_TITLE_SHADOW = 0xAA000000;
    private static final int CENTER_PANEL_BLUR = 0xE6FFFFFF;
    private static final int CENTER_PANEL_BACKGROUND = 0xB80A0C12;
    private static final int CENTER_PANEL_BORDER = 0x66FFFFFF;
    private static final int PANEL_BACKGROUND = 0xEE0B0D12;
    private static final int PANEL_BORDER = 0x22FFFFFF;
    private static final int ROW_BACKGROUND = 0x9913161E;
    private static final int ROW_HOVER = 0xBB1A1E29;
    private static final int ROW_SELECTED = 0x993D81F7;
    private static final int CONTROL_BACKGROUND = 0xAA0E1118;
    private static final int CONTROL_HOVER = 0xD7191D28;
    private static final int ACCENT = 0xFF3D81F7;
    private static final int USER_CARD_BLUR = 0xE6FFFFFF;
    private static final int USER_CARD_BACKGROUND = 0xB80A0C12;
    private static final int USER_CARD_BORDER = 0x2EFFFFFF;
    private static final int USER_CARD_SHADOW = 0x66000000;
    private static final int USER_NAME_SHADOW = 0xAA000000;

    private static final List<BackgroundMedia> BACKGROUND_CACHE = new ArrayList<>();
    private static final AtomicInteger AVATAR_TEXTURE_IDS = new AtomicInteger();

    private static String rememberedSelectedKey = BackgroundLibrary.DEFAULT_KEY;
    private static boolean backgroundCacheLoaded;
    private static boolean sharedSettingsOpen;
    private static float sharedSettingsPanelProgress;
    private static float sharedPanelScroll;
    private static float sharedMaxPanelScroll;

    private final List<MainUIButton> mainButtons = new ArrayList<>();
    private static boolean sharedWindowsUserNameLoaded;
    private static boolean sharedWindowsAvatarImageLoaded;
    private static String sharedWindowsUserName;
    private static BufferedImage sharedWindowsAvatarImage;
    private static DynamicTexture sharedWindowsAvatarTexture;

    private long lastFrameNanos;
    private float frameSeconds = DEFAULT_FRAME_SECONDS;
    private MainUIButton multiplayerButton;

    public MainUI() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();
        ensureBackgroundCacheLoaded();
        syncSharedSelectedBackground();
        initMainButtons();
        this.lastFrameNanos = 0L;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!isActiveScreen()) {
            pauseSharedBackgroundPlayback();
            return;
        }

        Render2DUtility.withGuiGraphics(guiGraphics, () -> {
            updateFrameTime();
            renderSelectedBackground();
            renderUserCard();
            renderCenterPanel();
            layoutMainButtons();
            updateMainButtonStates();
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            renderSharedBackgroundSelector(this.width, this.height, mouseX, mouseY, this.frameSeconds);
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        if (mouseClickedSharedBackgroundSelector(event, this.width, this.height)) {
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseScrolledSharedBackgroundSelector(mouseX, mouseY, scrollY, this.width, this.height)) {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && closeSharedBackgroundSelector()) {
            return true;
        }

        return super.keyPressed(event);
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
        this.lastFrameNanos = 0L;
        pauseSharedBackgroundPlayback();
        closeWindowsAvatarTexture();
        super.removed();
    }

    public static void renderSharedBackground(float width, float height) {
        ensureBackgroundCacheLoaded();
        BackgroundMedia selected = selectedSharedBackground();
        if (selected == null || !selected.render(0.0F, 0.0F, width, height)) {
            Render2DUtility.drawVerticalGradientRect(0.0F, 0.0F, width, height, 0xFF10131B, 0xFF05060A);
        }
    }

    public static void pauseSharedBackgroundPlayback() {
        for (BackgroundMedia background : BACKGROUND_CACHE) {
            background.pausePlayback();
        }
    }

    private void renderSelectedBackground() {
        renderSharedBackground(this.width, this.height);
    }

    private void renderCenterPanel() {
        CenterPanelBounds panel = centerPanelBounds();
        Render2DUtility.drawGaussianBlurredPanel(
            panel.x(),
            panel.y(),
            panel.width(),
            panel.height(),
            CENTER_PANEL_RADIUS,
            CENTER_PANEL_BLUR_RADIUS,
            CENTER_PANEL_BLUR,
            CENTER_PANEL_BACKGROUND,
            CENTER_PANEL_BORDER_WIDTH,
            CENTER_PANEL_BORDER
        );

        FontRenderer titleFont = displayFont(CENTER_PANEL_TITLE_SIZE);
        float titleX = panel.x() + (panel.width() - titleFont.getStringWidth(CENTER_PANEL_TITLE)) * 0.5F;
        float titleY = Math.max(8.0F, panel.y() - CENTER_PANEL_TITLE_GAP - CENTER_PANEL_TITLE_SIZE);
        titleFont.drawString(CENTER_PANEL_TITLE, titleX + 1.0F, titleY + 1.0F, CENTER_PANEL_TITLE_SHADOW);
        titleFont.drawString(CENTER_PANEL_TITLE, titleX, titleY, TEXT);
    }

    private void renderUserCard() {
        renderSharedUserCard(this.width, this.height, 0.0F, 1.0F);
    }

    public static void renderSharedUserCard(float screenWidth, float screenHeight, float offsetX, float alpha) {
        if (alpha <= 0.001F) {
            return;
        }

        float x = USER_CARD_MARGIN + offsetX;
        float y = USER_CARD_MARGIN;
        float width = sharedUserCardWidth(screenWidth);
        float height = Math.min(USER_CARD_HEIGHT, Math.max(1.0F, screenHeight - USER_CARD_MARGIN * 2.0F));
        int shadowColor = Render2DUtility.applyOpacity(USER_CARD_SHADOW, alpha);
        int blurColor = Render2DUtility.applyOpacity(USER_CARD_BLUR, alpha);
        int backgroundColor = Render2DUtility.applyOpacity(USER_CARD_BACKGROUND, alpha);
        int borderColor = Render2DUtility.applyOpacity(USER_CARD_BORDER, alpha);

        Render2DUtility.drawDropShadow(x, y, width, height, USER_CARD_RADIUS, 0.0F, 3.5F, 8.4F, shadowColor);
        Render2DUtility.drawGaussianBlurredPanel(
            x,
            y,
            width,
            height,
            USER_CARD_RADIUS,
            USER_CARD_BLUR_RADIUS,
            blurColor,
            backgroundColor,
            1.0F,
            borderColor
        );

        float avatarSize = Math.min(USER_AVATAR_SIZE, Math.max(1.0F, height - USER_CARD_PADDING * 2.0F));
        float avatarX = x + USER_CARD_PADDING;
        float avatarY = y + (height - avatarSize) * 0.5F;
        renderWindowsAvatar(avatarX, avatarY, avatarSize, alpha);

        FontRenderer nameFont = textFont(USER_NAME_SIZE);
        String windowsUserName = sharedWindowsUserName();
        String safeName = windowsUserName == null || windowsUserName.isBlank() ? "Windows User" : windowsUserName;
        float textX = avatarX + avatarSize + USER_NAME_GAP;
        float textWidth = Math.max(1.0F, x + width - USER_CARD_PADDING - textX);
        float textY = y + (height - nameFont.getLineHeight()) * 0.5F;
        String text = trimToWidth(nameFont, safeName, textWidth);
        nameFont.drawString(text, textX + 1.0F, textY + 1.0F, Render2DUtility.applyOpacity(USER_NAME_SHADOW, alpha));
        nameFont.drawString(text, textX, textY, Render2DUtility.applyOpacity(TEXT, alpha));
    }

    private static void renderWindowsAvatar(float x, float y, float size, float alpha) {
        if (sharedWindowsAvatarImage() != null) {
            ensureWindowsAvatarTexture();
        }

        float centerX = x + size * 0.5F;
        float centerY = y + size * 0.5F;
        float radius = size * 0.5F;
        int borderColor = Render2DUtility.applyOpacity(USER_CARD_BORDER, alpha);
        if (sharedWindowsAvatarTexture != null) {
            Render2DUtility.drawTexture(sharedWindowsAvatarTexture.getTextureView(), x, y, size, size,
                Render2DUtility.applyOpacity(0xFFFFFFFF, alpha));
            Render2DUtility.drawOutlineCircle(centerX, centerY, radius, 1.0F, borderColor);
            return;
        }

        Render2DUtility.drawCircle(centerX, centerY, radius, Render2DUtility.applyOpacity(CONTROL_HOVER, alpha));
        Render2DUtility.drawOutlineCircle(centerX, centerY, radius, 1.0F, borderColor);
        FontRenderer font = textFont(14.0F * USER_CARD_SCALE);
        String windowsUserName = sharedWindowsUserName();
        String initial = windowsUserName == null || windowsUserName.isBlank() ? "U" : windowsUserName.substring(0, 1).toUpperCase(Locale.ROOT);
        font.drawCenteredString(initial, centerX, y + (size - font.getLineHeight()) * 0.5F,
            Render2DUtility.applyOpacity(TEXT_MUTED, alpha));
    }

    private static void ensureWindowsAvatarTexture() {
        BufferedImage avatarImage = sharedWindowsAvatarImage();
        if (sharedWindowsAvatarTexture != null || avatarImage == null) {
            return;
        }

        sharedWindowsAvatarTexture = new DynamicTexture(
            () -> "nyx-mainui-avatar-" + AVATAR_TEXTURE_IDS.incrementAndGet(),
            toNativeImage(avatarImage)
        );
    }

    private static void closeWindowsAvatarTexture() {
        if (sharedWindowsAvatarTexture != null) {
            sharedWindowsAvatarTexture.close();
            sharedWindowsAvatarTexture = null;
        }
    }

    private void initMainButtons() {
        this.mainButtons.clear();
        this.multiplayerButton = null;
        addMainButton("Single Player", this::openSinglePlayer);
        this.multiplayerButton = addMainButton("Muti Player", this::openMultiplayer);
        addMainButton("Option", this::openOptions);
        addMainButton("Exit", this::exitGame);
        layoutMainButtons();
        updateMainButtonStates();
    }

    private MainUIButton addMainButton(String label, Runnable action) {
        MainUIButton button = addRenderableWidget(new MainUIButton(0, 0, 1, 1, Component.literal(label), action));
        this.mainButtons.add(button);
        return button;
    }

    private void layoutMainButtons() {
        if (this.mainButtons.isEmpty()) {
            return;
        }

        CenterPanelBounds panel = centerPanelBounds();
        float inset = clamp(panel.width() * 0.13F, CENTER_BUTTON_MIN_INSET, CENTER_BUTTON_MAX_INSET);
        int buttonWidth = Math.max(1, Math.round(panel.width() - inset * 2.0F));
        int buttonHeight = Math.max(1, Math.round(clamp(panel.height() * 0.11F, CENTER_BUTTON_MIN_HEIGHT, CENTER_BUTTON_HEIGHT)));
        float buttonGap = clamp(panel.height() * 0.032F, CENTER_BUTTON_MIN_GAP, CENTER_BUTTON_GAP);
        float totalHeight = this.mainButtons.size() * buttonHeight + (this.mainButtons.size() - 1) * buttonGap;
        int buttonX = Math.round(panel.x() + (panel.width() - buttonWidth) * 0.5F);
        float buttonY = panel.y() + (panel.height() - totalHeight) * 0.5F;

        for (MainUIButton button : this.mainButtons) {
            button.setX(buttonX);
            button.setY(Math.round(buttonY));
            button.setSize(buttonWidth, buttonHeight);
            buttonY += buttonHeight + buttonGap;
        }
    }

    private void updateMainButtonStates() {
        if (this.multiplayerButton != null) {
            Minecraft minecraft = Minecraft.getInstance();
            this.multiplayerButton.active = minecraft != null && minecraft.allowsMultiplayer();
        }
    }

    private void openSinglePlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new SinglePlayerUI(this));
        }
    }

    private void openMultiplayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !minecraft.allowsMultiplayer()) {
            return;
        }

        MutiPlayerUI.open(this);
    }

    private void openOptions() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new OptionsScreen(this, minecraft.options));
        }
    }

    private void exitGame() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.stop();
        }
    }

    public static void renderSharedBackgroundSelector(float width, float height, int mouseX, int mouseY, float frameSeconds) {
        ensureBackgroundCacheLoaded();
        syncSharedSelectedBackground();
        updateSharedPanelAnimation(frameSeconds);
        renderSettingsPanel(width, height, mouseX, mouseY);
        renderSettingsButton(width, mouseX, mouseY);
    }

    public static boolean mouseClickedSharedBackgroundSelector(MouseButtonEvent event, float width, float height) {
        if (event.button() != GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        ensureBackgroundCacheLoaded();
        syncSharedSelectedBackground();
        if (isInside(event.x(), event.y(), settingsButtonX(width), settingsButtonY(), SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE)) {
            sharedSettingsOpen = !sharedSettingsOpen;
            return true;
        }

        if (isSettingsPanelVisible()) {
            if (isInside(event.x(), event.y(), panelX(width), 0.0F, panelWidth(width), height)) {
                int row = backgroundRowAt(event.x(), event.y(), width);
                if (row >= 0 && row < BACKGROUND_CACHE.size()) {
                    selectBackground(row);
                }
            }
            return true;
        }

        return false;
    }

    public static boolean mouseScrolledSharedBackgroundSelector(double mouseX, double mouseY, double scrollY, float width, float height) {
        if (!isSettingsPanelVisible()) {
            return false;
        }

        float listHeight = Math.max(0.0F, height - PANEL_HEADER_HEIGHT - PANEL_PADDING);
        updatePanelScrollLimit(listHeight);
        if (isInside(mouseX, mouseY, panelX(width), PANEL_HEADER_HEIGHT, panelWidth(width), height - PANEL_HEADER_HEIGHT)) {
            sharedPanelScroll = clamp(sharedPanelScroll - (float)scrollY * SCROLL_STEP, 0.0F, sharedMaxPanelScroll);
            return true;
        }

        return false;
    }

    public static boolean closeSharedBackgroundSelector() {
        if (!sharedSettingsOpen) {
            return false;
        }

        sharedSettingsOpen = false;
        return true;
    }

    private static void renderSettingsButton(float width, int mouseX, int mouseY) {
        float x = settingsButtonX(width);
        float y = settingsButtonY();
        boolean hovered = isInside(mouseX, mouseY, x, y, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE);
        int fill = Render2DUtility.mix(CONTROL_BACKGROUND, CONTROL_HOVER, hovered || sharedSettingsOpen ? 1.0F : 0.0F);

        Render2DUtility.drawDropShadow(x, y, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE, 8.0F, 0.0F, 5.0F, 10.0F, 0x66000000);
        Render2DUtility.drawRoundedRect(x, y, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE, 8.0F, fill);
        Render2DUtility.drawOutlineRoundedRect(x, y, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE, 8.0F, 1.0F, PANEL_BORDER);
        drawResourceTexture(SETTINGS_ICON, x + 8.0F, y + 8.0F, 18.0F, 18.0F, sharedSettingsOpen ? 0xFFFFFFFF : 0xFFE2E6EF);
    }

    private static void renderSettingsPanel(float width, float height, int mouseX, int mouseY) {
        if (!isSettingsPanelVisible()) {
            return;
        }

        float progress = easeOutCubic(sharedSettingsPanelProgress);
        float panelWidth = panelWidth(width);
        float panelX = width - panelWidth * progress;
        float visibleAlpha = clamp(progress, 0.0F, 1.0F);
        Render2DUtility.drawRect(0.0F, 0.0F, width, height, Render2DUtility.applyOpacity(0x66000000, visibleAlpha * 0.65F));
        Render2DUtility.drawDropShadow(panelX, 0.0F, panelWidth, height, 0.0F, -12.0F, 0.0F, 24.0F, 0x7A000000);
        Render2DUtility.drawRect(panelX, 0.0F, panelWidth, height, Render2DUtility.applyOpacity(PANEL_BACKGROUND, visibleAlpha));
        Render2DUtility.drawRect(panelX, 0.0F, 1.0F, height, Render2DUtility.applyOpacity(PANEL_BORDER, visibleAlpha));

        float titleX = panelX + PANEL_PADDING;
        FontRenderer titleFont = displayFont(17.0F);
        FontRenderer pathFont = textFont(9.0F);
        titleFont.drawString("Background", titleX, 20.0F, Render2DUtility.applyOpacity(TEXT, visibleAlpha));
        pathFont.drawString(
            trimToWidth(pathFont, PathManager.BACKGROUND, panelWidth - PANEL_PADDING * 2.0F),
            titleX,
            45.0F,
            Render2DUtility.applyOpacity(TEXT_DIM, visibleAlpha)
        );

        float listTop = PANEL_HEADER_HEIGHT;
        float listHeight = Math.max(0.0F, height - listTop - PANEL_PADDING);
        updatePanelScrollLimit(listHeight);
        Render2DUtility.withClip(panelX, listTop, panelWidth, listHeight, () -> renderBackgroundRows(panelX, listTop, panelWidth, height, mouseX, mouseY, visibleAlpha));
    }

    private static void renderBackgroundRows(float panelX, float listTop, float panelWidth, float screenHeight, int mouseX, int mouseY, float alpha) {
        float rowX = panelX + PANEL_PADDING;
        float rowWidth = panelWidth - PANEL_PADDING * 2.0F;
        FontRenderer nameFont = textFont(11.0F);
        FontRenderer metaFont = textFont(9.0F);
        for (int i = 0; i < BACKGROUND_CACHE.size(); i++) {
            float rowY = stackedItemY(listTop, i, ROW_HEIGHT, ROW_GAP, sharedPanelScroll);
            if (rowY > screenHeight || rowY + ROW_HEIGHT < listTop) {
                continue;
            }

            BackgroundMedia background = BACKGROUND_CACHE.get(i);
            boolean selected = background.key().equals(rememberedSelectedKey);
            boolean hovered = isInside(mouseX, mouseY, rowX, rowY, rowWidth, ROW_HEIGHT);
            int fill = selected ? ROW_SELECTED : Render2DUtility.mix(ROW_BACKGROUND, ROW_HOVER, hovered ? 1.0F : 0.0F);

            Render2DUtility.drawRoundedRect(rowX, rowY, rowWidth, ROW_HEIGHT, 8.0F, Render2DUtility.applyOpacity(fill, alpha));
            Render2DUtility.drawOutlineRoundedRect(rowX, rowY, rowWidth, ROW_HEIGHT, 8.0F, 1.0F,
                Render2DUtility.applyOpacity(selected ? ACCENT : PANEL_BORDER, alpha));

            float thumbX = rowX + 10.0F;
            float thumbY = rowY + (ROW_HEIGHT - THUMBNAIL_HEIGHT) * 0.5F;
            Render2DUtility.withClip(thumbX, thumbY, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, () -> {
                boolean rendered = background.renderThumbnail(thumbX, thumbY, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
                if (!rendered) {
                    Render2DUtility.drawVerticalGradientRect(thumbX, thumbY, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, 0xFF252A35, 0xFF11141B);
                }
            });
            Render2DUtility.drawOutlineRoundedRect(thumbX, thumbY, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, 5.0F, 1.0F,
                Render2DUtility.applyOpacity(0x44FFFFFF, alpha));

            float textX = thumbX + THUMBNAIL_WIDTH + 12.0F;
            float textWidth = Math.max(1.0F, rowX + rowWidth - 12.0F - textX);
            nameFont.drawString(trimToWidth(nameFont, background.displayName(), textWidth), textX, rowY + 17.0F,
                Render2DUtility.applyOpacity(TEXT, alpha));
            metaFont.drawString(background.animated() ? "Animated" : "Image", textX, rowY + 38.0F,
                Render2DUtility.applyOpacity(selected ? 0xFFD7E6FF : TEXT_MUTED, alpha));
        }
    }

    private static void ensureBackgroundCacheLoaded() {
        if (backgroundCacheLoaded) {
            return;
        }

        backgroundCacheLoaded = true;
        BACKGROUND_CACHE.addAll(BackgroundLibrary.load());
        syncSharedSelectedBackground();
    }

    private static BackgroundMedia selectedSharedBackground() {
        syncSharedSelectedBackground();
        if (BACKGROUND_CACHE.isEmpty()) {
            return null;
        }

        int index = indexOfBackground(rememberedSelectedKey);
        return BACKGROUND_CACHE.get(Math.max(0, index));
    }

    private static void syncSharedSelectedBackground() {
        if (BACKGROUND_CACHE.isEmpty()) {
            rememberedSelectedKey = BackgroundLibrary.DEFAULT_KEY;
            sharedPanelScroll = 0.0F;
            sharedMaxPanelScroll = 0.0F;
            return;
        }

        if (indexOfBackground(rememberedSelectedKey) < 0) {
            rememberedSelectedKey = BACKGROUND_CACHE.get(0).key();
        }
    }

    private static void selectBackground(int index) {
        if (index < 0 || index >= BACKGROUND_CACHE.size()) {
            return;
        }

        rememberedSelectedKey = BACKGROUND_CACHE.get(index).key();
    }

    private static int indexOfBackground(String key) {
        for (int i = 0; i < BACKGROUND_CACHE.size(); i++) {
            if (BACKGROUND_CACHE.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
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

    private static void updateSharedPanelAnimation(float frameSeconds) {
        sharedSettingsPanelProgress = animateLinear(sharedSettingsPanelProgress, sharedSettingsOpen ? 1.0F : 0.0F, PANEL_ANIMATION_SPEED, frameSeconds);
    }

    private static void updatePanelScrollLimit(float listHeight) {
        float contentHeight = stackedContentHeight(BACKGROUND_CACHE.size(), ROW_HEIGHT, ROW_GAP);
        sharedMaxPanelScroll = Math.max(0.0F, contentHeight - listHeight);
        sharedPanelScroll = clamp(sharedPanelScroll, 0.0F, sharedMaxPanelScroll);
    }

    private static int backgroundRowAt(double mouseX, double mouseY, float screenWidth) {
        float panelX = panelX(screenWidth);
        float rowX = panelX + PANEL_PADDING;
        float rowWidth = panelWidth(screenWidth) - PANEL_PADDING * 2.0F;
        for (int i = 0; i < BACKGROUND_CACHE.size(); i++) {
            float rowY = stackedItemY(PANEL_HEADER_HEIGHT, i, ROW_HEIGHT, ROW_GAP, sharedPanelScroll);
            if (isInside(mouseX, mouseY, rowX, rowY, rowWidth, ROW_HEIGHT)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSettingsPanelVisible() {
        return sharedSettingsOpen || sharedSettingsPanelProgress > 0.001F;
    }

    private boolean isActiveScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.screen == this;
    }

    private static float settingsButtonX(float screenWidth) {
        return screenWidth - SETTINGS_BUTTON_MARGIN - SETTINGS_BUTTON_SIZE;
    }

    private static float settingsButtonY() {
        return SETTINGS_BUTTON_MARGIN;
    }

    private CenterPanelBounds centerPanelBounds() {
        float maxWidth = Math.max(1.0F, Math.min(CENTER_PANEL_MAX_WIDTH, this.width - 32.0F));
        float minWidth = Math.min(CENTER_PANEL_MIN_WIDTH, maxWidth);
        float panelWidth = clamp(this.width * 0.336F, minWidth, maxWidth);

        float maxHeight = Math.max(1.0F, Math.min(CENTER_PANEL_MAX_HEIGHT, this.height - 32.0F));
        float minHeight = Math.min(CENTER_PANEL_MIN_HEIGHT, maxHeight);
        float panelHeight = clamp(this.height * 0.432F, minHeight, maxHeight);

        return new CenterPanelBounds(
            (this.width - panelWidth) * 0.5F,
            (this.height - panelHeight) * 0.5F,
            panelWidth,
            panelHeight
        );
    }

    public static float sharedUserCardWidth(float screenWidth) {
        float settingsSafeMax = settingsButtonX(screenWidth) - USER_CARD_MARGIN - 8.0F;
        float screenSafeMax = screenWidth - USER_CARD_MARGIN * 2.0F;
        float maxWidth = Math.max(1.0F, Math.min(USER_CARD_MAX_WIDTH, Math.min(settingsSafeMax, screenSafeMax)));
        float minWidth = Math.min(USER_CARD_MIN_WIDTH, maxWidth);
        return clamp(screenWidth * 0.34F, minWidth, maxWidth);
    }

    private static float panelWidth(float screenWidth) {
        float upper = Math.max(120.0F, Math.min(PANEL_MAX_WIDTH, screenWidth - 24.0F));
        float lower = Math.min(PANEL_MIN_WIDTH, upper);
        return clamp(screenWidth * 0.78F, lower, upper);
    }

    private static float panelX(float screenWidth) {
        return screenWidth - panelWidth(screenWidth) * easeOutCubic(sharedSettingsPanelProgress);
    }

    private static void drawResourceTexture(Identifier resource, float x, float y, float width, float height, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        Render2DUtility.drawTexture(minecraft.getTextureManager().getTexture(resource).getTextureView(), x, y, width, height, color);
    }

    private static FontRenderer displayFont(float size) {
        return FontManager.getAppleDisplayRenderer(size);
    }

    private static FontRenderer textFont(float size) {
        return FontManager.getAppleTextRenderer(size);
    }

    private static String trimToWidth(FontRenderer renderer, String text, float maxWidth) {
        if (maxWidth <= 0.0F) {
            return "";
        }
        if (text == null || text.isEmpty() || renderer.getStringWidth(text) <= maxWidth) {
            return text == null ? "" : text;
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

    private static String loadWindowsUserName() {
        try {
            String userName = MicrosoftUtility.getCurrentComputerUserName();
            if (userName == null || userName.isBlank()) {
                userName = MicrosoftUtility.getCurrentWindowsAccountName();
            }
            return formatWindowsUserName(userName);
        } catch (RuntimeException ignored) {
            return "Windows User";
        }
    }

    private static String sharedWindowsUserName() {
        if (!sharedWindowsUserNameLoaded) {
            sharedWindowsUserNameLoaded = true;
            sharedWindowsUserName = loadWindowsUserName();
        }

        return sharedWindowsUserName;
    }

    private static BufferedImage sharedWindowsAvatarImage() {
        if (!sharedWindowsAvatarImageLoaded) {
            sharedWindowsAvatarImageLoaded = true;
            sharedWindowsAvatarImage = loadWindowsAvatarImage();
        }

        return sharedWindowsAvatarImage;
    }

    private static String formatWindowsUserName(String userName) {
        if (userName == null || userName.isBlank()) {
            return "Windows User";
        }

        String displayName = userName.strip();
        int separator = Math.max(displayName.lastIndexOf('\\'), displayName.lastIndexOf('/'));
        if (separator >= 0 && separator + 1 < displayName.length()) {
            displayName = displayName.substring(separator + 1);
        }
        if (displayName.isBlank()) {
            return "Windows User";
        }

        return displayName.substring(0, 1).toUpperCase(Locale.ROOT) + displayName.substring(1);
    }

    private static BufferedImage loadWindowsAvatarImage() {
        try {
            return MicrosoftUtility.getCurrentMicrosoftAccountAvatarImage()
                .map(image -> circularImage(image, AVATAR_TEXTURE_SIZE))
                .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static BufferedImage circularImage(BufferedImage source, int size) {
        int safeSize = Math.max(1, size);
        int supersampledSize = safeSize * AVATAR_SUPERSAMPLE;
        BufferedImage image = new BufferedImage(supersampledSize, supersampledSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            applyAvatarRenderingHints(graphics);

            int sourceSize = Math.min(source.getWidth(), source.getHeight());
            int sourceX = (source.getWidth() - sourceSize) / 2;
            int sourceY = (source.getHeight() - sourceSize) / 2;
            graphics.drawImage(source, 0, 0, supersampledSize, supersampledSize,
                sourceX, sourceY, sourceX + sourceSize, sourceY + sourceSize, null);
        } finally {
            graphics.dispose();
        }

        applyCircleAlphaMask(image);
        return scaleAvatarImage(image, safeSize);
    }

    private static void applyCircleAlphaMask(BufferedImage image) {
        int size = image.getWidth();
        BufferedImage mask = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = mask.createGraphics();
        try {
            applyAvatarRenderingHints(graphics);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillOval(0, 0, size, size);
        } finally {
            graphics.dispose();
        }

        WritableRaster alpha = image.getAlphaRaster();
        WritableRaster maskRaster = mask.getRaster();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int maskedAlpha = alpha.getSample(x, y, 0) * maskRaster.getSample(x, y, 0) / 255;
                alpha.setSample(x, y, 0, maskedAlpha);
            }
        }
    }

    private static BufferedImage scaleAvatarImage(BufferedImage source, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            applyAvatarRenderingHints(graphics);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void applyAvatarRenderingHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
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

    private record CenterPanelBounds(float x, float y, float width, float height) {
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }
}
