package io.github.seraphina.nyx.client.ui.clickgui;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.manager.ModuleManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.ui.clickgui.component.*;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.utility.web.MicrosoftUtility;
import io.github.seraphina.nyx.client.value.AbstractValue;
import io.github.seraphina.nyx.client.value.impl.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.seraphina.nyx.client.utility.MathUtility.animateExp;
import static io.github.seraphina.nyx.client.utility.MathUtility.clamp;
import static io.github.seraphina.nyx.client.utility.MathUtility.easeOutCubic;
import static io.github.seraphina.nyx.client.utility.MathUtility.isInsideExclusive;
import static io.github.seraphina.nyx.client.utility.MathUtility.lerp;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetKey;

public class ClickGuiUI extends Screen {
    private static final float PANEL_WIDTH = 820.0F;
    private static final float PANEL_HEIGHT = 540.0F;
    private static final float UI_SCALE = 1.7F;
    private static final float MIN_MARGIN = 16.0F;
    private static final float SIDEBAR_WIDTH = 220.0F;
    private static final float SIDEBAR_WIDTH_COMPACT = 172.0F;
    private static final float HEADER_HEIGHT = 64.0F;
    private static final float LOGO_AREA_HEIGHT = 64.0F;
    private static final float USER_AREA_HEIGHT = 72.0F;
    private static final float NAV_ITEM_HEIGHT = 34.0F;
    private static final float CONTENT_PADDING = 16.0F;
    private static final float CONTENT_TOP_GAP = 24.0F;
    private static final float COLUMN_GAP = 20.0F;
    private static final float COLUMN_GAP_COMPACT = 12.0F;
    private static final float GROUP_HEADER_HEIGHT = 18.0F;
    private static final float GROUP_PADDING = 4.0F;
    private static final float MODULE_ROW_HEIGHT = 50.0F;
    private static final float EXPANDED_TOP_PADDING = 8.0F;
    private static final float EXPANDED_BOTTOM_PADDING = 8.0F;
    private static final float BOTTOM_PADDING = 28.0F;
    private static final float SCROLL_STEP = 34.0F;
    private static final int AVATAR_TEXTURE_SIZE = 128;
    private static final int AVATAR_SUPERSAMPLE = 4;
    private static final long CATEGORY_SWITCH_ANIMATION_NANOS = 260_000_000L;
    private static final long SCREEN_TRANSITION_ANIMATION_NANOS = 180_000_000L;
    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;
    private static final float MODULE_EXPAND_ANIMATION_SPEED = 13.0F;
    private static final float MODULE_TOGGLE_ANIMATION_SPEED = 16.0F;
    private static final float MODULE_HOVER_ANIMATION_SPEED = 18.0F;
    private static final float SCREEN_TRANSITION_MIN_SCALE = 0.86F;

    private static final int SCREEN_DIM = 0xB005060A;
    private static final int PANEL_BACKGROUND = 0xFF0C0D11;
    private static final int SIDEBAR_OVERLAY = 0x33000000;
    private static final int CARD_BACKGROUND = 0xFF14161D;
    private static final int CONTROL_BACKGROUND = 0xFF0C0D11;
    private static final int CONTROL_HOVER = 0xFF181B24;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFA0A5B5;
    private static final int TEXT_DIM = 0xFF4B5263;
    private static final int TEXT_SUBTLE = 0xFF6C717E;
    private static final int BORDER = 0x1AFFFFFF;
    private static final int BORDER_SOFT = 0x0AFFFFFF;
    private static final int DIVIDER = 0x08FFFFFF;
    private static final int HOVER = 0x0AFFFFFF;
    private static final int ACCENT = 0xB33D81F7;
    private static final int ACCENT_SOLID = 0xFF3D81F7;
    private static final int ACCENT_DARK = 0xFF1A4DA3;
    private static final int TOGGLE_OFF = 0xFF20222B;

    private static final AtomicInteger AVATAR_TEXTURE_IDS = new AtomicInteger();

    private final Map<Module, Boolean> expandedModules = new IdentityHashMap<>();
    private final Map<Module, ModuleAnimationState> moduleAnimations = new IdentityHashMap<>();
    private final Map<AbstractValue<?>, AbstractComponent> valueComponents = new IdentityHashMap<>();
    private final String userName;
    @Nullable
    private final BufferedImage avatarImage;

    private Category selectedCategory = Category.COMBAT;
    private float scroll;
    private float maxScroll;
    private float panelX;
    private float panelY;
    private float panelWidth;
    private float panelHeight;
    private float sidebarWidth;
    private boolean panelPositionInitialized;
    private boolean draggingPanel;
    private float dragOffsetX;
    private float dragOffsetY;
    @Nullable
    private Category previousCategory;
    private float previousCategoryScroll;
    private long categorySwitchStartedAtNanos;
    private int categorySwitchDirection = 1;
    private float categorySwitchProgress = 1.0F;
    private long screenTransitionStartedAtNanos;
    private float screenTransitionProgress = 1.0F;
    private boolean closing;
    private boolean closingCompleted;
    private long lastAnimationFrameNanos;
    private float animationFrameSeconds = DEFAULT_FRAME_SECONDS;
    @Nullable
    private AbstractComponent capturedComponent;
    @Nullable
    private DynamicTexture avatarTexture;

    public ClickGuiUI() {
        super(Component.empty());
        this.userName = MicrosoftUtility.getCurrentComputerUserName();
        this.avatarImage = loadAvatarImage();
    }

    @Override
    protected void init() {
        if (screenTransitionStartedAtNanos == 0L && !closing) {
            beginOpenAnimation();
        }
    }

