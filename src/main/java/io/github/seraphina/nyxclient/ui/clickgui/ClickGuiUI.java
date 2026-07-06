package io.github.seraphina.nyxclient.ui.clickgui;

import io.github.seraphina.nyxclient.manager.FontManager;
import io.github.seraphina.nyxclient.manager.ModuleManager;
import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.utility.font.FontRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class ClickGuiUI extends Screen {
    private static final float GLOBAL_NAV_HEIGHT = 44.0F;
    private static final float SUB_NAV_HEIGHT = 52.0F;
    private static final float CONTENT_TOP_GAP = 28.0F;
    private static final float CONTENT_MAX_WIDTH = 1120.0F;
    private static final float CONTENT_MARGIN = 24.0F;
    private static final float CONTENT_MARGIN_COMPACT = 16.0F;
    private static final float HERO_HEIGHT = 88.0F;
    private static final float HERO_HEIGHT_COMPACT = 112.0F;
    private static final float CARD_GAP = 20.0F;
    private static final float CARD_GAP_COMPACT = 12.0F;
    private static final float CARD_PADDING = 18.0F;
    private static final float CARD_RADIUS = 18.0F;
    private static final float CARD_HEADER_HEIGHT = 34.0F;
    private static final float MODULE_ROW_HEIGHT = 54.0F;
    private static final float MODULE_ROW_GAP = 8.0F;
    private static final float MODULE_ROW_RADIUS = 16.0F;
    private static final float BOTTOM_PADDING = 32.0F;
    private static final float SCROLL_STEP = 34.0F;

    private static final int ACTION_BLUE = 0xFF0066CC;
    private static final int ACTION_BLUE_ON_DARK = 0xFF2997FF;
    private static final int CANVAS = 0xFFFFFFFF;
    private static final int CANVAS_PARCHMENT = 0xFFF5F5F7;
    private static final int SURFACE_BLACK = 0xFF000000;
    private static final int INK = 0xFF1D1D1F;
    private static final int INK_MUTED = 0xFF7A7A7A;
    private static final int BODY_MUTED_ON_DARK = 0xFFCCCCCC;
    private static final int HAIRLINE = 0xFFE0E0E0;
    private static final int DIVIDER_SOFT = 0xFFF0F0F0;
    private static final int FROSTED_PARCHMENT = 0xEAF5F5F7;
    private static final int ROW_HOVER = 0xFFF5F5F7;
    private static final int ROW_ACTIVE = 0xFFFAFAFC;
    private static final int TOGGLE_DISABLED = 0xFFD2D2D7;

    private float scroll;
    private float maxScroll;

    public ClickGuiUI() {
        super(Component.empty());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        Render2DUtility.withGuiGraphics(guiGraphics, () -> {
            updateScrollLimit();
            Render2DUtility.drawRect(0.0F, 0.0F, this.width, this.height, CANVAS_PARCHMENT);
            Render2DUtility.withClip(0.0F, GLOBAL_NAV_HEIGHT + SUB_NAV_HEIGHT, this.width, this.height - GLOBAL_NAV_HEIGHT - SUB_NAV_HEIGHT, () -> {
                renderContent(mouseX, mouseY);
            });
            renderNavigation();
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        Module module = getModuleAt(event.x(), event.y());
        if (module == null) {
            return super.mouseClicked(event, doubleClick);
        }

        module.toggle();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll <= 0.0F) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        scroll = clamp(scroll - (float)scrollY * SCROLL_STEP, 0.0F, maxScroll);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderNavigation() {
        float contentX = contentX();
        float contentWidth = contentWidth();
        int enabledCount = enabledModuleCount();
        int moduleCount = ModuleManager.getModules().size();

        Render2DUtility.drawRect(0.0F, 0.0F, this.width, GLOBAL_NAV_HEIGHT, SURFACE_BLACK);
        FontRenderer navFont = clickGuiFont(12.0F);
        navFont.drawString("Nyx", contentX, centeredTextY(GLOBAL_NAV_HEIGHT, navFont), CANVAS);

        if (contentWidth > 520.0F) {
            float linkX = contentX + 70.0F;
            for (Category category : Category.values()) {
                List<Module> modules = ModuleManager.getModules(category);
                if (modules.isEmpty()) {
                    continue;
                }

                String label = categoryLabel(category);
                navFont.drawString(label, linkX, centeredTextY(GLOBAL_NAV_HEIGHT, navFont), BODY_MUTED_ON_DARK);
                linkX += navFont.getStringWidth(label) + 24.0F;
                if (linkX > contentX + contentWidth - 120.0F) {
                    break;
                }
            }
        }

        String countLabel = enabledCount + "/" + moduleCount + " On";
        float countWidth = navFont.getStringWidth(countLabel);
        navFont.drawString(countLabel, contentX + contentWidth - countWidth, centeredTextY(GLOBAL_NAV_HEIGHT, navFont), ACTION_BLUE_ON_DARK);

        Render2DUtility.drawRect(0.0F, GLOBAL_NAV_HEIGHT, this.width, SUB_NAV_HEIGHT, FROSTED_PARCHMENT);
        Render2DUtility.drawRect(0.0F, GLOBAL_NAV_HEIGHT + SUB_NAV_HEIGHT - 1.0F, this.width, 1.0F, HAIRLINE);

        FontRenderer subTitleFont = clickGuiFont(21.0F);
        subTitleFont.drawString("ClickGui", contentX, GLOBAL_NAV_HEIGHT + centeredTextY(SUB_NAV_HEIGHT, subTitleFont), INK);

        FontRenderer buttonFont = clickGuiFont(14.0F);
        String pillText = enabledCount + " Enabled";
        float pillWidth = Math.max(94.0F, buttonFont.getStringWidth(pillText) + 28.0F);
        float pillHeight = 28.0F;
        float pillX = contentX + contentWidth - pillWidth;
        float pillY = GLOBAL_NAV_HEIGHT + (SUB_NAV_HEIGHT - pillHeight) * 0.5F;
        Render2DUtility.drawPill(pillX, pillY, pillWidth, pillHeight, ACTION_BLUE, 0);
        buttonFont.drawString(pillText, pillX + (pillWidth - buttonFont.getStringWidth(pillText)) * 0.5F,
            pillY + centeredTextY(pillHeight, buttonFont), CANVAS);
    }

    private void renderContent(int mouseX, int mouseY) {
        float contentX = contentX();
        float contentWidth = contentWidth();
        float top = contentTop(scroll);

        renderHero(contentX, top, contentWidth);
        for (CategoryLayout layout : buildCategoryLayouts(scroll)) {
            renderCategory(layout, mouseX, mouseY);
        }
    }

    private void renderHero(float x, float y, float width) {
        boolean compact = width < 520.0F;
        FontRenderer titleFont = clickGuiFont(compact ? 30.0F : 34.0F);
        FontRenderer bodyFont = clickGuiFont(17.0F);
        FontRenderer pillFont = clickGuiFont(14.0F);
        int enabledCount = enabledModuleCount();
        int moduleCount = ModuleManager.getModules().size();

        titleFont.drawString("Nyx", x, y, INK);
        String summary = moduleCount + " modules";
        bodyFont.drawString(summary, x, y + 42.0F, INK_MUTED);

        String enabledText = enabledCount + " active";
        float pillWidth = Math.max(78.0F, pillFont.getStringWidth(enabledText) + 26.0F);
        float pillHeight = 30.0F;
        float pillX = compact ? x : x + width - pillWidth;
        float pillY = compact ? y + 72.0F : y + 14.0F;
        Render2DUtility.drawPill(pillX, pillY, pillWidth, pillHeight, CANVAS, ACTION_BLUE);
        pillFont.drawString(enabledText, pillX + (pillWidth - pillFont.getStringWidth(enabledText)) * 0.5F,
            pillY + centeredTextY(pillHeight, pillFont), ACTION_BLUE);
    }

    private void renderCategory(CategoryLayout layout, int mouseX, int mouseY) {
        Render2DUtility.drawSoftCard(layout.x(), layout.y(), layout.width(), layout.height(), CARD_RADIUS, CANVAS, HAIRLINE);

        float innerX = layout.x() + CARD_PADDING;
        float innerWidth = layout.width() - CARD_PADDING * 2.0F;
        float titleY = layout.y() + CARD_PADDING;
        FontRenderer titleFont = clickGuiFont(17.0F);
        FontRenderer captionFont = clickGuiFont(12.0F);
        String categoryName = categoryLabel(layout.category());
        String countLabel = enabledModuleCount(layout.modules()) + "/" + layout.modules().size();

        titleFont.drawString(categoryName, innerX, titleY, INK);
        float countWidth = captionFont.getStringWidth(countLabel);
        captionFont.drawString(countLabel, innerX + innerWidth - countWidth, titleY + 3.0F, INK_MUTED);

        float rowY = layout.y() + CARD_PADDING + CARD_HEADER_HEIGHT + 14.0F;
        for (Module module : layout.modules()) {
            renderModuleRow(module, innerX, rowY, innerWidth, mouseX, mouseY);
            rowY += MODULE_ROW_HEIGHT + MODULE_ROW_GAP;
        }
    }

    private void renderModuleRow(Module module, float x, float y, float width, int mouseX, int mouseY) {
        boolean hovered = isInside(mouseX, mouseY, x, y, width, MODULE_ROW_HEIGHT);
        boolean enabled = module.isEnabled();
        int rowColor = enabled ? ROW_ACTIVE : hovered ? ROW_HOVER : CANVAS;
        int borderColor = enabled ? 0 : hovered ? DIVIDER_SOFT : 0;

        Render2DUtility.drawSoftCard(x, y, width, MODULE_ROW_HEIGHT, MODULE_ROW_RADIUS, rowColor, borderColor);
        if (enabled) {
            Render2DUtility.drawOutlineRoundedRect(x, y, width, MODULE_ROW_HEIGHT, MODULE_ROW_RADIUS, 2.0F, ACTION_BLUE);
        }

        FontRenderer nameFont = clickGuiFont(15.0F);
        FontRenderer descriptionFont = clickGuiFont(12.0F);
        float toggleWidth = 42.0F;
        float toggleHeight = 24.0F;
        float toggleX = x + width - toggleWidth - 14.0F;
        float toggleY = y + (MODULE_ROW_HEIGHT - toggleHeight) * 0.5F;
        float textWidth = Math.max(20.0F, toggleX - x - 26.0F);

        nameFont.drawString(trimToWidth(nameFont, module.getName(), textWidth), x + 14.0F, y + 9.0F, INK);
        descriptionFont.drawString(trimToWidth(descriptionFont, module.getDescription(), textWidth), x + 14.0F, y + 31.0F, INK_MUTED);

        Render2DUtility.drawToggleSwitch(toggleX, toggleY, toggleWidth, toggleHeight, enabled, ACTION_BLUE, TOGGLE_DISABLED, CANVAS);
    }

    private Module getModuleAt(double mouseX, double mouseY) {
        if (mouseY < GLOBAL_NAV_HEIGHT + SUB_NAV_HEIGHT) {
            return null;
        }

        for (CategoryLayout layout : buildCategoryLayouts(scroll)) {
            float innerX = layout.x() + CARD_PADDING;
            float innerWidth = layout.width() - CARD_PADDING * 2.0F;
            float rowY = layout.y() + CARD_PADDING + CARD_HEADER_HEIGHT + 14.0F;
            for (Module module : layout.modules()) {
                if (isInside(mouseX, mouseY, innerX, rowY, innerWidth, MODULE_ROW_HEIGHT)) {
                    return module;
                }
                rowY += MODULE_ROW_HEIGHT + MODULE_ROW_GAP;
            }
        }

        return null;
    }

    private List<CategoryLayout> buildCategoryLayouts(float scrollOffset) {
        List<CategoryLayout> layouts = new ArrayList<>();
        float contentX = contentX();
        float contentWidth = contentWidth();
        int columns = columnCount(contentWidth);
        float gap = columns == 1 ? CARD_GAP_COMPACT : CARD_GAP;
        float cardWidth = (contentWidth - gap * (columns - 1)) / columns;
        float cardTop = contentTop(scrollOffset) + heroHeight() + 28.0F;
        float[] columnYs = new float[columns];
        Arrays.fill(columnYs, cardTop);

        for (Category category : Category.values()) {
            List<Module> modules = ModuleManager.getModules(category);
            if (modules.isEmpty()) {
                continue;
            }

            int column = shortestColumn(columnYs);
            float x = contentX + column * (cardWidth + gap);
            float y = columnYs[column];
            float height = categoryCardHeight(modules);
            layouts.add(new CategoryLayout(category, modules, x, y, cardWidth, height));
            columnYs[column] += height + gap;
        }

        return layouts;
    }

    private float categoryCardHeight(List<Module> modules) {
        float rowsHeight = modules.size() * MODULE_ROW_HEIGHT + Math.max(0, modules.size() - 1) * MODULE_ROW_GAP;
        return CARD_PADDING + CARD_HEADER_HEIGHT + 14.0F + rowsHeight + CARD_PADDING;
    }

    private void updateScrollLimit() {
        float bottom = calculateContentBottom(0.0F);
        maxScroll = Math.max(0.0F, bottom + BOTTOM_PADDING - this.height);
        scroll = clamp(scroll, 0.0F, maxScroll);
    }

    private float calculateContentBottom(float scrollOffset) {
        float bottom = contentTop(scrollOffset) + heroHeight();
        for (CategoryLayout layout : buildCategoryLayouts(scrollOffset)) {
            bottom = Math.max(bottom, layout.y() + layout.height());
        }
        return bottom;
    }

    private float contentTop(float scrollOffset) {
        return GLOBAL_NAV_HEIGHT + SUB_NAV_HEIGHT + CONTENT_TOP_GAP - scrollOffset;
    }

    private float contentX() {
        return (this.width - contentWidth()) * 0.5F;
    }

    private float contentWidth() {
        float margin = this.width < 520 ? CONTENT_MARGIN_COMPACT : CONTENT_MARGIN;
        return Math.max(1.0F, Math.min(CONTENT_MAX_WIDTH, this.width - margin * 2.0F));
    }

    private float heroHeight() {
        return contentWidth() < 520.0F ? HERO_HEIGHT_COMPACT : HERO_HEIGHT;
    }

    private int columnCount(float contentWidth) {
        if (contentWidth < 560.0F) {
            return 1;
        }
        if (contentWidth < 900.0F) {
            return 2;
        }
        return 3;
    }

    private int shortestColumn(float[] columnYs) {
        int column = 0;
        for (int i = 1; i < columnYs.length; i++) {
            if (columnYs[i] < columnYs[column]) {
                column = i;
            }
        }
        return column;
    }

    private int enabledModuleCount() {
        int count = 0;
        for (Module module : ModuleManager.getModules()) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    private int enabledModuleCount(List<Module> modules) {
        int count = 0;
        for (Module module : modules) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    private static String categoryLabel(Category category) {
        String value = category.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private FontRenderer clickGuiFont(float size) {
        return FontManager.getClickGuiRenderer(size);
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

    private record CategoryLayout(Category category, List<Module> modules, float x, float y, float width, float height) {
    }
}
