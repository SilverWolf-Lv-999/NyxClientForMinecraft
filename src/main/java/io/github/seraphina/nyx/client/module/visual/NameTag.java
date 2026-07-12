package io.github.seraphina.nyx.client.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.mixins.GuiAccessor;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(name = "nyxclient.module.nametag.name", description = "nyxclient.module.nametag.description", category = Category.VISUAL)
public class NameTag extends Module {
    public static final NameTag INSTANCE = new NameTag();

    private static final float WIDTH_MIN = 72.0F;
    private static final float WIDTH_MAX = 190.0F;
    private static final float NAME_HEIGHT = 18.0F;
    private static final float NAME_PADDING_X = 8.0F;
    private static final float ITEM_SLOT_SIZE = 18.0F;
    private static final float ITEM_GAP = 2.0F;
    private static final float ITEM_ROW_GAP = 3.0F;
    private static final float RADIUS = 5.0F;
    private static final float SCREEN_MARGIN = 3.0F;
    private static final float HEIGHT_OFFSET = 0.55F;
    private static final int BACKGROUND = 0xCC0C0D11;
    private static final int BORDER = 0x22FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HEALTH_GOOD = 0xFF70F58E;
    private static final int HEALTH_WARN = 0xFFFFD166;
    private static final int HEALTH_LOW = 0xFFFF5C66;
    private static final int SLOT_BACKGROUND = 0xAA0C0D11;
    private static final int SLOT_BORDER = 0x26FFFFFF;
    private static final int SHADOW = 0x80000000;
    private static final Comparator<RenderEntry> FAR_TO_NEAR = Comparator.comparingDouble((RenderEntry entry) -> entry.distanceSqr).reversed();

    public final BoolValue allowSelf = ValueBuild.boolSetting("allow self", true,this);

    public final BoolValue showItem = ValueBuild.boolSetting("show item", true,this);

    public final BoolValue showHealth = ValueBuild.boolSetting("show health", true,this);

    private Matrix4f lastModelViewMatrix;
    private Matrix4f lastProjectionMatrix;
    private float lastPartialTick;

    @Override
    public void onDisable() {
        lastModelViewMatrix = null;
        lastProjectionMatrix = null;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        PoseStack.Pose pose = event.getPoseStack().last();
        lastModelViewMatrix = new Matrix4f(pose.pose());
        lastProjectionMatrix = event.getProjectionMatrix();
        lastPartialTick = event.getPartialTick();
    }

    @EventTarget
    public void onRender2D(Render2DEvent.HUD event) {
        if (mc.player == null || mc.level == null || lastModelViewMatrix == null || lastProjectionMatrix == null) {
            return;
        }

        FontRenderer font = font();
        GuiGraphics graphics = event.getGuiGraphics();
        List<RenderEntry> entries = collectEntries(graphics, font);
        if (entries.isEmpty()) {
            return;
        }

        entries.sort(FAR_TO_NEAR);
        Render2DUtility.withGuiGraphics(graphics, () -> {
            for (RenderEntry entry : entries) {
                renderEntry(graphics, font, entry);
            }
        });
    }

    private List<RenderEntry> collectEntries(GuiGraphics graphics, FontRenderer font) {
        List<RenderEntry> entries = new ArrayList<>();
        float screenWidth = graphics.guiWidth();
        float screenHeight = graphics.guiHeight();

        for (AbstractClientPlayer player : mc.level.players()) {
            if (!isValidPlayer(player)) {
                continue;
            }

            Vec3 tagPosition = interpolatedPosition(player, lastPartialTick).add(0.0D, player.getBbHeight() + HEIGHT_OFFSET, 0.0D);
            MathUtility.ScreenPosition screen = MathUtility.worldToScreen(
                    tagPosition,
                    lastModelViewMatrix,
                    lastProjectionMatrix,
                    screenWidth,
                    screenHeight
            );
            if (screen == null) {
                continue;
            }

            RenderEntry entry = buildEntry(player, font, screen, screenWidth, screenHeight);
            if (entry != null) {
                entries.add(entry);
            }
        }

        return entries;
    }

