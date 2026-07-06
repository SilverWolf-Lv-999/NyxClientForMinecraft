package io.github.seraphina.nyxclient.ui.clickgui.component;

import io.github.seraphina.nyxclient.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.utility.font.FontRenderer;
import io.github.seraphina.nyxclient.value.impl.ButtonValue;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class ButtonComponent extends AbstractComponent {
    private static final float BUTTON_HEIGHT = 20.0F;

    private final ButtonValue buttonValue;
    private float buttonX;
    private float buttonY;
    private float buttonWidth;

    public ButtonComponent(ButtonValue value) {
        super(value);
        this.buttonValue = value;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        FontRenderer buttonFont = font(9.0F);
        buttonWidth = Math.min(Math.max(58.0F, buttonFont.getStringWidth("Run") + 22.0F), Math.max(42.0F, width * 0.45F));
        buttonX = x + width - buttonWidth;
        buttonY = y + 5.0F;

        drawLabel(buttonWidth + 12.0F);
        boolean hovered = isInside(mouseX, mouseY, buttonX, buttonY, buttonWidth, BUTTON_HEIGHT);
        Render2DUtility.drawRoundedRect(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, 4.0F, hovered ? CONTROL_HOVER : CONTROL_BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, 4.0F, 1.0F, hovered ? BORDER : BORDER_SOFT);
        buttonFont.drawCenteredString("Run", buttonX + buttonWidth * 0.5F, buttonY + centeredTextY(BUTTON_HEIGHT, buttonFont), hovered ? TEXT : TEXT_SUBTLE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInside(mouseX, mouseY, buttonX, buttonY, buttonWidth, BUTTON_HEIGHT)) {
            return false;
        }

        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            buttonValue.press();
            return true;
        }
        if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            buttonValue.rightClick();
            return true;
        }
        return false;
    }
}
