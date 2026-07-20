package io.github.seraphina.nyx.client.ui.clickgui.component;

import io.github.seraphina.nyx.client.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.value.impl.BoolValue;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class BoolComponent extends AbstractComponent {
    private final BoolValue boolValue;
    private float toggleX;
    private float toggleY;
    private float toggleWidth;
    private float toggleHeight;
    private float toggleProgress;
    private float hoverProgress;

    public BoolComponent(BoolValue value) {
        super(value);
        this.boolValue = value;
        this.toggleProgress = value.getValue() ? 1.0F : 0.0F;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        if (compactLayout()) {
            renderCompact(mouseX, mouseY);
            return;
        }

        toggleWidth = 30.0F;
        toggleHeight = 16.0F;
        toggleX = x + width - toggleWidth;
        toggleY = y + 7.0F;
        boolean hovered = isInside(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        hoverProgress = animate(hoverProgress, hovered ? 1.0F : 0.0F, 18.0F);
        toggleProgress = animate(toggleProgress, boolValue.getValue() ? 1.0F : 0.0F, 16.0F);

        if (hoverProgress > 0.0F) {
            Render2DUtility.drawRoundedRect(x - 4.0F, y + 3.0F, width + 8.0F, ROW_HEIGHT - 6.0F, 5.0F,
                Render2DUtility.applyOpacity(HOVER, hoverProgress));
        }
        drawLabel(toggleWidth + 12.0F);
        int fillColor = Render2DUtility.mix(TOGGLE_OFF, accentColor, toggleProgress);
        Render2DUtility.drawPill(toggleX, toggleY, toggleWidth, toggleHeight, fillColor, 0);
        float padding = Math.max(2.0F, toggleHeight * 0.125F);
        float knobRadius = Math.max(1.0F, (toggleHeight - padding * 2.0F) * 0.5F) + hoverProgress * 0.4F;
        float knobX = lerp(toggleX + padding + knobRadius, toggleX + toggleWidth - padding - knobRadius, toggleProgress);
        Render2DUtility.drawCircle(knobX, toggleY + toggleHeight * 0.5F, knobRadius, TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inside = compactLayout()
            ? isInside(mouseX, mouseY, toggleX, toggleY, toggleWidth, toggleHeight)
            : isInside(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        if (button != GLFW_MOUSE_BUTTON_LEFT || !inside) {
            return false;
        }

        boolValue.setValue(!boolValue.getValue());
        return true;
    }

    private void renderCompact(int mouseX, int mouseY) {
        toggleWidth = 20.0F;
        toggleHeight = 10.0F;
        toggleX = x + width - toggleWidth;
        toggleY = y + (getHeight() - toggleHeight) * 0.5F;
        boolean hovered = isInside(mouseX, mouseY, toggleX, toggleY, toggleWidth, toggleHeight);
        hoverProgress = animate(hoverProgress, hovered ? 1.0F : 0.0F, 18.0F);
        toggleProgress = animate(toggleProgress, boolValue.getValue() ? 1.0F : 0.0F, 16.0F);

        var labelFont = font(7.0F);
        labelFont.drawString(
            trimToWidth(labelFont, value.getDisplayName(), width - toggleWidth - 8.0F),
            x,
            y + centeredTextY(getHeight(), labelFont),
            0xCCFFFFFF
        );

        int fillColor = Render2DUtility.mix(0xFF3C3C3C, accentColor, toggleProgress);
        Render2DUtility.drawRoundedRect(toggleX, toggleY, toggleWidth, toggleHeight, 4.0F, fillColor);
        float knobX = lerp(toggleX + toggleHeight * 0.5F, toggleX + toggleWidth - toggleHeight * 0.5F, toggleProgress);
        Render2DUtility.drawCircle(knobX, toggleY + toggleHeight * 0.5F, 5.0F + hoverProgress * 0.2F, TEXT);
    }
}