    private boolean isValidPlayer(AbstractClientPlayer player) {
        return player != null
                && player.isAlive()
                && !player.isRemoved()
                && !player.isSpectator()
                && !player.isInvisible()
                && (allowSelf.getValue() || player != mc.player);
    }

    private RenderEntry buildEntry(
            AbstractClientPlayer player,
            FontRenderer font,
            MathUtility.ScreenPosition screen,
            float screenWidth,
            float screenHeight
    ) {
        String name = trimToWidth(font, displayName(player), WIDTH_MAX - NAME_PADDING_X * 2.0F);
        String health = showHealth.getValue() ? healthText(player) : "";
        float nameWidth = font.getStringWidth(name);
        float healthWidth = health.isEmpty() ? 0.0F : font.getStringWidth(health);
        float textGap = health.isEmpty() ? 0.0F : 4.0F;
        float labelWidth = clamp(nameWidth + healthWidth + textGap + NAME_PADDING_X * 2.0F, WIDTH_MIN, WIDTH_MAX);

        List<ItemStack> stacks = showItem.getValue() ? itemStacks(player) : List.of();
        float itemsWidth = itemRowWidth(stacks.size());
        float totalWidth = Math.max(labelWidth, itemsWidth);
        float totalHeight = NAME_HEIGHT + (stacks.isEmpty() ? 0.0F : ITEM_SLOT_SIZE + ITEM_ROW_GAP);
        float x = screen.x() - totalWidth * 0.5F;
        float y = screen.y() - totalHeight;

        if (x > screenWidth - SCREEN_MARGIN || x + totalWidth < SCREEN_MARGIN || y > screenHeight - SCREEN_MARGIN || y + totalHeight < SCREEN_MARGIN) {
            return null;
        }

        x = clamp(x, SCREEN_MARGIN - totalWidth, screenWidth - SCREEN_MARGIN);
        y = clamp(y, SCREEN_MARGIN - totalHeight, screenHeight - SCREEN_MARGIN);

        RenderEntry entry = new RenderEntry();
        entry.player = player;
        entry.name = name;
        entry.nameWidth = nameWidth;
        entry.health = health;
        entry.healthColor = healthColor(player);
        entry.stacks = stacks;
        entry.x = x;
        entry.y = y;
        entry.totalWidth = totalWidth;
        entry.labelWidth = labelWidth;
        entry.itemsWidth = itemsWidth;
        entry.distanceSqr = mc.player.distanceToSqr(player);
        return entry;
    }