    public void beginOpenAnimation() {
        closing = false;
        closingCompleted = false;
        screenTransitionProgress = 0.0F;
        screenTransitionStartedAtNanos = System.nanoTime();
        lastAnimationFrameNanos = 0L;
        draggingPanel = false;
        capturedComponent = null;
        blurComponentsExcept(null);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        Render2DUtility.withGuiGraphics(guiGraphics, () -> {
            updatePanelMetrics();
            ensureVisibleCategory();
            updateAnimationFrame();
            updateScreenTransitionAnimation();
            if (finishClosingIfNeeded()) {
                return;
            }
            updateCategorySwitchAnimation();
            updateModuleAnimations();
            updateScrollLimit();

            float scale = coordinateScale();
            int fixedMouseX = Math.round(mouseX * scale);
            int fixedMouseY = Math.round(mouseY * scale);
            Render2DUtility.withScale(1.0F / scale, 1.0F / scale, 0.0F, 0.0F, () -> {
                Render2DUtility.drawRect(0.0F, 0.0F, fixedScreenWidth(), fixedScreenHeight(), screenDimColor());
                Render2DUtility.withScale(screenTransitionScale(), screenTransitionScale(), panelCenterX(), panelCenterY(), () -> renderPanel(fixedMouseX, fixedMouseY));
            });
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isInteractive()) {
            return true;
        }

        updatePanelMetrics();
        ensureVisibleCategory();
        updateCategorySwitchAnimation();

        double fixedMouseX = fixedMouseX(event.x());
        double fixedMouseY = fixedMouseY(event.y());

        if (event.button() == GLFW_MOUSE_BUTTON_LEFT) {
            if (isInsideDragArea(fixedMouseX, fixedMouseY)) {
                blurComponentsExcept(null);
                draggingPanel = true;
                dragOffsetX = (float)fixedMouseX - panelX;
                dragOffsetY = (float)fixedMouseY - panelY;
                return true;
            }

            Category category = getCategoryAt(fixedMouseX, fixedMouseY);
            if (category != null) {
                blurComponentsExcept(null);
                selectCategory(category);
                return true;
            }
        }

        AbstractComponent component = getComponentAt(fixedMouseX, fixedMouseY);
        if (component != null) {
            if (event.button() == GLFW_MOUSE_BUTTON_LEFT && isLeftShiftDown() && component.value().isSerializable()) {
                blurComponentsExcept(null);
                resetValue(component.value());
                updateScrollLimit();
                return true;
            }

            blurComponentsExcept(component);
            if (component.mouseClicked(fixedMouseX, fixedMouseY, event.button())) {
                capturedComponent = component;
                updateScrollLimit();
                return true;
            }
        } else if (event.button() == GLFW_MOUSE_BUTTON_LEFT) {
            blurComponentsExcept(null);
        }

        ModuleRowLayout row = getModuleRowAt(fixedMouseX, fixedMouseY);
        if (row == null) {
            return super.mouseClicked(event, doubleClick);
        }

        if (event.button() == GLFW_MOUSE_BUTTON_LEFT) {
            row.module().toggle();
            return true;
        }

        if (event.button() == GLFW_MOUSE_BUTTON_RIGHT) {
            if (canExpandModule(row.module())) {
                setModuleExpanded(row.module(), !isExpanded(row.module()));
                updateScrollLimit();
            }
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!isInteractive()) {
            return true;
        }

        double fixedMouseX = fixedMouseX(event.x());
        double fixedMouseY = fixedMouseY(event.y());

        if (capturedComponent != null) {
            AbstractComponent component = capturedComponent;
            capturedComponent = null;
            boolean handled = component.mouseReleased(fixedMouseX, fixedMouseY, event.button());
            updateScrollLimit();
            if (handled) {
                return true;
            }
        }

        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && draggingPanel) {
            draggingPanel = false;
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!isInteractive()) {
            return true;
        }

        if (capturedComponent != null) {
            boolean handled = capturedComponent.mouseDragged(fixedMouseX(event.x()), fixedMouseY(event.y()), event.button(), dragX, dragY);
            updateScrollLimit();
            if (handled) {
                return true;
            }
        }

        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && draggingPanel) {
            updatePanelMetrics();
            panelX = (float)fixedMouseX(event.x()) - dragOffsetX;
            panelY = (float)fixedMouseY(event.y()) - dragOffsetY;
            clampPanelToScreen();
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isInteractive()) {
            return true;
        }

        updatePanelMetrics();
        updateCategorySwitchAnimation();
        updateScrollLimit();

        double fixedMouseX = fixedMouseX(mouseX);
        double fixedMouseY = fixedMouseY(mouseY);
        AbstractComponent component = getComponentAt(fixedMouseX, fixedMouseY);
        if (component != null && component.mouseScrolled(fixedMouseX, fixedMouseY, scrollY)) {
            updateScrollLimit();
            return true;
        }

        if (maxScroll <= 0.0F || !isInsideExclusive(fixedMouseX, fixedMouseY, mainX(), panelY + HEADER_HEIGHT, mainWidth(), panelHeight - HEADER_HEIGHT)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        scroll = clamp(scroll - (float)scrollY * SCROLL_STEP, 0.0F, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isInteractive()) {
            if (event.isEscape()) {
                beginCloseAnimation();
            }
            return true;
        }

        for (AbstractComponent component : valueComponents.values()) {
            if (component.keyPressed(event)) {
                updateScrollLimit();
                return true;
            }
        }

        if (event.isEscape()) {
            beginCloseAnimation();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!isInteractive()) {
            return true;
        }

        for (AbstractComponent component : valueComponents.values()) {
            if (component.charTyped(event)) {
                updateScrollLimit();
                return true;
            }
        }

        return super.charTyped(event);
    }

    @Override
    public void tick() {
        super.tick();
        for (AbstractComponent component : valueComponents.values()) {
            component.tick();
        }
    }

    @Override
    public void removed() {
        draggingPanel = false;
        capturedComponent = null;
        blurComponentsExcept(null);
        closeAvatarTexture();
        closing = false;
        closingCompleted = false;
        screenTransitionStartedAtNanos = 0L;
        screenTransitionProgress = 1.0F;
        lastAnimationFrameNanos = 0L;
        super.removed();
    }

