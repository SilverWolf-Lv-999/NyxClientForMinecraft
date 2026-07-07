package io.github.seraphina.nyx.client.ui.mainui;

import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.manager.PathManager;
import io.github.seraphina.nyx.client.ui.mainui.background.BackgroundLibrary;
import io.github.seraphina.nyx.client.ui.mainui.background.BackgroundMedia;
import io.github.seraphina.nyx.client.ui.mainui.button.MainUIButton;
import io.github.seraphina.nyx.client.ui.player.SinglePlayerUI;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

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

    private static final List<BackgroundMedia> BACKGROUND_CACHE = new ArrayList<>();

    private static String rememberedSelectedKey = BackgroundLibrary.DEFAULT_KEY;
    private static boolean backgroundCacheLoaded;

    private final List<BackgroundMedia> backgrounds = BACKGROUND_CACHE;
    private final List<MainUIButton> mainButtons = new ArrayList<>();

    private String selectedKey = rememberedSelectedKey;
    private int selectedIndex;
    private boolean settingsOpen;
    private float settingsPanelProgress;
    private float panelScroll;
    private float maxPanelScroll;
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
        syncSelectedBackground();
        initMainButtons();
        this.lastFrameNanos = 0L;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!isActiveScreen()) {
            pauseBackgroundPlayback();
            return;
        }

        Render2DUtility.withGuiGraphics(guiGraphics, () -> {
            updateFrameTime();
            updatePanelAnimation();
            renderSelectedBackground();
            renderCenterPanel();
            layoutMainButtons();
            updateMainButtonStates();
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            renderSettingsPanel(mouseX, mouseY);
            renderSettingsButton(mouseX, mouseY);
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        if (isInside(event.x(), event.y(), settingsButtonX(), settingsButtonY(), SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE)) {
            this.settingsOpen = !this.settingsOpen;
            if (this.settingsOpen) {
                ensureBackgroundCacheLoaded();
            }
            return true;
        }

        if (isSettingsPanelVisible()) {
            if (isInside(event.x(), event.y(), panelX(), 0.0F, panelWidth(), this.height)) {
                int row = backgroundRowAt(event.x(), event.y());
                if (row >= 0 && row < this.backgrounds.size()) {
                    selectBackground(row);
                }
            }
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isSettingsPanelVisible() && isInside(mouseX, mouseY, panelX(), PANEL_HEADER_HEIGHT, panelWidth(), this.height - PANEL_HEADER_HEIGHT)) {
            this.panelScroll = clamp(this.panelScroll - (float)scrollY * SCROLL_STEP, 0.0F, this.maxPanelScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && this.settingsOpen) {
            this.settingsOpen = false;
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
        this.settingsOpen = false;
        this.settingsPanelProgress = 0.0F;
        this.lastFrameNanos = 0L;
        pauseBackgroundPlayback();
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

        Screen screen = minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
        minecraft.setScreen(screen);
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

    private void renderSettingsButton(int mouseX, int mouseY) {
        float x = settingsButtonX();
        float y = settingsButtonY();
        boolean hovered = isInside(mouseX, mouseY, x, y, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE);
        int fill = Render2DUtility.mix(CONTROL_BACKGROUND, CONTROL_HOVER, hovered || this.settingsOpen ? 1.0F : 0.0F);

        Render2DUtility.drawDropShadow(x, y, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE, 8.0F, 0.0F, 5.0F, 10.0F, 0x66000000);
        Render2DUtility.drawRoundedRect(x, y, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE, 8.0F, fill);
        Render2DUtility.drawOutlineRoundedRect(x, y, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE, 8.0F, 1.0F, PANEL_BORDER);
        drawResourceTexture(SETTINGS_ICON, x + 8.0F, y + 8.0F, 18.0F, 18.0F, this.settingsOpen ? 0xFFFFFFFF : 0xFFE2E6EF);
    }

    private void renderSettingsPanel(int mouseX, int mouseY) {
        if (!isSettingsPanelVisible()) {
            return;
        }

        float progress = easeOutCubic(this.settingsPanelProgress);
        float panelWidth = panelWidth();
        float panelX = this.width - panelWidth * progress;
        float visibleAlpha = clamp(progress, 0.0F, 1.0F);
        Render2DUtility.drawRect(0.0F, 0.0F, this.width, this.height, Render2DUtility.applyOpacity(0x66000000, visibleAlpha * 0.65F));
        Render2DUtility.drawDropShadow(panelX, 0.0F, panelWidth, this.height, 0.0F, -12.0F, 0.0F, 24.0F, 0x7A000000);
        Render2DUtility.drawRect(panelX, 0.0F, panelWidth, this.height, Render2DUtility.applyOpacity(PANEL_BACKGROUND, visibleAlpha));
        Render2DUtility.drawRect(panelX, 0.0F, 1.0F, this.height, Render2DUtility.applyOpacity(PANEL_BORDER, visibleAlpha));

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
        float listHeight = Math.max(0.0F, this.height - listTop - PANEL_PADDING);
        updatePanelScrollLimit(listHeight);
        Render2DUtility.withClip(panelX, listTop, panelWidth, listHeight, () -> renderBackgroundRows(panelX, listTop, panelWidth, mouseX, mouseY, visibleAlpha));
    }

    private void renderBackgroundRows(float panelX, float listTop, float panelWidth, int mouseX, int mouseY, float alpha) {
        float rowX = panelX + PANEL_PADDING;
        float rowWidth = panelWidth - PANEL_PADDING * 2.0F;
        FontRenderer nameFont = textFont(11.0F);
        FontRenderer metaFont = textFont(9.0F);
        for (int i = 0; i < this.backgrounds.size(); i++) {
            float rowY = rowY(listTop, i);
            if (rowY > this.height || rowY + ROW_HEIGHT < listTop) {
                continue;
            }

            BackgroundMedia background = this.backgrounds.get(i);
            boolean selected = i == this.selectedIndex;
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
    }

    private static BackgroundMedia selectedSharedBackground() {
        if (BACKGROUND_CACHE.isEmpty()) {
            return null;
        }

        int index = 0;
        for (int i = 0; i < BACKGROUND_CACHE.size(); i++) {
            if (BACKGROUND_CACHE.get(i).key().equals(rememberedSelectedKey)) {
                index = i;
                break;
            }
        }
        return BACKGROUND_CACHE.get(index);
    }

    private void syncSelectedBackground() {
        String keyToKeep = this.selectedKey == null ? rememberedSelectedKey : this.selectedKey;
        this.selectedIndex = indexOfBackground(keyToKeep);
        if (this.selectedIndex < 0) {
            this.selectedIndex = 0;
        }
        this.selectedKey = this.backgrounds.isEmpty() ? BackgroundLibrary.DEFAULT_KEY : this.backgrounds.get(this.selectedIndex).key();
        rememberedSelectedKey = this.selectedKey;
        this.panelScroll = 0.0F;
    }

    private void selectBackground(int index) {
        if (index < 0 || index >= this.backgrounds.size()) {
            return;
        }

        this.selectedIndex = index;
        this.selectedKey = this.backgrounds.get(index).key();
        rememberedSelectedKey = this.selectedKey;
    }

    private int indexOfBackground(String key) {
        for (int i = 0; i < this.backgrounds.size(); i++) {
            if (this.backgrounds.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private BackgroundMedia selectedBackground() {
        if (this.backgrounds.isEmpty()) {
            ensureBackgroundCacheLoaded();
            syncSelectedBackground();
        }
        if (this.backgrounds.isEmpty()) {
            return null;
        }

        this.selectedIndex = Math.max(0, Math.min(this.selectedIndex, this.backgrounds.size() - 1));
        return this.backgrounds.get(this.selectedIndex);
    }

    private void pauseBackgroundPlayback() {
        for (BackgroundMedia background : this.backgrounds) {
            background.pausePlayback();
        }
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

    private void updatePanelAnimation() {
        this.settingsPanelProgress = animate(this.settingsPanelProgress, this.settingsOpen ? 1.0F : 0.0F, PANEL_ANIMATION_SPEED);
    }

    private void updatePanelScrollLimit(float listHeight) {
        float contentHeight = this.backgrounds.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
        this.maxPanelScroll = Math.max(0.0F, contentHeight - listHeight);
        this.panelScroll = clamp(this.panelScroll, 0.0F, this.maxPanelScroll);
    }

    private int backgroundRowAt(double mouseX, double mouseY) {
        float panelX = panelX();
        float rowX = panelX + PANEL_PADDING;
        float rowWidth = panelWidth() - PANEL_PADDING * 2.0F;
        for (int i = 0; i < this.backgrounds.size(); i++) {
            float rowY = rowY(PANEL_HEADER_HEIGHT, i);
            if (isInside(mouseX, mouseY, rowX, rowY, rowWidth, ROW_HEIGHT)) {
                return i;
            }
        }
        return -1;
    }

    private float rowY(float listTop, int index) {
        return listTop + index * (ROW_HEIGHT + ROW_GAP) - this.panelScroll;
    }

    private boolean isSettingsPanelVisible() {
        return this.settingsOpen || this.settingsPanelProgress > 0.001F;
    }

    private boolean isActiveScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.screen == this;
    }

    private float settingsButtonX() {
        return this.width - SETTINGS_BUTTON_MARGIN - SETTINGS_BUTTON_SIZE;
    }

    private float settingsButtonY() {
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

    private float panelWidth() {
        float upper = Math.max(120.0F, Math.min(PANEL_MAX_WIDTH, this.width - 24.0F));
        float lower = Math.min(PANEL_MIN_WIDTH, upper);
        return clamp(this.width * 0.78F, lower, upper);
    }

    private float panelX() {
        return this.width - panelWidth() * easeOutCubic(this.settingsPanelProgress);
    }

    private void drawResourceTexture(Identifier resource, float x, float y, float width, float height, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        Render2DUtility.drawTexture(minecraft.getTextureManager().getTexture(resource).getTextureView(), x, y, width, height, color);
    }

    private FontRenderer displayFont(float size) {
        return FontManager.getAppleDisplayRenderer(size);
    }

    private FontRenderer textFont(float size) {
        return FontManager.getAppleTextRenderer(size);
    }

    private String trimToWidth(FontRenderer renderer, String text, float maxWidth) {
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

    private float animate(float current, float target, float speed) {
        if (current == target) {
            return current;
        }

        float step = Math.min(1.0F, this.frameSeconds * speed);
        float next = current + (target - current) * step;
        return Math.abs(target - next) < 0.001F ? target : next;
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isInside(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
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
