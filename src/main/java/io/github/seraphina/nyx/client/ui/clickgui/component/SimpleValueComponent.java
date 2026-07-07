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
