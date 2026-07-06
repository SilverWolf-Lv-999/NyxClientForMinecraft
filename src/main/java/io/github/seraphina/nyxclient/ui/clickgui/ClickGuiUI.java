package io.github.seraphina.nyxclient.ui.clickgui;

import io.github.seraphina.nyxclient.manager.ModuleManager;
import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class ClickGuiUI extends Screen {
    private static final int START_X = 16;
    private static final int START_Y = 16;
    private static final int PANEL_WIDTH = 120;
    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = 18;
    private static final int GAP = 8;

    private static final int PANEL_COLOR = 0xD014171C;
    private static final int HEADER_COLOR = 0xFF20242C;
    private static final int ROW_COLOR = 0xA0181B22;
    private static final int ROW_HOVER_COLOR = 0xC0272D38;
    private static final int ENABLED_COLOR = 0xFF49B36D;
    private static final int TEXT_COLOR = 0xFFECEFF4;
    private static final int MUTED_TEXT_COLOR = 0xFF9AA3AE;

    public ClickGuiUI() {
        super(Component.empty());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = START_X;
        int y = START_Y;
        int rowMaxHeight = 0;

        for (Category category : Category.values()) {
            List<Module> modules = ModuleManager.getModules(category);
            if (modules.isEmpty()) {
                continue;
            }

            int panelHeight = HEADER_HEIGHT + modules.size() * ROW_HEIGHT;
            if (x + PANEL_WIDTH > this.width - START_X) {
                x = START_X;
                y += rowMaxHeight + GAP;
                rowMaxHeight = 0;
            }

            renderCategory(guiGraphics, category, modules, x, y, panelHeight, mouseX, mouseY);
            x += PANEL_WIDTH + GAP;
            rowMaxHeight = Math.max(rowMaxHeight, panelHeight);
        }
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
    public boolean isPauseScreen() {
        return false;
    }

    private void renderCategory(GuiGraphics guiGraphics, Category category, List<Module> modules, int x, int y, int height, int mouseX, int mouseY) {
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + height, PANEL_COLOR);
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, HEADER_COLOR);
        guiGraphics.drawString(this.font, category.name(), x + 6, y + 6, TEXT_COLOR, false);

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int rowY = y + HEADER_HEIGHT + i * ROW_HEIGHT;
            boolean hovered = isInside(mouseX, mouseY, x, rowY, PANEL_WIDTH, ROW_HEIGHT);
            int rowColor = hovered ? ROW_HOVER_COLOR : ROW_COLOR;

            guiGraphics.fill(x, rowY, x + PANEL_WIDTH, rowY + ROW_HEIGHT, rowColor);
            guiGraphics.fill(x, rowY, x + 3, rowY + ROW_HEIGHT, module.isEnabled() ? ENABLED_COLOR : 0xFF3A404A);
            guiGraphics.drawString(this.font, module.getName(), x + 8, rowY + 5, module.isEnabled() ? ENABLED_COLOR : MUTED_TEXT_COLOR, false);
        }
    }

    private Module getModuleAt(double mouseX, double mouseY) {
        int x = START_X;
        int y = START_Y;
        int rowMaxHeight = 0;

        for (Category category : Category.values()) {
            List<Module> modules = ModuleManager.getModules(category);
            if (modules.isEmpty()) {
                continue;
            }

            int panelHeight = HEADER_HEIGHT + modules.size() * ROW_HEIGHT;
            if (x + PANEL_WIDTH > this.width - START_X) {
                x = START_X;
                y += rowMaxHeight + GAP;
                rowMaxHeight = 0;
            }

            for (int i = 0; i < modules.size(); i++) {
                int rowY = y + HEADER_HEIGHT + i * ROW_HEIGHT;
                if (isInside(mouseX, mouseY, x, rowY, PANEL_WIDTH, ROW_HEIGHT)) {
                    return modules.get(i);
                }
            }

            x += PANEL_WIDTH + GAP;
            rowMaxHeight = Math.max(rowMaxHeight, panelHeight);
        }

        return null;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
