package io.github.seraphina.nyx.client.ui.clickgui.component;

import io.github.seraphina.nyx.client.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.value.impl.ButtonValue;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class ButtonComponent extends AbstractComponent {
    private static final float BUTTON_HEIGHT = 20.0F;

    private final ButtonValue buttonValue;
    private float buttonX;
    private float buttonY;
    private float buttonWidth;
    private float buttonHeight = BUTTON_HEIGHT;
    private float hoverProgress;
    private float pressProgress;

    public ButtonComponent(ButtonValue value) {
        super(value);
        this.buttonValue = value;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        FontRenderer buttonFont = compactLayout() ? boldFont(6.5F) : font(9.0F);
        if (compactLayout()) {
            buttonHeight = 14.0F;
            buttonWidth = Math.min(42.0F, Math.max(34.0F, buttonFont.getStringWidth("Run") + 14.0F));
            buttonX = x + width - buttonWidth;
            buttonY = y + 2.0F;
        } else {
            buttonHeight = BUTTON_HEIGHT;
            buttonWidth = Math.min(Math.max(58.0F, buttonFont.getStringWidth("Run") + 22.0F), Math.max(42.0F, width * 0.45F));
            buttonX = x + width - buttonWidth;
            buttonY = y + 5.0F;
        }

        drawLabel(buttonWidth + (compactLayout() ? 8.0F : 12.0F));
        boolean hovered = isInside(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);
        hoverProgress = animate(hoverProgress, hovered ? 1.0F : 0.0F, 18.0F);
        pressProgress = animate(pressProgress, 0.0F, 14.0F);

        int fillColor = Render2DUtility.mix(CONTROL_BACKGROUND, CONTROL_HOVER, hoverProgress);
        fillColor = Render2DUtility.mix(fillColor, accentColor, pressProgress * 0.35F);
        int outlineColor = Render2DUtility.mix(BORDER_SOFT, BORDER, hoverProgress);
        outlineColor = Render2DUtility.mix(outlineColor, accentColor, pressProgress);
        float radius = compactLayout() ? 3.0F : 4.0F;
        Render2DUtility.drawRoundedRect(buttonX, buttonY, buttonWidth, buttonHeight, radius, fillColor);
        Render2DUtility.drawOutlineRoundedRect(buttonX, buttonY, buttonWidth, buttonHeight, radius, 1.0F, outlineColor);
        buttonFont.drawCenteredString("Run", buttonX + buttonWidth * 0.5F,
            buttonY + centeredTextY(buttonHeight, buttonFont) + pressProgress * 0.5F,
            Render2DUtility.mix(TEXT_SUBTLE, TEXT, Math.max(hoverProgress, pressProgress)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInside(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight)) {
            return false;
        }

        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            buttonValue.press();
            pressProgress = 1.0F;
            return true;
        }
        if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            buttonValue.rightClick();
            pressProgress = 1.0F;
            return true;
        }
        return false;
    }
}
