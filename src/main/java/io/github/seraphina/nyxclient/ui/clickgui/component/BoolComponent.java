package io.github.seraphina.nyxclient.ui.clickgui.component;

import io.github.seraphina.nyxclient.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.value.impl.BoolValue;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class BoolComponent extends AbstractComponent {
    private final BoolValue boolValue;
    private float toggleX;
    private float toggleY;
    private float toggleProgress;
    private float hoverProgress;

    public BoolComponent(BoolValue value) {
        super(value);
        this.boolValue = value;
        this.toggleProgress = value.getValue() ? 1.0F : 0.0F;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        float toggleWidth = 30.0F;
        float toggleHeight = 16.0F;
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
        int fillColor = Render2DUtility.mix(TOGGLE_OFF, ACCENT, toggleProgress);
        Render2DUtility.drawPill(toggleX, toggleY, toggleWidth, toggleHeight, fillColor, 0);
        float padding = Math.max(2.0F, toggleHeight * 0.125F);
        float knobRadius = Math.max(1.0F, (toggleHeight - padding * 2.0F) * 0.5F) + hoverProgress * 0.4F;
        float knobX = lerp(toggleX + padding + knobRadius, toggleX + toggleWidth - padding - knobRadius, toggleProgress);
        Render2DUtility.drawCircle(knobX, toggleY + toggleHeight * 0.5F, knobRadius, TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW_MOUSE_BUTTON_LEFT || !isInside(mouseX, mouseY, x, y, width, ROW_HEIGHT)) {
            return false;
        }

        boolValue.setValue(!boolValue.getValue());
        return true;
    }
}
