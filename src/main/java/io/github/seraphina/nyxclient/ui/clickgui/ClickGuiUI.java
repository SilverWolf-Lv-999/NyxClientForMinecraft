package io.github.seraphina.nyxclient.ui.clickgui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.seraphina.nyxclient.manager.FontManager;
import io.github.seraphina.nyxclient.manager.ModuleManager;
import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.ui.clickgui.component.*;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.utility.font.FontRenderer;
import io.github.seraphina.nyxclient.utility.web.MicrosoftUtility;
import io.github.seraphina.nyxclient.value.AbstractValue;
import io.github.seraphina.nyxclient.value.impl.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;

import javax.annotation.Nullable;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class ClickGuiUI extends Screen {
    private static final float PANEL_WIDTH = 820.0F;
    private static final float PANEL_HEIGHT = 540.0F;
    private static final float UI_SCALE = 1.4F;
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
    private static final float EMPTY_EXPANDED_HEIGHT = 34.0F;
    private static final float BOTTOM_PADDING = 28.0F;
    private static final float SCROLL_STEP = 34.0F;
    private static final float AVATAR_TEXTURE_SIZE = 64.0F;
    private static final long CATEGORY_SWITCH_ANIMATION_NANOS = 260_000_000L;

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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        Render2DUtility.withGuiGraphics(guiGraphics, () -> {
            updatePanelMetrics();
            ensureVisibleCategory();
            updateCategorySwitchAnimation();
            updateScrollLimit();

            float scale = coordinateScale();
            int fixedMouseX = Math.round(mouseX * scale);
            int fixedMouseY = Math.round(mouseY * scale);
            Render2DUtility.withScale(1.0F / scale, 1.0F / scale, 0.0F, 0.0F, () -> {
                Render2DUtility.drawRect(0.0F, 0.0F, fixedScreenWidth(), fixedScreenHeight(), SCREEN_DIM);
                renderPanel(fixedMouseX, fixedMouseY);
            });
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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
            expandedModules.put(row.module(), !isExpanded(row.module()));
            updateScrollLimit();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
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

        if (maxScroll <= 0.0F || !isInside(fixedMouseX, fixedMouseY, mainX(), panelY + HEADER_HEIGHT, mainWidth(), panelHeight - HEADER_HEIGHT)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        scroll = clamp(scroll - (float)scrollY * SCROLL_STEP, 0.0F, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        for (AbstractComponent component : valueComponents.values()) {
            if (component.keyPressed(event)) {
                updateScrollLimit();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
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
        super.removed();
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
        float logoX = x;
        float logoY = y + 16.0F;
        Render2DUtility.drawRoundedLinearGradientRect(
            logoX,
            logoY,
            32.0F,
            32.0F,
            7.0F,
            logoX,
            logoY,
            logoX + 32.0F,
            logoY + 32.0F,
            new int[] {0xFF4489FF, ACCENT_DARK},
            null
        );

        FontRenderer logoFont = clickGuiFont(15.0F);
        logoFont.drawCenteredString("N", logoX + 16.0F, logoY + centeredTextY(32.0F, logoFont), TEXT);

        if (sidebarWidth >= 175.0F) {
            FontRenderer titleFont = clickGuiFont(16.0F);
            FontRenderer subFont = clickGuiFont(9.0F);
            titleFont.drawString("Nyx", logoX + 44.0F, logoY + 2.0F, TEXT);
            subFont.drawString("Minecraft", logoX + 44.0F, logoY + 21.0F, TEXT_DIM);
        }
    }

    private void renderCategoryNavItem(Category category, float sidebarX, float y, float width, float pad, int mouseX, int mouseY) {
        boolean hovered = isInside(mouseX, mouseY, sidebarX, y, width, NAV_ITEM_HEIGHT);
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
        boolean hovered = isInside(mouseX, mouseY, x, y, width, MODULE_ROW_HEIGHT);
        boolean enabled = module.isEnabled();
        boolean expanded = isExpanded(module);

        if (hovered) {
            Render2DUtility.drawRoundedRect(x, y + 2.0F, width, MODULE_ROW_HEIGHT - 4.0F, 6.0F, HOVER);
        }

        if (enabled) {
            Render2DUtility.drawRect(x + 2.0F, y + 10.0F, 2.0F, MODULE_ROW_HEIGHT - 20.0F, ACCENT);
        }

        FontRenderer nameFont = clickGuiFont(12.0F);
        FontRenderer descriptionFont = clickGuiFont(10.0F);
        float arrowAreaWidth = 18.0F;
        float toggleWidth = 32.0F;
        float toggleHeight = 16.0F;
        float arrowX = x + width - arrowAreaWidth - 8.0F;
        float toggleX = arrowX - toggleWidth - 12.0F;
        float textX = x + 12.0F;
        float textWidth = Math.max(20.0F, toggleX - textX - 12.0F);

        nameFont.drawString(trimToWidth(nameFont, module.getName(), textWidth), textX, y + 9.0F, enabled ? TEXT : TEXT_MUTED);
        descriptionFont.drawString(trimToWidth(descriptionFont, module.getDescription(), textWidth), textX, y + 29.0F, TEXT_DIM);

        Render2DUtility.drawToggleSwitch(
            toggleX,
            y + (MODULE_ROW_HEIGHT - toggleHeight) * 0.5F,
            toggleWidth,
            toggleHeight,
            enabled,
            ACCENT,
            TOGGLE_OFF,
            TEXT
        );
        renderChevron(arrowX + arrowAreaWidth * 0.5F, y + MODULE_ROW_HEIGHT * 0.5F, expanded);

        if (expanded) {
            Render2DUtility.drawRect(x + 12.0F, y + MODULE_ROW_HEIGHT, width - 24.0F, 1.0F, DIVIDER);
            renderExpandedModule(module, x + 12.0F, y + MODULE_ROW_HEIGHT + EXPANDED_TOP_PADDING, width - 24.0F,
                totalHeight - MODULE_ROW_HEIGHT - EXPANDED_TOP_PADDING - EXPANDED_BOTTOM_PADDING, mouseX, mouseY);
        }
    }

    private void renderChevron(float centerX, float centerY, boolean expanded) {
        FontRenderer font = clickGuiFont(14.0F);
        float textWidth = font.getStringWidth(">");
        float textHeight = font.getLineHeight();
        int color = expanded ? ACCENT : TEXT_SUBTLE;
        Render2DUtility.withRotation(expanded ? 90.0F : 0.0F, centerX, centerY, () -> {
            font.drawString(">", centerX - textWidth * 0.5F, centerY - textHeight * 0.5F, color);
        });
    }

    private void renderExpandedModule(Module module, float x, float y, float width, float height, int mouseX, int mouseY) {
        List<AbstractValue<?>> values = visibleValues(module);
        FontRenderer smallFont = clickGuiFont(10.0F);

        if (values.isEmpty()) {
            smallFont.drawString("No settings", x, y + 6.0F, TEXT_DIM);
            return;
        }

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
            GuiGraphics graphics = Render2DUtility.currentGuiGraphics();
            graphics.submitGuiElementRenderState(new AvatarTextureRenderState(
                TextureSetup.singleTexture(avatarTexture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                new Matrix3x2f(graphics.pose()),
                x,
                y,
                x + size,
                y + size,
                graphics.peekScissorStack()
            ));
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
                .map(image -> circularImage(image, Math.round(AVATAR_TEXTURE_SIZE)))
                .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static BufferedImage circularImage(BufferedImage source, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, size, size);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setClip(new Ellipse2D.Float(0.0F, 0.0F, size, size));

            int sourceSize = Math.min(source.getWidth(), source.getHeight());
            int sourceX = (source.getWidth() - sourceSize) / 2;
            int sourceY = (source.getHeight() - sourceSize) / 2;
            graphics.drawImage(source, 0, 0, size, size, sourceX, sourceY, sourceX + sourceSize, sourceY + sourceSize, null);
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

    private ModuleRowLayout getModuleRowAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY, mainX(), panelY + HEADER_HEIGHT, mainWidth(), panelHeight - HEADER_HEIGHT)) {
            return null;
        }

        for (ModuleRowLayout row : buildModuleRowLayouts(selectedCategory, scroll, incomingCategoryContentOffset())) {
            if (isInside(mouseX, mouseY, row.x(), row.y(), row.width(), MODULE_ROW_HEIGHT)) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    private AbstractComponent getComponentAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY, mainX(), panelY + HEADER_HEIGHT, mainWidth(), panelHeight - HEADER_HEIGHT)) {
            return null;
        }

        for (ValueComponentLayout layout : buildValueComponentLayouts(selectedCategory, scroll, incomingCategoryContentOffset())) {
            if (isInside(mouseX, mouseY, layout.x(), layout.y(), layout.width(), layout.height())) {
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
            if (isInside(mouseX, mouseY, panelX, navY, sidebarWidth, NAV_ITEM_HEIGHT)) {
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
            if (!isExpanded(row.module())) {
                continue;
            }

            float componentX = row.x() + 12.0F;
            float componentY = row.y() + MODULE_ROW_HEIGHT + EXPANDED_TOP_PADDING;
            float componentWidth = row.width() - 24.0F;
            for (AbstractValue<?> value : visibleValues(row.module())) {
                AbstractComponent component = componentFor(value);
                float componentHeight = component.getHeight();
                layouts.add(new ValueComponentLayout(component, componentX, componentY, componentWidth, componentHeight));
                componentY += componentHeight;
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

            String title = columns == 1 ? categoryLabel(category) : i == 0 ? "Main" : "Other";
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
        if (!isExpanded(module)) {
            return MODULE_ROW_HEIGHT;
        }

        List<AbstractValue<?>> values = visibleValues(module);
        return MODULE_ROW_HEIGHT + (values.isEmpty() ? EMPTY_EXPANDED_HEIGHT : valuesHeight(values));
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

    private AbstractComponent componentFor(AbstractValue<?> value) {
        return valueComponents.computeIfAbsent(value, this::createComponent);
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

    private boolean isExpanded(Module module) {
        return expandedModules.getOrDefault(module, false);
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
        return isInside(mouseX, mouseY, panelX, panelY, panelWidth, HEADER_HEIGHT);
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

    private float sidebarPadding() {
        return sidebarWidth < 175.0F ? 14.0F : 24.0F;
    }

    private float mainX() {
        return panelX + sidebarWidth;
    }

    private float mainWidth() {
        return panelWidth - sidebarWidth;
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

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * clamp(progress, 0.0F, 1.0F);
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

    private static boolean isInside(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampPanelAxis(float position, float panelSize, float screenSize) {
        if (screenSize <= panelSize + MIN_MARGIN * 2.0F) {
            return (screenSize - panelSize) * 0.5F;
        }

        return clamp(position, MIN_MARGIN, screenSize - panelSize - MIN_MARGIN);
    }

    @Nullable
    private static ScreenRectangle boundsFor(float x0, float y0, float x1, float y1, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        float minX = Math.min(x0, x1);
        float maxX = Math.max(x0, x1);
        float minY = Math.min(y0, y1);
        float maxY = Math.max(y0, y1);
        ScreenRectangle bounds = new ScreenRectangle(
            (int)Math.floor(minX),
            (int)Math.floor(minY),
            Math.max(1, (int)Math.ceil(maxX - minX)),
            Math.max(1, (int)Math.ceil(maxY - minY))
        ).transformMaxBounds(pose);
        return scissorArea == null ? bounds : scissorArea.intersection(bounds);
    }

    private record ModuleColumnLayout(String title, List<Module> modules, float x, float y, float width, float cardHeight) {
    }

    private record ModuleRowLayout(Module module, float x, float y, float width, float height) {
    }

    private record ValueComponentLayout(AbstractComponent component, float x, float y, float width, float height) {
    }

    private record AvatarTextureRenderState(
        TextureSetup textureSetup,
        Matrix3x2f pose,
        float x0,
        float y0,
        float x1,
        float y1,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {
        private AvatarTextureRenderState(TextureSetup textureSetup, Matrix3x2f pose, float x0, float y0, float x1, float y1,
                                         @Nullable ScreenRectangle scissorArea) {
            this(textureSetup, pose, x0, y0, x1, y1, scissorArea, boundsFor(x0, y0, x1, y1, pose, scissorArea));
        }

        @Override
        public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return RenderPipelines.GUI_TEXTURED;
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            consumer.addVertexWith2DPose(pose, x0, y0).setUv(0.0F, 0.0F).setColor(0xFFFFFFFF);
            consumer.addVertexWith2DPose(pose, x0, y1).setUv(0.0F, 1.0F).setColor(0xFFFFFFFF);
            consumer.addVertexWith2DPose(pose, x1, y1).setUv(1.0F, 1.0F).setColor(0xFFFFFFFF);
            consumer.addVertexWith2DPose(pose, x1, y0).setUv(1.0F, 0.0F).setColor(0xFFFFFFFF);
        }
    }
}
