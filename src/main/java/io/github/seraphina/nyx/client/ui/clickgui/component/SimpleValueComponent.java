package io.github.seraphina.nyx.client.ui.clickgui.component;

import io.github.seraphina.nyx.client.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.value.AbstractValue;

public class SimpleValueComponent extends AbstractComponent {
    private float hoverProgress;

    public SimpleValueComponent(AbstractValue<?> value) {
        super(value);
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        String text = String.valueOf(value.getValue());
        if (compactLayout()) {
            var labelFont = font(7.0F);
            var valueFont = boldFont(6.5F);
            float valueWidth = Math.min(width * 0.48F, valueFont.getStringWidth(text));
            boolean hovered = isInside(mouseX, mouseY, x, y, width, getHeight());
            hoverProgress = animate(hoverProgress, hovered ? 1.0F : 0.0F, 18.0F);
            labelFont.drawString(
                trimToWidth(labelFont, value.getDisplayName(), Math.max(20.0F, width - valueWidth - 8.0F)),
                x,
                y + centeredTextY(getHeight(), labelFont),
                Render2DUtility.mix(0xCCFFFFFF, TEXT, hoverProgress * 0.25F)
            );
            String valueText = trimToWidth(valueFont, text, width * 0.48F);
            valueFont.drawString(valueText, x + width - valueFont.getStringWidth(valueText),
                y + centeredTextY(getHeight(), valueFont), accentColor);
            return;
        }

        float pillWidth = Math.min(Math.max(72.0F, font(9.0F).getStringWidth(text) + 18.0F), Math.max(42.0F, width * 0.52F));
        boolean hovered = isInside(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        hoverProgress = animate(hoverProgress, hovered ? 1.0F : 0.0F, 18.0F);
        if (hoverProgress > 0.0F) {
            Render2DUtility.drawRoundedRect(x - 4.0F, y + 3.0F, width + 8.0F, ROW_HEIGHT - 6.0F, 5.0F,
                Render2DUtility.applyOpacity(HOVER, hoverProgress));
        }
        drawLabel(pillWidth + 12.0F);
        renderPill(x + width - pillWidth, y + 5.0F, pillWidth, 20.0F, text, true);
    }
}
