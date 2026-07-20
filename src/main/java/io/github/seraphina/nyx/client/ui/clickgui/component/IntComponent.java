package io.github.seraphina.nyx.client.ui.clickgui.component;

import io.github.seraphina.nyx.client.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.value.impl.IntValue;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class IntComponent extends AbstractComponent {
    private final IntValue intValue;
    private float sliderX;
    private float sliderY;
    private float sliderWidth;
    private boolean dragging;
    private float progressVisual = Float.NaN;
    private float hoverProgress;
    private float dragProgress;

    public IntComponent(IntValue value) {
        super(value);
        this.intValue = value;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        if (compactLayout()) {
            renderCompact(mouseX, mouseY);
            return;
        }

        String valueText = intValue.getValue() + (intValue.isPercentageMode() ? "%" : "");
        FontRenderer valueFont = font(9.0F);
        float valueBoxWidth = Math.max(42.0F, valueFont.getStringWidth(valueText) + 12.0F);
        sliderWidth = Math.min(92.0F, Math.max(34.0F, width - valueBoxWidth - 76.0F));
        sliderX = x + width - valueBoxWidth - sliderWidth - 8.0F;
        sliderY = y + ROW_HEIGHT * 0.5F - 1.5F;

        drawLabel(width - sliderX + 10.0F);
        float progress = percentage(intValue.getValue(), intValue.getMin(), intValue.getMax());
        boolean hovered = isInside(mouseX, mouseY, sliderX - 4.0F, y, sliderWidth + 8.0F, ROW_HEIGHT);
        hoverProgress = animate(hoverProgress, hovered ? 1.0F : 0.0F, 18.0F);
        dragProgress = animate(dragProgress, dragging ? 1.0F : 0.0F, 20.0F);
        progressVisual = animate(progressVisual, progress, dragging ? 30.0F : 14.0F);

        int trackColor = Render2DUtility.mix(SLIDER_BACKGROUND, CONTROL_HOVER, hoverProgress * 0.7F);
        Render2DUtility.drawRoundedRect(sliderX, sliderY, sliderWidth, 3.0F, 2.0F, trackColor);
        Render2DUtility.drawRoundedRect(sliderX, sliderY, sliderWidth * progressVisual, 3.0F, 2.0F, accentColor);
        float knobRadius = 3.0F + hoverProgress * 0.45F + dragProgress * 1.0F;
        Render2DUtility.drawCircle(sliderX + sliderWidth * progressVisual, sliderY + 1.5F, knobRadius, TEXT);
        renderPill(x + width - valueBoxWidth, y + 5.0F, valueBoxWidth, 20.0F, valueText, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW_MOUSE_BUTTON_LEFT || !isSliderHit(mouseX, mouseY)) {
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

    @Override
    public float getHeight() {
        return compactLayout() ? 30.0F : super.getHeight();
    }

    private void renderCompact(int mouseX, int mouseY) {
        String valueText = intValue.getValue() + (intValue.isPercentageMode() ? "%" : "");
        FontRenderer labelFont = font(7.0F);
        FontRenderer valueFont = boldFont(6.5F);
        float valueWidth = valueFont.getStringWidth(valueText);
        float labelHeight = 15.0F;
        labelFont.drawString(
            trimToWidth(labelFont, value.getDisplayName(), Math.max(20.0F, width - valueWidth - 8.0F)),
            x,
            y + centeredTextY(labelHeight, labelFont),
            0xCCFFFFFF
        );
        valueFont.drawString(valueText, x + width - valueWidth,
            y + centeredTextY(labelHeight, valueFont), 0xEBFFFFFF);

        sliderX = x;
        sliderY = y + 20.0F;
        sliderWidth = width;
        float progress = percentage(intValue.getValue(), intValue.getMin(), intValue.getMax());
        boolean hovered = isSliderHit(mouseX, mouseY);
        hoverProgress = animate(hoverProgress, hovered ? 1.0F : 0.0F, 18.0F);
        dragProgress = animate(dragProgress, dragging ? 1.0F : 0.0F, 20.0F);
        progressVisual = animate(progressVisual, progress, dragging ? 30.0F : 14.0F);

        int trackColor = Render2DUtility.mix(0xFF3C3C3C, 0xFF565656, hoverProgress * 0.7F);
        Render2DUtility.drawRoundedRect(sliderX, sliderY, sliderWidth, 5.0F, 2.0F, trackColor);
        Render2DUtility.drawRoundedRect(sliderX, sliderY, sliderWidth * progressVisual, 5.0F, 2.0F, accentColor);
        float knobRadius = 3.0F + hoverProgress * 0.2F + dragProgress * 0.45F;
        Render2DUtility.drawCircle(sliderX + sliderWidth * progressVisual, sliderY + 2.5F, knobRadius, TEXT);
    }

    private boolean isSliderHit(double mouseX, double mouseY) {
        if (compactLayout()) {
            return isInside(mouseX, mouseY, sliderX - 2.0F, sliderY - 4.0F, sliderWidth + 4.0F, 13.0F);
        }
        return isInside(mouseX, mouseY, sliderX - 4.0F, y, sliderWidth + 8.0F, ROW_HEIGHT);
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