    @Override
    public void onClose() {
        beginCloseAnimation();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderPanel(int mouseX, int mouseY) {
        Render2DUtility.drawDropShadow(panelX, panelY, panelWidth, panelHeight, 12.0F, 0.0F, 18.0F, 30.0F, 0xA0000000);
        Render2DUtility.drawRoundedRect(panelX, panelY, panelWidth, panelHeight, 12.0F, PANEL_BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(panelX, panelY, panelWidth, panelHeight, 12.0F, 1.0F, BORDER);

        Render2DUtility.withClip(panelX, panelY, panelWidth, panelHeight, () -> {
            renderSidebar(mouseX, mouseY);
            renderMain(mouseX, mouseY);
        });
    }

    private void renderSidebar(int mouseX, int mouseY) {
        float x = panelX;
        float y = panelY;
        float width = sidebarWidth;
        float pad = sidebarPadding();

        Render2DUtility.drawRect(x, y, width, panelHeight, SIDEBAR_OVERLAY);
        Render2DUtility.drawRect(x + width - 1.0F, y + 12.0F, 1.0F, panelHeight - 24.0F, BORDER_SOFT);

        renderLogo(x + pad, y);
        Render2DUtility.drawRect(x + 16.0F, y + LOGO_AREA_HEIGHT - 1.0F, width - 32.0F, 1.0F, BORDER_SOFT);

        FontRenderer sectionFont = clickGuiFont(9.0F);
        float navTop = y + LOGO_AREA_HEIGHT + 28.0F;
        sectionFont.drawString("MODULES", x + pad, navTop, TEXT_DIM);
        navTop += 17.0F;

        renderCategoryNavSelection(x, width);
        for (Category category : Category.values()) {
            List<Module> modules = ModuleManager.getModules(category);
            if (modules.isEmpty()) {
                continue;
            }

            renderCategoryNavItem(category, x, navTop, width, pad, mouseX, mouseY);
            navTop += NAV_ITEM_HEIGHT + 4.0F;
        }

        renderUserArea(x, y + panelHeight - USER_AREA_HEIGHT, width, pad);
    }

    private void renderLogo(float x, float y) {
        FontRenderer titleFont = clickGuiFont(22.0F);
        FontRenderer versionFont = clickGuiFont(8.0F);
        String title = NyxClient.CLIENT_NAME;
        String version = NyxClient.VERSION;
        float titleX = x;
        float titleY = y + centeredTextY(LOGO_AREA_HEIGHT, titleFont) - 1.0F;
        float versionX = titleX + titleFont.getStringWidth(title) + 3.0F;
        float versionY = titleY - 3.0F;

        titleFont.drawString(title, titleX, titleY, TEXT);
        drawOutlinedString(versionFont, version, versionX, versionY, TEXT_DIM, 0xF0000000);
    }

    private void renderCategoryNavItem(Category category, float sidebarX, float y, float width, float pad, int mouseX, int mouseY) {
        boolean hovered = isInsideExclusive(mouseX, mouseY, sidebarX, y, width, NAV_ITEM_HEIGHT);
        float activeAmount = categoryActiveAmount(category);

        if (hovered && activeAmount < 0.1F) {
            Render2DUtility.drawRect(sidebarX, y, width, NAV_ITEM_HEIGHT, 0x03FFFFFF);
        }

        FontRenderer iconFont = clickGuiFont(11.0F);
        FontRenderer labelFont = clickGuiFont(12.0F);
        int baseColor = hovered ? TEXT : TEXT_SUBTLE;
        int color = activeAmount <= 0.0F ? baseColor : Render2DUtility.mix(baseColor, ACCENT, activeAmount);
        float iconX = sidebarX + pad;
        float iconY = y + (NAV_ITEM_HEIGHT - 18.0F) * 0.5F;
        Render2DUtility.drawRoundedRect(iconX, iconY, 18.0F, 18.0F, 4.0F, 0xFF11131A);
        if (activeAmount > 0.0F) {
            Render2DUtility.drawRoundedRect(iconX, iconY, 18.0F, 18.0F, 4.0F, Render2DUtility.applyOpacity(0x183D81F7, activeAmount));
        }
        iconFont.drawCenteredString(category.name().substring(0, 1), iconX + 9.0F, iconY + centeredTextY(18.0F, iconFont), color);
        labelFont.drawString(categoryLabel(category), iconX + 30.0F, y + centeredTextY(NAV_ITEM_HEIGHT, labelFont), color);
    }

    private void renderCategoryNavSelection(float sidebarX, float width) {
        float selectedY = categoryNavY(selectedCategory);
        if (Float.isNaN(selectedY)) {
            return;
        }

        float y = selectedY;
        if (previousCategory != null) {
            float previousY = categoryNavY(previousCategory);
            if (!Float.isNaN(previousY)) {
                y = lerp(previousY, selectedY, categorySwitchProgress);
            }
        }

        Render2DUtility.drawHorizontalGradientRect(sidebarX, y, width, NAV_ITEM_HEIGHT, 0x143D81F7, 0x003D81F7);
        Render2DUtility.drawRect(sidebarX, y, 2.0F, NAV_ITEM_HEIGHT, ACCENT);
    }

    private void renderUserArea(float x, float y, float width, float pad) {
        Render2DUtility.drawRect(x + 16.0F, y, width - 32.0F, 1.0F, BORDER_SOFT);

        float avatarSize = 32.0F;
        float avatarX = x + pad;
        float avatarY = y + 20.0F;
        renderAvatar(avatarX, avatarY, avatarSize);

        if (sidebarWidth >= 165.0F) {
            FontRenderer nameFont = clickGuiFont(11.0F);
            FontRenderer subFont = clickGuiFont(9.0F);
            float textX = avatarX + avatarSize + 12.0F;
            float maxTextWidth = Math.max(20.0F, x + width - pad - textX);
            String safeName = userName == null || userName.isBlank() ? "User" : userName;
            nameFont.drawString(trimToWidth(nameFont, safeName, maxTextWidth), textX, avatarY + 3.0F, TEXT);
            subFont.drawString("Windows", textX, avatarY + 20.0F, TEXT_DIM);
        }
    }

    private void renderMain(int mouseX, int mouseY) {
        renderHeader();
        Render2DUtility.withClip(mainX(), panelY + HEADER_HEIGHT, mainWidth(), panelHeight - HEADER_HEIGHT, () -> {
            renderContent(mouseX, mouseY);
        });
    }

    private void renderHeader() {
        float x = mainX();
        float width = mainWidth();
        Render2DUtility.drawRect(x, panelY, width, HEADER_HEIGHT, PANEL_BACKGROUND);
        Render2DUtility.drawRect(x + 16.0F, panelY + HEADER_HEIGHT - 1.0F, width - 32.0F, 1.0F, BORDER_SOFT);

        float buttonY = panelY + (HEADER_HEIGHT - 28.0F) * 0.5F;
        float buttonX = x + 16.0F;
        renderHeaderButton(buttonX, buttonY, 150.0F, selectedCategoryLabel(), "S");
        renderHeaderButton(buttonX + 162.0F, buttonY, 96.0F, "Global", null);

        FontRenderer searchFont = clickGuiFont(16.0F);
        float searchX = x + width - 32.0F;
        searchFont.drawCenteredString("/", searchX, panelY + centeredTextY(HEADER_HEIGHT, searchFont), TEXT_DIM);
    }

    private void renderHeaderButton(float x, float y, float width, String label, @Nullable String icon) {
        Render2DUtility.drawRoundedRect(x, y, width, 28.0F, 4.0F, CONTROL_BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(x, y, width, 28.0F, 4.0F, 1.0F, BORDER_SOFT);

        FontRenderer font = clickGuiFont(11.0F);
        float labelX = x + 10.0F;
        if (icon != null) {
            Render2DUtility.drawRect(x + 31.0F, y, 1.0F, 28.0F, BORDER_SOFT);
            font.drawCenteredString(icon, x + 15.5F, y + centeredTextY(28.0F, font), TEXT_SUBTLE);
            labelX = x + 42.0F;
        }

        float labelWidth = Math.max(1.0F, x + width - 22.0F - labelX);
        font.drawString(trimToWidth(font, label, labelWidth), labelX, y + centeredTextY(28.0F, font), TEXT_MUTED);
        font.drawString("v", x + width - 16.0F, y + centeredTextY(28.0F, font), TEXT_SUBTLE);
    }

    private void renderContent(int mouseX, int mouseY) {
        if (previousCategory != null) {
            renderContent(previousCategory, previousCategoryScroll, outgoingCategoryContentOffset(), mouseX, mouseY);
            renderContent(selectedCategory, scroll, incomingCategoryContentOffset(), mouseX, mouseY);
            return;
        }

        renderContent(selectedCategory, scroll, 0.0F, mouseX, mouseY);
    }

    private void renderContent(Category category, float scrollOffset, float yOffset, int mouseX, int mouseY) {
        List<Module> modules = modulesForCategory(category);
        float contentX = mainX() + CONTENT_PADDING;
        float top = contentTop(scrollOffset) + yOffset;

        if (modules.isEmpty()) {
            FontRenderer font = clickGuiFont(12.0F);
            font.drawString("No modules", contentX, top, TEXT_DIM);
            return;
        }

        for (ModuleColumnLayout column : buildColumnLayouts(category, scrollOffset, yOffset)) {
            renderModuleColumn(column, mouseX, mouseY);
        }
    }

    private void renderModuleColumn(ModuleColumnLayout column, int mouseX, int mouseY) {
        FontRenderer headerFont = clickGuiFont(9.0F);
        headerFont.drawString(column.title(), column.x(), column.y(), TEXT_DIM);

        float cardY = column.y() + GROUP_HEADER_HEIGHT;
        Render2DUtility.drawRoundedRect(column.x(), cardY, column.width(), column.cardHeight(), 8.0F, CARD_BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(column.x(), cardY, column.width(), column.cardHeight(), 8.0F, 1.0F, BORDER_SOFT);

        float rowY = cardY + GROUP_PADDING;
        List<Module> modules = column.modules();
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float rowHeight = moduleRowHeight(module);
            renderModuleRow(module, column.x() + GROUP_PADDING, rowY, column.width() - GROUP_PADDING * 2.0F, rowHeight, mouseX, mouseY);
            rowY += rowHeight;
            if (i < modules.size() - 1) {
                Render2DUtility.drawRect(column.x() + 12.0F, rowY, column.width() - 24.0F, 1.0F, DIVIDER);
            }
        }
    }

    private void renderModuleRow(Module module, float x, float y, float width, float totalHeight, int mouseX, int mouseY) {
        boolean hovered = isInsideExclusive(mouseX, mouseY, x, y, width, MODULE_ROW_HEIGHT);
        ModuleAnimationState animation = moduleAnimation(module);
        animation.hoverProgress = animateExp(animation.hoverProgress, hovered ? 1.0F : 0.0F, MODULE_HOVER_ANIMATION_SPEED, animationFrameSeconds);
        float hoverProgress = animation.hoverProgress;
        float enabledProgress = animation.enabledProgress;
        float expandProgress = animation.expandProgress;
        boolean expandable = canExpandModule(module);

        if (hoverProgress > 0.0F) {
            Render2DUtility.drawRoundedRect(x, y + 2.0F, width, MODULE_ROW_HEIGHT - 4.0F, 6.0F,
                Render2DUtility.applyOpacity(HOVER, hoverProgress));
        }

        if (enabledProgress > 0.0F) {
            Render2DUtility.drawRect(x + 2.0F, y + 10.0F, 2.0F, MODULE_ROW_HEIGHT - 20.0F,
                Render2DUtility.applyOpacity(ACCENT, enabledProgress));
        }

        FontRenderer nameFont = clickGuiFont(12.0F);
        FontRenderer descriptionFont = clickGuiFont(10.0F);
        float toggleWidth = 32.0F;
        float toggleHeight = 16.0F;
        float arrowAreaWidth = expandable ? 18.0F : 0.0F;
        float arrowX = x + width - arrowAreaWidth - 8.0F;
        float toggleX = expandable ? arrowX - toggleWidth - 12.0F : x + width - toggleWidth - 8.0F;
        float textX = x + 12.0F;
        float textWidth = Math.max(20.0F, toggleX - textX - 12.0F);

        int nameColor = Render2DUtility.mix(TEXT_MUTED, TEXT, Math.max(enabledProgress, hoverProgress * 0.65F));
        nameFont.drawString(trimToWidth(nameFont, module.getName(), textWidth), textX, y + 9.0F, nameColor);
        descriptionFont.drawString(trimToWidth(descriptionFont, module.getDescription(), textWidth), textX, y + 29.0F,
            Render2DUtility.mix(TEXT_DIM, TEXT_SUBTLE, hoverProgress * 0.45F));

        renderAnimatedToggle(toggleX, y + (MODULE_ROW_HEIGHT - toggleHeight) * 0.5F, toggleWidth, toggleHeight,
            enabledProgress, hoverProgress);
        if (expandable) {
            renderChevron(arrowX + arrowAreaWidth * 0.5F, y + MODULE_ROW_HEIGHT * 0.5F, expandProgress, hoverProgress);
        }

        float expandedHeight = totalHeight - MODULE_ROW_HEIGHT;
        if (expandedHeight > 0.5F) {
            Render2DUtility.drawRect(x + 12.0F, y + MODULE_ROW_HEIGHT, width - 24.0F, 1.0F,
                Render2DUtility.applyOpacity(DIVIDER, expandProgress));
            Render2DUtility.withClip(x, y + MODULE_ROW_HEIGHT, width, expandedHeight, () -> {
                renderExpandedModule(module, x + 12.0F, y + MODULE_ROW_HEIGHT + EXPANDED_TOP_PADDING, width - 24.0F,
                    Math.max(0.0F, expandedHeight - EXPANDED_TOP_PADDING - EXPANDED_BOTTOM_PADDING), mouseX, mouseY);
            });
        }
    }

    private void renderAnimatedToggle(float x, float y, float width, float height, float enabledProgress, float hoverProgress) {
        int fillColor = Render2DUtility.mix(TOGGLE_OFF, ACCENT, enabledProgress);
        if (hoverProgress > 0.0F) {
            fillColor = Render2DUtility.mix(fillColor, CONTROL_HOVER, hoverProgress * (1.0F - enabledProgress) * 0.45F);
        }

        Render2DUtility.drawPill(x, y, width, height, fillColor, 0);
        float padding = Math.max(2.0F, height * 0.125F);
        float knobRadius = Math.max(1.0F, (height - padding * 2.0F) * 0.5F);
        float knobX = lerp(x + padding + knobRadius, x + width - padding - knobRadius, enabledProgress);
        float knobY = y + height * 0.5F;
        Render2DUtility.drawCircle(knobX, knobY, knobRadius + hoverProgress * 0.5F, TEXT);
    }

    private void renderChevron(float centerX, float centerY, float expandProgress, float hoverProgress) {
        FontRenderer font = clickGuiFont(14.0F);
        float textWidth = font.getStringWidth(">");
        float textHeight = font.getLineHeight();
        int color = Render2DUtility.mix(TEXT_SUBTLE, ACCENT, Math.max(expandProgress, hoverProgress * 0.35F));
        Render2DUtility.withRotation(lerp(0.0F, 90.0F, expandProgress), centerX, centerY, () -> {
            font.drawString(">", centerX - textWidth * 0.5F, centerY - textHeight * 0.5F, color);
        });
    }

    private void renderExpandedModule(Module module, float x, float y, float width, float height, int mouseX, int mouseY) {
        List<AbstractValue<?>> values = visibleValues(module);

        float rowY = y;
        for (AbstractValue<?> value : values) {
            AbstractComponent component = componentFor(value);
            component.render(x, rowY, width, mouseX, mouseY, 0.0F);
            rowY += component.getHeight();
            if (rowY - y > height) {
                break;
            }
        }
    }

    private void renderAvatar(float x, float y, float size) {
        if (avatarImage != null) {
            ensureAvatarTexture();
        }

        if (avatarTexture != null) {
            Render2DUtility.drawTexture(avatarTexture.getTextureView(), x, y, size, size);
            Render2DUtility.drawOutlineCircle(x + size * 0.5F, y + size * 0.5F, size * 0.5F, 1.0F, BORDER);
            return;
        }

        Render2DUtility.drawCircle(x + size * 0.5F, y + size * 0.5F, size * 0.5F, CONTROL_HOVER);
        Render2DUtility.drawOutlineCircle(x + size * 0.5F, y + size * 0.5F, size * 0.5F, 1.0F, BORDER);
        FontRenderer font = clickGuiFont(14.0F);
        String initial = userName == null || userName.isBlank() ? "U" : userName.substring(0, 1).toUpperCase(Locale.ROOT);
        font.drawCenteredString(initial, x + size * 0.5F, y + centeredTextY(size, font), TEXT_DIM);
    }

    private void ensureAvatarTexture() {
        if (avatarTexture != null || avatarImage == null) {
            return;
        }

        avatarTexture = new DynamicTexture(() -> "nyx-clickgui-avatar-" + AVATAR_TEXTURE_IDS.incrementAndGet(), toNativeImage(avatarImage));
    }

    private void closeAvatarTexture() {
        if (avatarTexture != null) {
            avatarTexture.close();
            avatarTexture = null;
        }
    }

    @Nullable
    private static BufferedImage loadAvatarImage() {
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
            graphics.setColor(Color.WHITE);
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

    private ModuleRowLayout getModuleRowAt(double mouseX, double mouseY) {
        if (!isInsideExclusive(mouseX, mouseY, mainX(), panelY + HEADER_HEIGHT, mainWidth(), panelHeight - HEADER_HEIGHT)) {
            return null;
        }

        for (ModuleRowLayout row : buildModuleRowLayouts(selectedCategory, scroll, incomingCategoryContentOffset())) {
            if (isInsideExclusive(mouseX, mouseY, row.x(), row.y(), row.width(), MODULE_ROW_HEIGHT)) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    private AbstractComponent getComponentAt(double mouseX, double mouseY) {
        if (!isInsideExclusive(mouseX, mouseY, mainX(), panelY + HEADER_HEIGHT, mainWidth(), panelHeight - HEADER_HEIGHT)) {
            return null;
        }

        for (ValueComponentLayout layout : buildValueComponentLayouts(selectedCategory, scroll, incomingCategoryContentOffset())) {
            if (isInsideExclusive(mouseX, mouseY, layout.x(), layout.y(), layout.width(), layout.height())) {
                layout.component().setBounds(layout.x(), layout.y(), layout.width());
                return layout.component();
            }
        }
        return null;
    }

    @Nullable
    private Category getCategoryAt(double mouseX, double mouseY) {
        float pad = sidebarPadding();
        float navY = panelY + LOGO_AREA_HEIGHT + 45.0F;
        for (Category category : Category.values()) {
            if (ModuleManager.getModules(category).isEmpty()) {
                continue;
            }
            if (isInsideExclusive(mouseX, mouseY, panelX, navY, sidebarWidth, NAV_ITEM_HEIGHT)) {
                return category;
            }
            navY += NAV_ITEM_HEIGHT + 4.0F;
        }
        return null;
    }

    private List<ModuleRowLayout> buildModuleRowLayouts(float scrollOffset) {
        return buildModuleRowLayouts(selectedCategory, scrollOffset, 0.0F);
    }

    private List<ModuleRowLayout> buildModuleRowLayouts(Category category, float scrollOffset, float yOffset) {
        List<ModuleRowLayout> rows = new ArrayList<>();
        for (ModuleColumnLayout column : buildColumnLayouts(category, scrollOffset, yOffset)) {
            float rowY = column.y() + GROUP_HEADER_HEIGHT + GROUP_PADDING;
            for (Module module : column.modules()) {
                float rowHeight = moduleRowHeight(module);
                rows.add(new ModuleRowLayout(module, column.x() + GROUP_PADDING, rowY, column.width() - GROUP_PADDING * 2.0F, rowHeight));
                rowY += rowHeight;
            }
        }
        return rows;
    }

    private List<ValueComponentLayout> buildValueComponentLayouts(Category category, float scrollOffset, float yOffset) {
        List<ValueComponentLayout> layouts = new ArrayList<>();
        for (ModuleRowLayout row : buildModuleRowLayouts(category, scrollOffset, yOffset)) {
            if (!isExpanded(row.module()) || !canExpandModule(row.module()) || moduleExpandAmount(row.module()) <= 0.05F) {
                continue;
            }

            float componentX = row.x() + 12.0F;
            float componentY = row.y() + MODULE_ROW_HEIGHT + EXPANDED_TOP_PADDING;
            float componentWidth = row.width() - 24.0F;
            float remainingHeight = row.height() - MODULE_ROW_HEIGHT - EXPANDED_TOP_PADDING - EXPANDED_BOTTOM_PADDING;
            for (AbstractValue<?> value : visibleValues(row.module())) {
                if (remainingHeight <= 0.0F) {
                    break;
                }

                AbstractComponent component = componentFor(value);
                float componentHeight = component.getHeight();
                layouts.add(new ValueComponentLayout(component, componentX, componentY, componentWidth, Math.min(componentHeight, remainingHeight)));
                componentY += componentHeight;
                remainingHeight -= componentHeight;
            }
        }
        return layouts;
    }

    private List<ModuleColumnLayout> buildColumnLayouts(float scrollOffset) {
        return buildColumnLayouts(selectedCategory, scrollOffset, 0.0F);
    }

    private List<ModuleColumnLayout> buildColumnLayouts(Category category, float scrollOffset, float yOffset) {
        List<Module> modules = modulesForCategory(category);
        List<ModuleColumnLayout> layouts = new ArrayList<>();
        if (modules.isEmpty()) {
            return layouts;
        }

        float contentX = mainX() + CONTENT_PADDING;
        float contentWidth = mainWidth() - CONTENT_PADDING * 2.0F;
        int columns = contentWidth >= 480.0F ? 2 : 1;
        float gap = columns == 1 ? COLUMN_GAP_COMPACT : COLUMN_GAP;
        float columnWidth = (contentWidth - gap * (columns - 1)) / columns;
        List<List<Module>> columnModules = new ArrayList<>();
        float[] columnHeights = new float[columns];
        for (int i = 0; i < columns; i++) {
            columnModules.add(new ArrayList<>());
        }

        for (Module module : modules) {
            int column = shortestColumn(columnHeights);
            columnModules.get(column).add(module);
            columnHeights[column] += moduleRowHeight(module);
        }

        float top = contentTop(scrollOffset) + yOffset;
        for (int i = 0; i < columns; i++) {
            List<Module> columnModuleList = columnModules.get(i);
            if (columnModuleList.isEmpty()) {
                continue;
            }

            String title = columns == 1 ? categoryLabel(category) : i == 0 ? "Right" : "Left";
            float cardHeight = GROUP_PADDING * 2.0F + columnHeights[i];
            layouts.add(new ModuleColumnLayout(
                title,
                columnModuleList,
                contentX + i * (columnWidth + gap),
                top,
                columnWidth,
                cardHeight
            ));
        }
        return layouts;
    }

    private void updateScrollLimit() {
        float contentBottom = CONTENT_TOP_GAP + BOTTOM_PADDING;
        for (ModuleColumnLayout layout : buildColumnLayouts(0.0F)) {
            float bottom = layout.y() + GROUP_HEADER_HEIGHT + layout.cardHeight() - (panelY + HEADER_HEIGHT);
            contentBottom = Math.max(contentBottom, bottom + BOTTOM_PADDING);
        }
        if (modulesForSelectedCategory().isEmpty()) {
            contentBottom = 96.0F;
        }

        maxScroll = Math.max(0.0F, contentBottom - (panelHeight - HEADER_HEIGHT));
        scroll = clamp(scroll, 0.0F, maxScroll);
    }

    private float moduleRowHeight(Module module) {
        List<AbstractValue<?>> values = visibleValues(module);
        if (values.isEmpty()) {
            return MODULE_ROW_HEIGHT;
        }

        return MODULE_ROW_HEIGHT + valuesHeight(values) * moduleExpandAmount(module);
    }

    private float valuesHeight(List<AbstractValue<?>> values) {
        float height = EXPANDED_TOP_PADDING + EXPANDED_BOTTOM_PADDING;
        for (AbstractValue<?> value : values) {
            height += componentFor(value).getHeight();
        }
        return height;
    }

    private List<AbstractValue<?>> visibleValues(Module module) {
        List<AbstractValue<?>> values = new ArrayList<>();
        for (AbstractValue<?> value : module.getValues()) {
            if (value.isVisible()) {
                values.add(value);
            }
        }
        return values;
    }

    private boolean canExpandModule(Module module) {
        return !visibleValues(module).isEmpty();
    }

    private AbstractComponent componentFor(AbstractValue<?> value) {
        return valueComponents.computeIfAbsent(value, this::createComponent);
    }

    private static <T> void resetValue(AbstractValue<T> value) {
        value.setValue(value.getDefaultValue());
    }

    private AbstractComponent createComponent(AbstractValue<?> value) {
        if (value instanceof BoolValue boolValue) {
            return new BoolComponent(boolValue);
        }
        if (value instanceof ButtonValue buttonValue) {
            return new ButtonComponent(buttonValue);
        }
        if (value instanceof ColorValue colorValue) {
            return new ColorComponent(colorValue);
        }
        if (value instanceof DoubleValue doubleValue) {
            return new DoubleComponent(doubleValue);
        }
        if (value instanceof KeyBindValue keyBindValue) {
            return new SimpleValueComponent(keyBindValue);
        }
        if (value instanceof IntValue intValue) {
            return new IntComponent(intValue);
        }
        if (value instanceof StringValue stringValue) {
            return new StringComponent(stringValue);
        }
        if (value instanceof EnumValue<?> enumValue) {
            return new EnumComponent(enumValue);
        }
        return new SimpleValueComponent(value);
    }

    private void blurComponentsExcept(@Nullable AbstractComponent keepFocused) {
        if (capturedComponent != keepFocused) {
            capturedComponent = null;
        }

        for (AbstractComponent component : valueComponents.values()) {
            if (component != keepFocused) {
                component.blur();
            }
        }
    }

    private void setModuleExpanded(Module module, boolean expanded) {
        moduleAnimation(module);
        if (expanded && canExpandModule(module)) {
            expandedModules.put(module, true);
        } else {
            expandedModules.remove(module);
        }
    }

    private boolean isExpanded(Module module) {
        return expandedModules.getOrDefault(module, false);
    }

    private float moduleExpandAmount(Module module) {
        return moduleAnimation(module).expandProgress;
    }

    private ModuleAnimationState moduleAnimation(Module module) {
        return moduleAnimations.computeIfAbsent(module, ModuleAnimationState::new);
    }

    private void selectCategory(Category category) {
        if (category == selectedCategory) {
            if (previousCategory == null) {
                scroll = 0.0F;
                updateScrollLimit();
            }
            return;
        }

        Category oldCategory = selectedCategory;
        previousCategory = oldCategory;
        previousCategoryScroll = scroll;
        selectedCategory = category;
        scroll = 0.0F;
        categorySwitchDirection = categorySwitchDirection(oldCategory, selectedCategory);
        categorySwitchStartedAtNanos = System.nanoTime();
        categorySwitchProgress = 0.0F;
        updateScrollLimit();
    }

    private void updateAnimationFrame() {
        long now = System.nanoTime();
        if (lastAnimationFrameNanos == 0L) {
            animationFrameSeconds = DEFAULT_FRAME_SECONDS;
        } else {
            animationFrameSeconds = clamp((now - lastAnimationFrameNanos) / 1_000_000_000.0F, 0.0F, MAX_FRAME_SECONDS);
        }
        lastAnimationFrameNanos = now;
    }

    private void updateScreenTransitionAnimation() {
        if (screenTransitionStartedAtNanos == 0L) {
            screenTransitionProgress = 1.0F;
            return;
        }

        float rawProgress = (float)((System.nanoTime() - screenTransitionStartedAtNanos) / (double)SCREEN_TRANSITION_ANIMATION_NANOS);
        if (rawProgress >= 1.0F) {
            screenTransitionProgress = 1.0F;
            if (closing) {
                closingCompleted = true;
            }
            return;
        }

        screenTransitionProgress = easeOutCubic(clamp(rawProgress, 0.0F, 1.0F));
    }

    private boolean finishClosingIfNeeded() {
        if (!closingCompleted) {
            return false;
        }

        closingCompleted = false;
        if (this.minecraft != null && this.minecraft.screen == this) {
            this.minecraft.setScreen(null);
        }
        return true;
    }

    private void beginCloseAnimation() {
        if (closing) {
            return;
        }

        closing = true;
        closingCompleted = false;
        screenTransitionProgress = 0.0F;
        screenTransitionStartedAtNanos = System.nanoTime();
        lastAnimationFrameNanos = 0L;
        draggingPanel = false;
        capturedComponent = null;
        blurComponentsExcept(null);
    }

    private boolean isInteractive() {
        return !closing && screenTransitionProgress >= 1.0F;
    }

    private float screenTransitionVisibility() {
        return closing ? 1.0F - screenTransitionProgress : screenTransitionProgress;
    }

    private float screenTransitionScale() {
        return lerp(SCREEN_TRANSITION_MIN_SCALE, 1.0F, screenTransitionVisibility());
    }

    private int screenDimColor() {
        return Render2DUtility.applyOpacity(SCREEN_DIM, screenTransitionVisibility());
    }

    private void updateCategorySwitchAnimation() {
        if (previousCategory == null) {
            categorySwitchProgress = 1.0F;
            return;
        }

        float rawProgress = (float)((System.nanoTime() - categorySwitchStartedAtNanos) / (double)CATEGORY_SWITCH_ANIMATION_NANOS);
        if (rawProgress >= 1.0F) {
            finishCategorySwitchAnimation();
            return;
        }

        categorySwitchProgress = easeOutCubic(clamp(rawProgress, 0.0F, 1.0F));
    }

    private void updateModuleAnimations() {
        for (Module module : ModuleManager.getModules()) {
            ModuleAnimationState animation = moduleAnimation(module);
            boolean expandable = canExpandModule(module);
            if (!expandable) {
                expandedModules.remove(module);
            }
            animation.expandProgress = animateExp(animation.expandProgress, expandable && isExpanded(module) ? 1.0F : 0.0F, MODULE_EXPAND_ANIMATION_SPEED, animationFrameSeconds);
            animation.enabledProgress = animateExp(animation.enabledProgress, module.isEnabled() ? 1.0F : 0.0F, MODULE_TOGGLE_ANIMATION_SPEED, animationFrameSeconds);
        }
    }

    private void finishCategorySwitchAnimation() {
        previousCategory = null;
        previousCategoryScroll = 0.0F;
        categorySwitchProgress = 1.0F;
    }

    private void cancelCategorySwitchAnimation() {
        previousCategory = null;
        previousCategoryScroll = 0.0F;
        categorySwitchProgress = 1.0F;
        categorySwitchDirection = 1;
    }

    private float incomingCategoryContentOffset() {
        if (previousCategory == null) {
            return 0.0F;
        }

        return categorySwitchDirection * categorySwitchDistance() * (1.0F - categorySwitchProgress);
    }

    private float outgoingCategoryContentOffset() {
        if (previousCategory == null) {
            return 0.0F;
        }

        return -categorySwitchDirection * categorySwitchDistance() * categorySwitchProgress;
    }

    private float categorySwitchDistance() {
        return Math.max(1.0F, panelHeight - HEADER_HEIGHT);
    }

    private float categoryActiveAmount(Category category) {
        if (previousCategory == null) {
            return selectedCategory == category ? 1.0F : 0.0F;
        }

        if (category == selectedCategory) {
            return categorySwitchProgress;
        }
        if (category == previousCategory) {
            return 1.0F - categorySwitchProgress;
        }
        return 0.0F;
    }

    private List<Module> modulesForSelectedCategory() {
        return modulesForCategory(selectedCategory);
    }

    private List<Module> modulesForCategory(Category category) {
        return ModuleManager.getModules(category);
    }

    private void ensureVisibleCategory() {
        if (!ModuleManager.getModules(selectedCategory).isEmpty()) {
            return;
        }

        for (Category category : Category.values()) {
            if (!ModuleManager.getModules(category).isEmpty()) {
                selectedCategory = category;
                scroll = 0.0F;
                cancelCategorySwitchAnimation();
                return;
            }
        }
    }

    private void updatePanelMetrics() {
        panelWidth = PANEL_WIDTH;
        panelHeight = PANEL_HEIGHT;

        if (!panelPositionInitialized) {
            panelX = (fixedScreenWidth() - panelWidth) * 0.5F;
            panelY = (fixedScreenHeight() - panelHeight) * 0.5F;
            panelPositionInitialized = true;
        }

        clampPanelToScreen();

        if (panelWidth < 520.0F) {
            sidebarWidth = 138.0F;
        } else if (panelWidth < 760.0F) {
            sidebarWidth = SIDEBAR_WIDTH_COMPACT;
        } else {
            sidebarWidth = SIDEBAR_WIDTH;
        }
    }

    private void clampPanelToScreen() {
        panelX = clampPanelAxis(panelX, panelWidth, fixedScreenWidth());
        panelY = clampPanelAxis(panelY, panelHeight, fixedScreenHeight());
    }

    private boolean isInsideDragArea(double mouseX, double mouseY) {
        return isInsideExclusive(mouseX, mouseY, panelX, panelY, panelWidth, HEADER_HEIGHT);
    }

    private float fixedMouseX(double mouseX) {
        return (float)(mouseX * coordinateScale());
    }

    private float fixedMouseY(double mouseY) {
        return (float)(mouseY * coordinateScale());
    }

    private float fixedScreenWidth() {
        return this.width * coordinateScale();
    }

    private float fixedScreenHeight() {
        return this.height * coordinateScale();
    }

    private float coordinateScale() {
        return guiScale() / UI_SCALE;
    }

    private int guiScale() {
        return this.minecraft == null ? 1 : Math.max(1, this.minecraft.getWindow().getGuiScale());
    }

    private boolean isLeftShiftDown() {
        return this.minecraft != null && this.minecraft.getWindow() != null
            && glfwGetKey(this.minecraft.getWindow().handle(), GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS;
    }

    private float sidebarPadding() {
        return sidebarWidth < 175.0F ? 14.0F : 24.0F;
    }

    private float mainX() {
        return panelX + sidebarWidth;
    }

    private float mainWidth() {
        return panelWidth - sidebarWidth;
    }

    private float panelCenterX() {
        return panelX + panelWidth * 0.5F;
    }

    private float panelCenterY() {
        return panelY + panelHeight * 0.5F;
    }

    private float contentTop(float scrollOffset) {
        return panelY + HEADER_HEIGHT + CONTENT_TOP_GAP - scrollOffset;
    }

    private float categoryNavY(Category target) {
        float navY = panelY + LOGO_AREA_HEIGHT + 45.0F;
        for (Category category : Category.values()) {
            if (ModuleManager.getModules(category).isEmpty()) {
                continue;
            }
            if (category == target) {
                return navY;
            }
            navY += NAV_ITEM_HEIGHT + 4.0F;
        }
        return Float.NaN;
    }

    private int categorySwitchDirection(Category from, Category to) {
        int direction = Integer.compare(visibleCategoryIndex(to), visibleCategoryIndex(from));
        return direction == 0 ? 1 : direction;
    }

    private int visibleCategoryIndex(Category target) {
        int index = 0;
        for (Category category : Category.values()) {
            if (ModuleManager.getModules(category).isEmpty()) {
                continue;
            }
            if (category == target) {
                return index;
            }
            index++;
        }
        return target.ordinal();
    }

    private static void drawOutlinedString(FontRenderer font, String text, float x, float y, int color, int outlineColor) {
        font.drawString(text, x - 1.0F, y, outlineColor);
        font.drawString(text, x + 1.0F, y, outlineColor);
        font.drawString(text, x, y - 1.0F, outlineColor);
        font.drawString(text, x, y + 1.0F, outlineColor);
        font.drawString(text, x, y, color);
    }

    private String selectedCategoryLabel() {
        return categoryLabel(selectedCategory);
    }

    private FontRenderer clickGuiFont(float size) {
        return FontManager.getClickGuiRenderer(size);
    }

    private static int shortestColumn(float[] heights) {
        int column = 0;
        for (int i = 1; i < heights.length; i++) {
            if (heights[i] < heights[column]) {
                column = i;
            }
        }
        return column;
    }

    private static String categoryLabel(Category category) {
        String value = category.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String trimToWidth(FontRenderer renderer, String text, float maxWidth) {
        if (text == null || text.isEmpty()) {
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
        return text.substring(0, end) + suffix;
    }

    private static float centeredTextY(float height, FontRenderer renderer) {
        return (height - renderer.getLineHeight()) * 0.5F;
    }

    private static float clampPanelAxis(float position, float panelSize, float screenSize) {
        if (screenSize <= panelSize + MIN_MARGIN * 2.0F) {
            return (screenSize - panelSize) * 0.5F;
        }

        return clamp(position, MIN_MARGIN, screenSize - panelSize - MIN_MARGIN);
    }

    private static final class ModuleAnimationState {
        private float expandProgress;
        private float enabledProgress;
        private float hoverProgress;

        private ModuleAnimationState(Module module) {
            this.enabledProgress = module.isEnabled() ? 1.0F : 0.0F;
        }
    }

    private record ModuleColumnLayout(String title, List<Module> modules, float x, float y, float width, float cardHeight) {
    }

    private record ModuleRowLayout(Module module, float x, float y, float width, float height) {
    }

    private record ValueComponentLayout(AbstractComponent component, float x, float y, float width, float height) {
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {

    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

    }
}
