package io.github.seraphina.nyxclient.ui.clickgui.component;

import io.github.seraphina.nyxclient.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.utility.font.FontRenderer;
import io.github.seraphina.nyxclient.value.impl.EnumValue;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class EnumComponent extends AbstractComponent {
    private static final float OPTION_HEIGHT = 24.0F;

    private final EnumValue<?> enumValue;
    private final Map<Enum<?>, Float> optionHoverProgress = new IdentityHashMap<>();
    private boolean expanded;
    private float expandProgress;
    private float hoverProgress;

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
        boolean hovered = isInside(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        hoverProgress = animate(hoverProgress, hovered ? 1.0F : 0.0F, 18.0F);
        expandProgress = animate(expandProgress, expanded ? 1.0F : 0.0F, 16.0F);

        if (hoverProgress > 0.0F) {
            Render2DUtility.drawRoundedRect(x - 4.0F, y + 3.0F, width + 8.0F, ROW_HEIGHT - 6.0F, 5.0F,
                Render2DUtility.applyOpacity(HOVER, hoverProgress));
        }
        labelFont.drawString(trimToWidth(labelFont, value.getDisplayName(), Math.max(20.0F, pillX - x - 8.0F)), x,
            y + centeredTextY(ROW_HEIGHT, labelFont), Render2DUtility.mix(TEXT_MUTED, TEXT, hoverProgress * 0.45F));
        renderPill(pillX, y + 5.0F, pillWidth, 20.0F, current, true);
        renderArrow(x + width - arrowWidth * 0.5F, y + ROW_HEIGHT * 0.5F);

        float visibleOptionsHeight = enumValue.getModes().length * OPTION_HEIGHT * expandProgress;
        if (visibleOptionsHeight <= 0.5F) {
            return;
        }

        Render2DUtility.withClip(x, y + ROW_HEIGHT, width, visibleOptionsHeight, () -> {
            float optionY = y + ROW_HEIGHT;
            for (Enum<?> mode : enumValue.getModes()) {
                boolean selected = mode == enumValue.getValue();
                boolean optionHovered = isInside(mouseX, mouseY, x, optionY, width, OPTION_HEIGHT);
                float optionHover = animate(optionHoverProgress.getOrDefault(mode, 0.0F), optionHovered ? 1.0F : 0.0F, 18.0F);
                optionHoverProgress.put(mode, optionHover);

                if (selected || optionHover > 0.0F) {
                    int fill = selected ? 0x143D81F7 : Render2DUtility.applyOpacity(HOVER, optionHover);
                    Render2DUtility.drawRoundedRect(x, optionY + 2.0F, width, OPTION_HEIGHT - 4.0F, 4.0F, fill);
                }

                int color = selected ? ACCENT : Render2DUtility.mix(TEXT_SUBTLE, TEXT, optionHover);
                valueFont.drawString(trimToWidth(valueFont, mode.toString(), width - 20.0F), x + 10.0F,
                    optionY + centeredTextY(OPTION_HEIGHT, valueFont), Render2DUtility.applyOpacity(color, expandProgress));
                optionY += OPTION_HEIGHT;
            }
        });
    }

    @Override
    public float getHeight() {
        return ROW_HEIGHT + enumValue.getModes().length * OPTION_HEIGHT * expandProgress;
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
        float activeProgress = Math.max(expandProgress, hoverProgress * 0.35F);
        Render2DUtility.withRotation(lerp(0.0F, 90.0F, expandProgress), centerX, centerY, () -> {
            arrowFont.drawString(">", centerX - textWidth * 0.5F, centerY - textHeight * 0.5F,
                Render2DUtility.mix(TEXT_SUBTLE, ACCENT, activeProgress));
        });
    }
}
