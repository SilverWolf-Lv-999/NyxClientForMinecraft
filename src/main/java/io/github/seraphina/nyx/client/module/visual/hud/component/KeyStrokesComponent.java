package io.github.seraphina.nyx.client.module.visual.hud.component;

import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.module.visual.KeyStrokes;
import io.github.seraphina.nyx.client.ui.UIComponent;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;

public final class KeyStrokesComponent implements UIComponent<KeyStrokes> {
    private static final String ID = "keystrokes";
    private static final float KEY_SIZE = 24.0F;
    private static final float MOUSE_KEY_HEIGHT = 20.0F;
    private static final float SPACE_KEY_HEIGHT = 18.0F;
    private static final float GAP = 3.0F;
    private static final float WIDTH = KEY_SIZE * 3.0F + GAP * 2.0F;
    private static final float HEIGHT = KEY_SIZE * 2.0F + MOUSE_KEY_HEIGHT + SPACE_KEY_HEIGHT + GAP * 3.0F;
    private static final float RADIUS = 5.0F;
    private static final int BACKGROUND = 0xCC0C0D11;
    private static final int KEY_BACKGROUND = 0x66141622;
    private static final int KEY_BACKGROUND_DOWN = 0xAA213B6F;
    private static final int BORDER = 0x22FFFFFF;
    private static final int BORDER_DOWN = 0xAA3D81F7;
    private static final int TEXT = 0xFFEDEFF7;
    private static final int TEXT_DOWN = 0xFFFFFFFF;
    private static final int SHADOW = 0x80000000;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public float getDefaultX() {
        return 8.0F;
    }

    @Override
    public float getDefaultY() {
        if (mc.getWindow() == null) {
            return 96.0F;
        }
        return Math.max(8.0F, mc.getWindow().getGuiScaledHeight() - HEIGHT - 64.0F);
    }

    @Override
    public void render(GuiGraphics graphics, float partialTicks, float scale) {
        FontRenderer keyFont = FontManager.getAppleDisplayRenderer(13.0F);
        FontRenderer smallFont = FontManager.getAppleTextRenderer(10.5F);

        Render2DUtility.drawDropShadow(0.0F, 0.0F, WIDTH, HEIGHT, RADIUS, 0.0F, 2.0F, 10.0F, SHADOW);
        Render2DUtility.drawRoundedRect(0.0F, 0.0F, WIDTH, HEIGHT, RADIUS, BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(0.0F, 0.0F, WIDTH, HEIGHT, RADIUS, 1.0F, BORDER);

        renderKey(keyFont, "W", KEY_SIZE + GAP, 0.0F, KEY_SIZE, KEY_SIZE, mc.options.keyUp);
        renderKey(keyFont, "A", 0.0F, KEY_SIZE + GAP, KEY_SIZE, KEY_SIZE, mc.options.keyLeft);
        renderKey(keyFont, "S", KEY_SIZE + GAP, KEY_SIZE + GAP, KEY_SIZE, KEY_SIZE, mc.options.keyDown);
        renderKey(keyFont, "D", (KEY_SIZE + GAP) * 2.0F, KEY_SIZE + GAP, KEY_SIZE, KEY_SIZE, mc.options.keyRight);

        float mouseY = KEY_SIZE * 2.0F + GAP * 2.0F;
        float mouseWidth = (WIDTH - GAP) * 0.5F;
        renderKey(smallFont, "LMB", 0.0F, mouseY, mouseWidth, MOUSE_KEY_HEIGHT, mc.options.keyAttack);
        renderKey(smallFont, "RMB", mouseWidth + GAP, mouseY, mouseWidth, MOUSE_KEY_HEIGHT, mc.options.keyUse);

        renderKey(smallFont, "SPACE", 0.0F, mouseY + MOUSE_KEY_HEIGHT + GAP, WIDTH, SPACE_KEY_HEIGHT, mc.options.keyJump);
    }

    @Override
    public AABB getBoundingBox() {
        return new AABB(0.0D, 0.0D, 0.0D, WIDTH, HEIGHT, 1.0D);
    }

    @Override
    public boolean isVisible() {
        return KeyStrokes.INSTANCE.isEnabled();
    }

    private void renderKey(FontRenderer font, String label, float x, float y, float width, float height, KeyMapping key) {
        boolean down = key.isDown();
        int fillColor = down ? KEY_BACKGROUND_DOWN : KEY_BACKGROUND;
        int borderColor = down ? BORDER_DOWN : BORDER;
        int textColor = down ? TEXT_DOWN : TEXT;

        Render2DUtility.drawRoundedRect(x, y, width, height, 4.0F, fillColor);
        Render2DUtility.drawOutlineRoundedRect(x, y, width, height, 4.0F, 1.0F, borderColor);
        font.drawCenteredString(label, x + width * 0.5F, y + (height - font.getLineHeight()) * 0.5F - 0.5F, textColor);
    }
}
