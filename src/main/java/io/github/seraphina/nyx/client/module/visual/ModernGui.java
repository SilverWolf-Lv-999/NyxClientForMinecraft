package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

@ModuleInfo(name = "nyxclient.module.moderngui.name", description = "nyxclient.module.moderngui.description", category = Category.VISUAL)
public class ModernGui extends Module {
    public static final ModernGui INSTANCE = new ModernGui();

    private static final float BAR_WIDTH = 81.0F;
    private static final float BAR_HEIGHT = 9.0F;
    private static final int BACKGROUND = 0xA0000000;
    private static final int BORDER = 0x66000000;
    private static final int HEALTH = 0xFFE53935;
    private static final int HEALTH_FLASH = 0xFFFFFFFF;
    private static final int ABSORPTION = 0xFFFFC928;
    private static final int HEALTH_TEXT = 0xFFFFFFFF;
    private static final int HEALTH_FLASH_TEXT = 0xFF1E1E1E;
    private static final int ABSORPTION_TEXT = 0xFF2A2100;

    public boolean shouldReplaceStatusHearts() {
        return isEnabled();
    }

    public void renderStatusBars(GuiGraphics graphics, Player player, int x, int y, int rowSpacing, int displayHealth, int absorption, boolean flashing) {
        if (!shouldReplaceStatusHearts()) {
            return;
        }

        float health = clamp(player.getHealth(), 0.0F, player.getMaxHealth());
        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        float absorptionAmount = Math.max(0.0F, absorption);

        if (absorptionAmount > 0.0F) {
            renderAbsorptionBar(graphics, x, y - rowSpacing, absorptionAmount);
        }
        renderHealthBar(graphics, x, y, health, maxHealth, displayHealth, flashing);
    }

    private void renderHealthBar(GuiGraphics graphics, float x, float y, float health, float maxHealth, float displayHealth, boolean flashing) {
        drawBarBase(x, y);

        float flashingHealth = clamp(displayHealth, 0.0F, maxHealth);
        boolean showDamageFlash = flashing && flashingHealth > health;
        if (showDamageFlash) {
            Render2DUtility.drawRect(x, y, BAR_WIDTH * clamp(flashingHealth / maxHealth, 0.0F, 1.0F), BAR_HEIGHT, HEALTH_FLASH);
        }

        float fillWidth = BAR_WIDTH * clamp(health / maxHealth, 0.0F, 1.0F);
        Render2DUtility.drawRect(x, y, fillWidth, BAR_HEIGHT, HEALTH);

        String text = formatAmount(health) + "/" + formatAmount(maxHealth);
        drawCenteredText(graphics, text, x, y, showDamageFlash ? HEALTH_FLASH_TEXT : HEALTH_TEXT);
    }

    private void renderAbsorptionBar(GuiGraphics graphics, float x, float y, float absorption) {
        drawBarBase(x, y);
        Render2DUtility.drawRect(x, y, BAR_WIDTH, BAR_HEIGHT, ABSORPTION);
        drawCenteredText(graphics, formatAmount(absorption), x, y, ABSORPTION_TEXT);
    }

    private void drawBarBase(float x, float y) {
        Render2DUtility.drawRect(x, y, BAR_WIDTH, BAR_HEIGHT, BACKGROUND);
        Render2DUtility.drawOutlineRect(x, y, BAR_WIDTH, BAR_HEIGHT, 1.0F, BORDER);
    }

    private void drawCenteredText(GuiGraphics graphics, String text, float x, float y, int color) {
        int textX = Math.round(x + BAR_WIDTH * 0.5F - mc.font.width(text) * 0.5F);
        int textY = Math.round(y + (BAR_HEIGHT - mc.font.lineHeight) * 0.5F);
        graphics.drawString(mc.font, text, textX, textY, color, false);
    }

    private String formatAmount(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
