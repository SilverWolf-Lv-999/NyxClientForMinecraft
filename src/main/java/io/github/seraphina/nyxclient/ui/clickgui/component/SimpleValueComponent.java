package io.github.seraphina.nyxclient.ui.clickgui.component;

import io.github.seraphina.nyxclient.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyxclient.value.AbstractValue;

public class SimpleValueComponent extends AbstractComponent {
    public SimpleValueComponent(AbstractValue<?> value) {
        super(value);
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        String text = String.valueOf(value.getValue());
        float pillWidth = Math.min(Math.max(72.0F, font(9.0F).getStringWidth(text) + 18.0F), Math.max(42.0F, width * 0.52F));
        drawLabel(pillWidth + 12.0F);
        renderPill(x + width - pillWidth, y + 5.0F, pillWidth, 20.0F, text, true);
    }
}
