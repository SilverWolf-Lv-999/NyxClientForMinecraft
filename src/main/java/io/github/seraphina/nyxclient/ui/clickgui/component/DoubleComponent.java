package io.github.seraphina.nyxclient.ui.clickgui.component;

import io.github.seraphina.nyxclient.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.utility.font.FontRenderer;
import io.github.seraphina.nyxclient.value.impl.DoubleValue;

import java.util.Locale;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class DoubleComponent extends AbstractComponent {
    private final DoubleValue doubleValue;
    private float sliderX;
    private float sliderY;
    private float sliderWidth;
    private boolean dragging;
    private float progressVisual = Float.NaN;
    private float hoverProgress;
    private float dragProgress;

    public DoubleComponent(DoubleValue value) {
        super(value);
        this.doubleValue = value;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        String valueText = displayValue();
        FontRenderer valueFont = font(9.0F);
        float valueBoxWidth = Math.max(42.0F, valueFont.getStringWidth(valueText) + 12.0F);
        sliderWidth = Math.min(92.0F, Math.max(34.0F, width - valueBoxWidth - 76.0F));
        sliderX = x + width - valueBoxWidth - sliderWidth - 8.0F;
        sliderY = y + ROW_HEIGHT * 0.5F - 1.5F;

        drawLabel(width - sliderX + 10.0F);
        float progress = percentage(doubleValue.getValue(), doubleValue.getMin(), doubleValue.getMax());
        boolean hovered = isInside(mouseX, mouseY, sliderX - 4.0F, y, sliderWidth + 8.0F, ROW_HEIGHT);
        hoverProgress = animate(hoverProgress, hovered ? 1.0F : 0.0F, 18.0F);
        dragProgress = animate(dragProgress, dragging ? 1.0F : 0.0F, 20.0F);
        progressVisual = animate(progressVisual, progress, dragging ? 30.0F : 14.0F);

        int trackColor = Render2DUtility.mix(SLIDER_BACKGROUND, CONTROL_HOVER, hoverProgress * 0.7F);
        Render2DUtility.drawRoundedRect(sliderX, sliderY, sliderWidth, 3.0F, 2.0F, trackColor);
        Render2DUtility.drawRoundedRect(sliderX, sliderY, sliderWidth * progressVisual, 3.0F, 2.0F, ACCENT);
        float knobRadius = 3.0F + hoverProgress * 0.45F + dragProgress * 1.0F;
        Render2DUtility.drawCircle(sliderX + sliderWidth * progressVisual, sliderY + 1.5F, knobRadius, TEXT);
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
        double range = doubleValue.getMax() - doubleValue.getMin();
        double raw = doubleValue.getMin() + clamp((float)((mouseX - sliderX) / sliderWidth), 0.0F, 1.0F) * range;
        double step = Math.abs(doubleValue.getStep());
        if (step > 0.0D) {
            raw = doubleValue.getMin() + Math.round((raw - doubleValue.getMin()) / step) * step;
        }
        doubleValue.setValue(raw);
    }

    private String displayValue() {
        String formatted = Math.abs(doubleValue.getStep()) >= 1.0D
            ? String.format(Locale.ROOT, "%.0f", doubleValue.getValue())
            : String.format(Locale.ROOT, "%.2f", doubleValue.getValue());
        return formatted + (doubleValue.isPercentageMode() ? "%" : "");
    }

    private static float percentage(double value, double min, double max) {
        if (max - min == 0.0D) {
            return 0.0F;
        }
        return clamp((float)((value - min) / (max - min)), 0.0F, 1.0F);
    }
}
