package io.github.seraphina.nyxclient.ui.clickgui.component;

import io.github.seraphina.nyxclient.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.utility.font.FontRenderer;
import io.github.seraphina.nyxclient.value.impl.IntValue;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class IntComponent extends AbstractComponent {
    private final IntValue intValue;
    private float sliderX;
    private float sliderY;
    private float sliderWidth;
    private boolean dragging;

    public IntComponent(IntValue value) {
        super(value);
        this.intValue = value;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        String valueText = intValue.getValue() + (intValue.isPercentageMode() ? "%" : "");
        FontRenderer valueFont = font(9.0F);
        float valueBoxWidth = Math.max(42.0F, valueFont.getStringWidth(valueText) + 12.0F);
        sliderWidth = Math.min(92.0F, Math.max(34.0F, width - valueBoxWidth - 76.0F));
        sliderX = x + width - valueBoxWidth - sliderWidth - 8.0F;
        sliderY = y + ROW_HEIGHT * 0.5F - 1.5F;

        drawLabel(width - sliderX + 10.0F);
        float progress = percentage(intValue.getValue(), intValue.getMin(), intValue.getMax());
        Render2DUtility.drawRoundedRect(sliderX, sliderY, sliderWidth, 3.0F, 2.0F, SLIDER_BACKGROUND);
        Render2DUtility.drawRoundedRect(sliderX, sliderY, sliderWidth * progress, 3.0F, 2.0F, ACCENT);
        Render2DUtility.drawCircle(sliderX + sliderWidth * progress, sliderY + 1.5F, dragging ? 4.0F : 3.0F, TEXT);
        renderPill(x + width - valueBoxWidth, y + 5.0F, valueBoxWidth, 20.0F, valueText, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW_MOUSE_BUTTON_LEFT || !isInside(mouseX, mouseY, sliderX - 4.0F, y, sliderWidth + 8.0F, ROW_HEIGHT)) {
            return false;
        }

        dragging = true;
        updateValue(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = dragging;
        dragging = false;
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging) {
            return false;
        }

        updateValue(mouseX);
        return true;
    }

    @Override
    public void blur() {
        dragging = false;
    }

    private void updateValue(double mouseX) {
        int range = intValue.getMax() - intValue.getMin();
        float progress = clamp((float)((mouseX - sliderX) / sliderWidth), 0.0F, 1.0F);
        int step = Math.max(1, Math.abs(intValue.getStep()));
        int raw = intValue.getMin() + Math.round(progress * range / step) * step;
        intValue.setValue(raw);
    }

    private static float percentage(double value, double min, double max) {
        if (max - min == 0.0D) {
            return 0.0F;
        }
        return clamp((float)((value - min) / (max - min)), 0.0F, 1.0F);
    }
}
