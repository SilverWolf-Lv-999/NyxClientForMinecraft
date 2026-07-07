package io.github.seraphina.nyx.client.module.visual.hud.component;

import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import io.github.seraphina.nyx.client.ui.UIComponent;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;

public final class WatermarkComponent implements UIComponent {
    private static final String ID = "watermark";
    private static final float HEIGHT = 24.0F;
    private static final float HORIZONTAL_PADDING = 8.0F;
    private static final float GAP = 5.0F;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isVisible() {
        return HUD.INSTANCE.watermark.getValue();
    }

    @Override
    public void render(GuiGraphics graphics, float partialTicks, float scale) {
        FontRenderer titleFont = FontManager.getAppleDisplayRenderer(13.0F);
        float width = width(titleFont);

        Render2DUtility.drawDropShadow(0.0F, 0.0F, width, HEIGHT, 6.0F, 0.0F, 0.0F, 10.0F, 0x80000000);
        Render2DUtility.drawRoundedRect(0.0F, 0.0F, width, HEIGHT, 6.0F, 0xCC0C0D11);
        Render2DUtility.drawOutlineRoundedRect(0.0F, 0.0F, width, HEIGHT, 6.0F, 1.0F, 0x22FFFFFF);
        Render2DUtility.drawRoundedRect(4.0F, 4.0F, 3.0F, HEIGHT - 8.0F, 1.5F, 0xFF3D81F7);

        float titleY = (HEIGHT - titleFont.getLineHeight()) * 0.5F - 0.5F;
        float titleX = HORIZONTAL_PADDING + 5.0F;
        titleFont.drawString(NyxClient.CLIENT_NAME, titleX, titleY, 0xFFFFFFFF);
    }

    @Override
    public AABB getBoundingBox() {
        return new AABB(0.0D, 0.0D, 0.0D, width(FontManager.getAppleDisplayRenderer(13.0F)), HEIGHT, 1.0D);
    }

    private float width(FontRenderer titleFont) {
        return HORIZONTAL_PADDING * 2.0F
                + 5.0F
                + titleFont.getStringWidth(NyxClient.CLIENT_NAME)
                + GAP;
    }
}
