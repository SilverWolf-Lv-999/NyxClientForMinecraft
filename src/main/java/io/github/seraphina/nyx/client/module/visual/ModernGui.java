package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

@ModuleInfo(name = "nyxclient.module.moderngui.name", description = "nyxclient.module.moderngui.description", category = Category.VISUAL)
public class ModernGui extends Module {
    public static final ModernGui INSTANCE = new ModernGui();

    private static final float BAR_WIDTH = 81.0F;
    private static final float BAR_ROW_HEIGHT = 9.0F;
    private static final float BAR_SCALE = 0.7F;
    private static final float BAR_HEIGHT = BAR_ROW_HEIGHT * BAR_SCALE;
    private static final float TEXT_SCALE = BAR_SCALE;
    private static final int BACKGROUND = 0xA0000000;
    private static final int BORDER = 0x66000000;
    private static final int HEALTH = 0xFFE53935;
    private static final int HEALTH_FLASH = 0xFFFFFFFF;
    private static final int ABSORPTION = 0xFFFFC928;
    private static final int ARMOR = 0xFF90A4AE;
    private static final int FOOD = 0xFFFF8F00;
    private static final int HUNGER = 0xFF7CB342;
    private static final int HEALTH_TEXT = 0xFFFFFFFF;
    private static final int HEALTH_FLASH_TEXT = 0xFF1E1E1E;
    private static final int ABSORPTION_TEXT = 0xFF2A2100;
    private static final int ARMOR_TEXT = 0xFF101820;
    private static final int FOOD_TEXT = 0xFF2A1500;
    private static final int HUNGER_TEXT = 0xFF112006;
    private static final float MAX_ARMOR = 20.0F;
    private static final float MAX_FOOD = 20.0F;

    public boolean shouldReplaceStatusHearts() {
        return shouldReplaceStatusBars();
    }

    public boolean shouldReplaceStatusBars() {
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
        float barY = centeredBarY(y);
        drawBarBase(x, barY);

        float flashingHealth = clamp(displayHealth, 0.0F, maxHealth);
        boolean showDamageFlash = flashing && flashingHealth > health;
        if (showDamageFlash) {
            Render2DUtility.drawRect(x, barY, BAR_WIDTH * clamp(flashingHealth / maxHealth, 0.0F, 1.0F), BAR_HEIGHT, HEALTH_FLASH);
        }

        float fillWidth = BAR_WIDTH * clamp(health / maxHealth, 0.0F, 1.0F);
        Render2DUtility.drawRect(x, barY, fillWidth, BAR_HEIGHT, HEALTH);

        String text = formatAmount(health) + "/" + formatAmount(maxHealth);
        drawCenteredText(graphics, text, x, barY, showDamageFlash ? HEALTH_FLASH_TEXT : HEALTH_TEXT);
    }

    private void renderAbsorptionBar(GuiGraphics graphics, float x, float y, float absorption) {
        float barY = centeredBarY(y);
        drawBarBase(x, barY);
        Render2DUtility.drawRect(x, barY, BAR_WIDTH, BAR_HEIGHT, ABSORPTION);
        drawCenteredText(graphics, formatAmount(absorption), x, barY, ABSORPTION_TEXT);
    }

    public void renderArmorBar(GuiGraphics graphics, Player player, int x, int y) {
        if (!shouldReplaceStatusBars()) {
            return;
        }

        int armor = player.getArmorValue();
        if (armor <= 0) {
            return;
        }

        renderAmountBar(graphics, x, y, armor, MAX_ARMOR, ARMOR, ARMOR_TEXT);
    }

    public void renderFoodBar(GuiGraphics graphics, Player player, int rightEdge, int y) {
        if (!shouldReplaceStatusBars()) {
            return;
        }

        int food = player.getFoodData().getFoodLevel();
        boolean hunger = player.hasEffect(MobEffects.HUNGER);
        renderAmountBar(graphics, rightEdge - BAR_WIDTH, y, food, MAX_FOOD, hunger ? HUNGER : FOOD, hunger ? HUNGER_TEXT : FOOD_TEXT);
    }

    private void renderAmountBar(GuiGraphics graphics, float x, float y, float value, float maxValue, int fillColor, int textColor) {
        float barY = centeredBarY(y);
        drawBarBase(x, barY);
        Render2DUtility.drawRect(x, barY, BAR_WIDTH * clamp(value / maxValue, 0.0F, 1.0F), BAR_HEIGHT, fillColor);
        drawCenteredText(graphics, formatAmount(value) + "/" + formatAmount(maxValue), x, barY, textColor);
    }

    private void drawBarBase(float x, float y) {
        Render2DUtility.drawRect(x, y, BAR_WIDTH, BAR_HEIGHT, BACKGROUND);
        Render2DUtility.drawOutlineRect(x, y, BAR_WIDTH, BAR_HEIGHT, 1.0F, BORDER);
    }

    private void drawCenteredText(GuiGraphics graphics, String text, float x, float y, int color) {
        float textWidth = mc.font.width(text) * TEXT_SCALE;
        float textHeight = mc.font.lineHeight * TEXT_SCALE;
        float textX = x + BAR_WIDTH * 0.5F - textWidth * 0.5F;
        float textY = y + BAR_HEIGHT * 0.5F - textHeight * 0.5F;

        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(textX, textY);
            graphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
            graphics.drawString(mc.font, text, 0, 0, color, false);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private float centeredBarY(float y) {
        return y + (BAR_ROW_HEIGHT - BAR_HEIGHT) * 0.5F;
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