    private void renderEntry(GuiGraphics graphics, FontRenderer font, RenderEntry entry) {
        float labelX = entry.x + (entry.totalWidth - entry.labelWidth) * 0.5F;
        float labelY = entry.y + (entry.stacks.isEmpty() ? 0.0F : ITEM_SLOT_SIZE + ITEM_ROW_GAP);

        if (!entry.stacks.isEmpty()) {
            renderItems(graphics, entry);
        }

        Render2DUtility.drawDropShadow(labelX, labelY, entry.labelWidth, NAME_HEIGHT, RADIUS, 0.0F, 0.0F, 9.0F, SHADOW);
        Render2DUtility.drawRoundedRect(labelX, labelY, entry.labelWidth, NAME_HEIGHT, RADIUS, BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(labelX, labelY, entry.labelWidth, NAME_HEIGHT, RADIUS, 1.0F, BORDER);

        float textX = labelX + (entry.labelWidth - entry.nameWidth - (entry.health.isEmpty() ? 0.0F : font.getStringWidth(entry.health) + 4.0F)) * 0.5F;
        float textY = labelY + (NAME_HEIGHT - font.getLineHeight()) * 0.5F - 0.75F;
        font.drawString(entry.name, textX, textY, TEXT);
        if (!entry.health.isEmpty()) {
            font.drawString(entry.health, textX + entry.nameWidth + 4.0F, textY, entry.healthColor);
        }
    }

    private void renderItems(GuiGraphics graphics, RenderEntry entry) {
        float startX = entry.x + (entry.totalWidth - entry.itemsWidth) * 0.5F;
        DeltaTracker deltaTracker = mc.getDeltaTracker();
        GuiAccessor gui = (GuiAccessor)mc.gui;

        for (int index = 0; index < entry.stacks.size(); index++) {
            ItemStack stack = entry.stacks.get(index);
            float slotX = startX + index * (ITEM_SLOT_SIZE + ITEM_GAP);
            renderSlotBackground(slotX, entry.y);
            if (!stack.isEmpty()) {
                gui.nyx$renderSlot(graphics, Math.round(slotX + 1.0F), Math.round(entry.y + 1.0F), deltaTracker, entry.player, stack, index);
            }
        }
    }

    private void renderSlotBackground(float x, float y) {
        Render2DUtility.drawRoundedRect(x, y, ITEM_SLOT_SIZE, ITEM_SLOT_SIZE, 4.0F, SLOT_BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(x, y, ITEM_SLOT_SIZE, ITEM_SLOT_SIZE, 4.0F, 1.0F, SLOT_BORDER);
    }

    private List<ItemStack> itemStacks(AbstractClientPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(6);
        addStack(stacks, player.getMainHandItem());
        addStack(stacks, player.getItemBySlot(EquipmentSlot.HEAD));
        addStack(stacks, player.getItemBySlot(EquipmentSlot.CHEST));
        addStack(stacks, player.getItemBySlot(EquipmentSlot.LEGS));
        addStack(stacks, player.getItemBySlot(EquipmentSlot.FEET));
        addStack(stacks, player.getOffhandItem());
        return stacks;
    }

    private static void addStack(List<ItemStack> stacks, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            stacks.add(stack);
        }
    }

    private String displayName(AbstractClientPlayer player) {
        Component displayName = player.getDisplayName();
        String text = displayName == null ? player.getScoreboardName() : displayName.getString();
        return text == null || text.isBlank() ? "Player" : text;
    }

    private String healthText(AbstractClientPlayer player) {
        return String.valueOf(Math.round(player.getHealth() + player.getAbsorptionAmount()));
    }

    private int healthColor(AbstractClientPlayer player) {
        float ratio = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
        if (ratio <= 0.33F) {
            return HEALTH_LOW;
        }
        if (ratio <= 0.66F) {
            return HEALTH_WARN;
        }
        return HEALTH_GOOD;
    }

    private FontRenderer font() {
        return FontManager.getAppleTextRenderer(10.0F);
    }

    private static Vec3 interpolatedPosition(AbstractClientPlayer player, float partialTick) {
        double x = Mth.lerp(partialTick, player.xOld, player.getX());
        double y = Mth.lerp(partialTick, player.yOld, player.getY());
        double z = Mth.lerp(partialTick, player.zOld, player.getZ());
        return new Vec3(x, y, z);
    }

    private static float itemRowWidth(int stackCount) {
        if (stackCount <= 0) {
            return 0.0F;
        }
        return stackCount * ITEM_SLOT_SIZE + (stackCount - 1) * ITEM_GAP;
    }

    private static String trimToWidth(FontRenderer font, String text, float maxWidth) {
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        float ellipsisWidth = font.getStringWidth(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }

        int end = text.length();
        while (end > 0 && font.getStringWidth(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class RenderEntry {
        private AbstractClientPlayer player;
        private String name;
        private float nameWidth;
        private String health;
        private int healthColor;
        private List<ItemStack> stacks;
        private float x;
        private float y;
        private float totalWidth;
        private float labelWidth;
        private float itemsWidth;
        private double distanceSqr;
    }
}
