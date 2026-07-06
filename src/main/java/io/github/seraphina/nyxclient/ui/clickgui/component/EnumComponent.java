package io.github.seraphina.nyxclient.ui.clickgui.component;

import io.github.seraphina.nyxclient.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.utility.font.FontRenderer;
import io.github.seraphina.nyxclient.value.impl.EnumValue;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class EnumComponent extends AbstractComponent {
    private static final float OPTION_HEIGHT = 24.0F;

    private final EnumValue<?> enumValue;
    private boolean expanded;

    public EnumComponent(EnumValue<?> value) {
        super(value);
        this.enumValue = value;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        FontRenderer labelFont = font(10.0F);
        FontRenderer valueFont = font(9.0F);
        float arrowWidth = 18.0F;
        String current = enumValue.getValue().toString();
        float pillWidth = Math.min(Math.max(64.0F, valueFont.getStringWidth(current) + 18.0F), Math.max(42.0F, width * 0.45F));
        float pillX = x + width - arrowWidth - pillWidth - 6.0F;

        labelFont.drawString(trimToWidth(labelFont, value.getDisplayName(), Math.max(20.0F, pillX - x - 8.0F)), x, y + centeredTextY(ROW_HEIGHT, labelFont), TEXT_MUTED);
        renderPill(pillX, y + 5.0F, pillWidth, 20.0F, current, true);
        renderArrow(x + width - arrowWidth * 0.5F, y + ROW_HEIGHT * 0.5F);

        if (!expanded) {
            return;
        }

        float optionY = y + ROW_HEIGHT;
        for (Enum<?> mode : enumValue.getModes()) {
            boolean selected = mode == enumValue.getValue();
            boolean hovered = isInside(mouseX, mouseY, x, optionY, width, OPTION_HEIGHT);
            if (hovered || selected) {
                Render2DUtility.drawRoundedRect(x, optionY + 2.0F, width, OPTION_HEIGHT - 4.0F, 4.0F, selected ? 0x143D81F7 : HOVER);
            }

            int color = selected ? ACCENT : hovered ? TEXT : TEXT_SUBTLE;
            valueFont.drawString(trimToWidth(valueFont, mode.toString(), width - 20.0F), x + 10.0F, optionY + centeredTextY(OPTION_HEIGHT, valueFont), color);
            optionY += OPTION_HEIGHT;
        }
    }

    @Override
    public float getHeight() {
        return ROW_HEIGHT + (expanded ? enumValue.getModes().length * OPTION_HEIGHT : 0.0F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (isInside(mouseX, mouseY, x, y, width, ROW_HEIGHT)) {
            expanded = !expanded;
            return true;
        }

        if (!expanded) {
            return false;
        }

        float optionY = y + ROW_HEIGHT;
        for (Enum<?> mode : enumValue.getModes()) {
            if (isInside(mouseX, mouseY, x, optionY, width, OPTION_HEIGHT)) {
                enumValue.setMode(mode.name());
                return true;
            }
            optionY += OPTION_HEIGHT;
        }
        return false;
    }

    private void renderArrow(float centerX, float centerY) {
        FontRenderer arrowFont = font(14.0F);
        float textWidth = arrowFont.getStringWidth(">");
        float textHeight = arrowFont.getLineHeight();
        Render2DUtility.withRotation(expanded ? 90.0F : 0.0F, centerX, centerY, () -> {
            arrowFont.drawString(">", centerX - textWidth * 0.5F, centerY - textHeight * 0.5F, expanded ? ACCENT : TEXT_SUBTLE);
        });
    }
}
