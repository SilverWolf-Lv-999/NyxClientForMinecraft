package io.github.seraphina.nyx.client.module.visual.hud.component;

import io.github.seraphina.nyx.client.mixins.GuiAccessor;
import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import io.github.seraphina.nyx.client.ui.UIComponent;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class InventoryComponent implements UIComponent<HUD> {
    private static final String ID = "inventory";
    private static final int COLUMNS = 9;
    private static final int ROWS = 3;
    private static final int SLOT_COUNT = COLUMNS * ROWS;
    private static final float SLOT_SIZE = 18.0F;
    private static final float SLOT_GAP = 2.0F;
    private static final float PADDING = 6.0F;
    private static final float RADIUS = 6.0F;
    private static final float WIDTH = PADDING * 2.0F + COLUMNS * SLOT_SIZE + (COLUMNS - 1) * SLOT_GAP;
    private static final float HEIGHT = PADDING * 2.0F + ROWS * SLOT_SIZE + (ROWS - 1) * SLOT_GAP;
    private static final int BACKGROUND = 0xCC0C0D11;
    private static final int BORDER = 0x22FFFFFF;
    private static final int SLOT_EMPTY = 0x44000000;
    private static final int SLOT_FILLED = 0x66141622;
    private static final int SLOT_BORDER = 0x1EFFFFFF;
    private static final int SHADOW = 0x80000000;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isVisible() {
        return HUD.INSTANCE.inventory.getValue();
    }

    @Override
    public float getDefaultX() {
        if (mc.getWindow() == null) {
            return 8.0F;
        }
        return (mc.getWindow().getGuiScaledWidth() - WIDTH) * 0.5F;
    }

    @Override
    public float getDefaultY() {
        if (mc.getWindow() == null) {
            return 80.0F;
        }
        return Math.max(8.0F, mc.getWindow().getGuiScaledHeight() - HEIGHT - 58.0F);
    }

    @Override
    public void render(GuiGraphics graphics, float partialTicks, float scale) {
        Player player = mc.player;
        if (player == null) {
            return;
        }

        Render2DUtility.drawDropShadow(0.0F, 0.0F, WIDTH, HEIGHT, RADIUS, 0.0F, 2.0F, 10.0F, SHADOW);
        Render2DUtility.drawRoundedRect(0.0F, 0.0F, WIDTH, HEIGHT, RADIUS, BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(0.0F, 0.0F, WIDTH, HEIGHT, RADIUS, 1.0F, BORDER);

        for (int index = 0; index < SLOT_COUNT; index++) {
            int inventorySlot = InventoryUtility.MAIN_INVENTORY_START + index;
            ItemStack stack = player.getInventory().getItem(inventorySlot);
            float slotX = slotX(index % COLUMNS);
            float slotY = slotY(index / COLUMNS);
            renderSlotBackground(slotX, slotY, !stack.isEmpty());
        }

        DeltaTracker deltaTracker = mc.getDeltaTracker();
        GuiAccessor gui = (GuiAccessor)mc.gui;
        for (int index = 0; index < SLOT_COUNT; index++) {
            int inventorySlot = InventoryUtility.MAIN_INVENTORY_START + index;
            ItemStack stack = player.getInventory().getItem(inventorySlot);
            if (stack.isEmpty()) {
                continue;
            }

            int itemX = Math.round(slotX(index % COLUMNS) + 1.0F);
            int itemY = Math.round(slotY(index / COLUMNS) + 1.0F);
            gui.nyx$renderSlot(graphics, itemX, itemY, deltaTracker, player, stack, inventorySlot);
        }
    }

    @Override
    public AABB getBoundingBox() {
        return new AABB(0.0D, 0.0D, 0.0D, WIDTH, HEIGHT, 1.0D);
    }

    private void renderSlotBackground(float x, float y, boolean hasItem) {
        Render2DUtility.drawRoundedRect(x, y, SLOT_SIZE, SLOT_SIZE, 4.0F, hasItem ? SLOT_FILLED : SLOT_EMPTY);
        Render2DUtility.drawOutlineRoundedRect(x, y, SLOT_SIZE, SLOT_SIZE, 4.0F, 1.0F, SLOT_BORDER);
    }

    private static float slotX(int column) {
        return PADDING + column * (SLOT_SIZE + SLOT_GAP);
    }

    private static float slotY(int row) {
        return PADDING + row * (SLOT_SIZE + SLOT_GAP);
    }
}
