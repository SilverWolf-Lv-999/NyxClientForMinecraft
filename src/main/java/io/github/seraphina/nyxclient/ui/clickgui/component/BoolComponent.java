package io.github.seraphina.nyxclient.ui.clickgui.component;

import io.github.seraphina.nyxclient.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.value.impl.BoolValue;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class BoolComponent extends AbstractComponent {
    private final BoolValue boolValue;
    private float toggleX;
    private float toggleY;

    public BoolComponent(BoolValue value) {
        super(value);
        this.boolValue = value;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        float toggleWidth = 30.0F;
        float toggleHeight = 16.0F;
        toggleX = x + width - toggleWidth;
        toggleY = y + 7.0F;

        drawLabel(toggleWidth + 12.0F);
        Render2DUtility.drawToggleSwitch(toggleX, toggleY, toggleWidth, toggleHeight, boolValue.getValue(), ACCENT, TOGGLE_OFF, TEXT);
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
