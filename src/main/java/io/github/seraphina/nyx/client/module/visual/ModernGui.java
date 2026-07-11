package io.github.seraphina.nyx.client.module.visual;

import com.google.common.collect.Ordering;
import com.mojang.authlib.GameProfile;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.StringUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(name = "nyxclient.module.moderngui.name", description = "nyxclient.module.moderngui.description", category = Category.VISUAL)
public class ModernGui extends Module {
    public static final ModernGui INSTANCE = new ModernGui();

    private static final float BAR_WIDTH = 82.0F;
    private static final float BAR_ROW_HEIGHT = 9.0F;
    private static final float BAR_HEIGHT = 9.0F;
    private static final float BAR_RADIUS = 3.0F;
    private static final float BAR_INSET = 1.5F;
    private static final float STATUS_BAR_BOTTOM = 30.0F;
    private static final float STATUS_CONTEXTUAL_BAR_GAP = 3.0F;
    private static final int BACKGROUND = 0xCC0C0D11;
    private static final int BORDER = 0x22FFFFFF;
    private static final int SHADOW = 0x74000000;
    private static final int TRACK = 0x66000000;
    private static final int HEALTH = 0xFFFF6373;
    private static final int HEALTH_DARK = 0xFFE63B4F;
    private static final int HEALTH_FLASH = 0xFFFFFFFF;
    private static final int ABSORPTION = 0xFFFFD36A;
    private static final int ABSORPTION_DARK = 0xFFFFAA2B;
    private static final int ARMOR = 0xFF57C7FF;
    private static final int ARMOR_DARK = 0xFF3D81F7;
    private static final int FOOD = 0xFFFFB24A;
    private static final int FOOD_DARK = 0xFFFF7A45;
    private static final int HUNGER = 0xFF8FE35D;
    private static final int HUNGER_DARK = 0xFF53E08C;
    private static final int AIR = 0xFF6AE8FF;
    private static final int AIR_DARK = 0xFF2F9BFF;
    private static final int HEALTH_TEXT = 0xFFFFFFFF;
    private static final int HEALTH_FLASH_TEXT = 0xFF14161D;
    private static final int DARK_TEXT = 0xFF14161D;
    private static final float MAX_ARMOR = 20.0F;
    private static final float MAX_FOOD = 20.0F;
    private static final float MAX_AIR_BUBBLES = 10.0F;
    private static final float HOTBAR_WIDTH = 182.0F;
    private static final float HOTBAR_HEIGHT = 24.0F;
    private static final float HOTBAR_RADIUS = 6.0F;
    private static final float HOTBAR_SLOT_SIZE = 20.0F;
    private static final float HOTBAR_SLOT_GAP = 0.0F;
    private static final float HOTBAR_SELECTED_SIZE = 24.0F;
    private static final float HOTBAR_OFFHAND_WIDTH = 26.0F;
    private static final float HOTBAR_OFFHAND_GAP = 4.0F;
    private static final int HOTBAR_SLOT = 0x22000000;
    private static final int HOTBAR_SLOT_BORDER = 0x18FFFFFF;
    private static final int HOTBAR_SELECTED = 0x333D81F7;
    private static final int HOTBAR_SELECTED_BORDER = 0xCC3D81F7;
    private static final int HOTBAR_ACCENT = 0xFF3D81F7;
    private static final int HOTBAR_ATTACK = 0xFFFFD36A;
    private static final int HOTBAR_ATTACK_TRACK = 0x66000000;
    private static final float CONTEXT_BAR_WIDTH = 182.0F;
    private static final float CONTEXT_BAR_HEIGHT = 9.0F;
    private static final float CONTEXT_BAR_RADIUS = 4.0F;
    private static final float CONTEXT_BAR_INSET = 1.5F;
    private static final float CONTEXT_BAR_BOTTOM = 24.0F;
    private static final int CONTEXT_TRACK = 0x72000000;
    private static final int CONTEXT_TICK = 0x18FFFFFF;
    private static final int EXP_START = 0xFF9BFF6A;
    private static final int EXP_END = 0xFF37C76A;
    private static final int EXP_GLOW = 0x5537C76A;
    private static final int LOCATOR_START = 0xFF6AE8FF;
    private static final int LOCATOR_END = 0xFF8C6DFF;
    private static final int LOCATOR_CENTER = 0xAAFFFFFF;
    private static final int LOCATOR_DOT_BACKDROP = 0xB00C0D11;
    private static final Identifier LOCATOR_BAR_ARROW_UP = Identifier.withDefaultNamespace("hud/locator_bar_arrow_up");
    private static final Identifier LOCATOR_BAR_ARROW_DOWN = Identifier.withDefaultNamespace("hud/locator_bar_arrow_down");
    private static final int LOCATOR_DOT_SIZE = 9;
    private static final int LOCATOR_VISIBLE_DEGREES = 60;
    private static final float SCOREBOARD_SCALE = 0.7F;
    private static final float SCOREBOARD_MARGIN = 8.0F;
    private static final float SCOREBOARD_RADIUS = 6.0F;
    private static final float SCOREBOARD_HEADER_HEIGHT = 12.0F;
    private static final float SCOREBOARD_ROW_HEIGHT = 10.0F;
    private static final float SCOREBOARD_PADDING_TOP = 7.0F;
    private static final float SCOREBOARD_PADDING_RIGHT = 9.0F;
    private static final float SCOREBOARD_PADDING_BOTTOM = 7.0F;
    private static final float SCOREBOARD_TEXT_X = 9.0F;
    private static final float SCOREBOARD_ENTRY_GAP = 2.0F;
    private static final float SCOREBOARD_MIN_WIDTH = 88.0F;
    private static final float SCOREBOARD_MAX_WIDTH_RATIO = 0.45F;
    private static final int SCOREBOARD_TEXT = 0xFFFFFFFF;
    private static final int SCOREBOARD_SCORE_TEXT = 0xFFD0D4DE;
    private static final int SCOREBOARD_DIVIDER = 0x55FFFFFF;
    private static final String SCOREBOARD_BRAND_TEXT = "Nyx Client";
    private static final int SCOREBOARD_BRAND_COLOR = 0xFFFFFFFF;
    private static final int SCOREBOARD_BRAND_CYAN = 0xFF50F5FF;
    private static final int SCOREBOARD_BRAND_MAGENTA = 0xFFFF4FD8;
    private static final int SCOREBOARD_BRAND_DIM = 0xCCD7DEFF;
    private static final long SCOREBOARD_GLITCH_FRAME_MS = 32L;
    private static final long SCOREBOARD_GLITCH_PHASES = 10L;
    private static final Pattern SCOREBOARD_SERVER_ADDRESS_CANDIDATE = Pattern.compile(
        "(?i)(?<![\\w.-])(?:(?:\\d{1,3}\\.){3}\\d{1,3}|[a-z0-9][a-z0-9.-]*\\.[a-z0-9.-]+)(?::\\d{1,5})?(?![\\w.-])"
    );
    private static final float EFFECT_WIDTH = 138.0F;
    private static final float EFFECT_HEIGHT = 24.0F;
    private static final float EFFECT_RADIUS = 6.0F;
    private static final float EFFECT_LEFT_BASE = 14.0F;
    private static final float EFFECT_LEFT_STEP = 3.25F;
    private static final float EFFECT_ROW_STEP = 15.0F;
    private static final float EFFECT_ICON_LEFT = 12.0F;
    private static final float EFFECT_ICON_SIZE = 14.0F;
    private static final float EFFECT_ICON_GAP = 6.0F;
    private static final float EFFECT_TEXT_RIGHT = 9.0F;
    private static final float EFFECT_SIZE_SCALE = 0.6F;
    private static final float EFFECT_MAX_SCALE = 0.89F;
    private static final float EFFECT_MIN_SCALE = 0.62F;
    private static final float EFFECT_SCALE_STEP = 0.11F;
    private static final int EFFECT_TEXT = 0xFFFFFFFF;
    private static final int EFFECT_DURATION_TEXT = 0xFFD0D4DE;
    private static final int EFFECT_TOO_LONG_TICKS = 32147;
    private static final float TAB_FONT_SIZE = 9.0F;
    private static final float TAB_MARGIN_TOP = 10.0F;
    private static final float TAB_PADDING_X = 8.0F;
    private static final float TAB_PADDING_TOP = 7.0F;
    private static final float TAB_PADDING_BOTTOM = 7.0F;
    private static final float TAB_RADIUS = 6.0F;
    private static final float TAB_ROW_HEIGHT = 13.0F;
    private static final float TAB_ROW_GAP = 2.0F;
    private static final float TAB_COLUMN_GAP = 5.0F;
    private static final float TAB_HEAD_SIZE = 9.0F;
    private static final float TAB_HEAD_GAP = 4.0F;
    private static final float TAB_PING_WIDTH = 12.0F;
    private static final float TAB_TEXT_GAP = 5.0F;
    private static final float TAB_MIN_COLUMN_WIDTH = 78.0F;
    private static final float TAB_MAX_WIDTH_INSET = 50.0F;
    private static final float TAB_MESSAGE_GAP = 6.0F;
    private static final float TAB_MESSAGE_LINE_GAP = 1.0F;
    private static final float TAB_SCORE_HEART_WIDTH = 45.0F;
    private static final float TAB_SCORE_HEART_HEIGHT = 5.0F;
    private static final int TAB_ROW_BACKGROUND = 0x22000000;
    private static final int TAB_ROW_BORDER = 0x14FFFFFF;
    private static final int TAB_TEXT = 0xFFFFFFFF;
    private static final int TAB_SPECTATOR_TEXT = 0x99FFFFFF;
    private static final int TAB_SCORE_TEXT = 0xFFD0D4DE;
    private static final int TAB_DIVIDER = 0x33FFFFFF;
    private static final Identifier PING_UNKNOWN_SPRITE = Identifier.withDefaultNamespace("icon/ping_unknown");
    private static final Identifier PING_1_SPRITE = Identifier.withDefaultNamespace("icon/ping_1");
    private static final Identifier PING_2_SPRITE = Identifier.withDefaultNamespace("icon/ping_2");
    private static final Identifier PING_3_SPRITE = Identifier.withDefaultNamespace("icon/ping_3");
    private static final Identifier PING_4_SPRITE = Identifier.withDefaultNamespace("icon/ping_4");
    private static final Identifier PING_5_SPRITE = Identifier.withDefaultNamespace("icon/ping_5");
    private static final Comparator<PlayerScoreEntry> SCORE_DISPLAY_ORDER = Comparator.comparing(PlayerScoreEntry::value)
        .reversed()
        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    public final BoolValue statusBars = ValueBuild.boolSetting("status bars", true, this);
    public final BoolValue hotbar = ValueBuild.boolSetting("hotbar", true, this);
    public final BoolValue contextualBar = ValueBuild.boolSetting("contextual bar", true, this);
    public final BoolValue scoreboard = ValueBuild.boolSetting("scoreboard", true, this);
    public final BoolValue potionEffects = ValueBuild.boolSetting("potion effects", true, this);
    public final BoolValue tabList = ValueBuild.boolSetting("tab list", true, this);

    public boolean shouldReplaceStatusHearts() {
        return shouldReplaceStatusBars();
    }

    public boolean shouldReplaceStatusBars() {
        return isEnabled() && statusBars.getValue();
    }

    public boolean shouldReplaceHotbar() {
        return isEnabled() && hotbar.getValue();
    }

    public boolean shouldReplaceContextualBar() {
        return isEnabled() && contextualBar.getValue();
    }

    public boolean shouldRenderModernContextualBar() {
        return shouldReplaceContextualBar() && contextualBarMode() != ContextualBarMode.NONE;
    }

    public boolean shouldReplaceExperienceLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        return shouldReplaceContextualBar()
            && minecraft.gameMode != null
            && minecraft.player != null
            && minecraft.gameMode.hasExperience()
            && minecraft.player.experienceLevel > 0;
    }

    public boolean shouldReplaceScoreboard() {
        return isEnabled() && scoreboard.getValue();
    }

    public boolean shouldReplacePotionEffects() {
        Minecraft minecraft = Minecraft.getInstance();
        return isEnabled()
            && potionEffects.getValue()
            && minecraft.player != null
            && (minecraft.screen == null || !minecraft.screen.showsActiveEffects());
    }

    public boolean shouldReplaceTabList() {
        return isEnabled() && tabList.getValue();
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
        float barY = statusBarY(graphics, y);
        drawBarBase(x, barY, HEALTH_DARK);

        float flashingHealth = clamp(displayHealth, 0.0F, maxHealth);
        boolean showDamageFlash = flashing && flashingHealth > health;
        if (showDamageFlash) {
            drawBarFill(x, barY, flashingHealth / maxHealth, HEALTH_FLASH, HEALTH_FLASH);
        }

        drawBarFill(x, barY, health / maxHealth, HEALTH, HEALTH_DARK);

        String text = formatAmount(health) + "/" + formatAmount(maxHealth);
        drawCenteredText(text, x, barY, showDamageFlash ? HEALTH_FLASH_TEXT : HEALTH_TEXT);
    }

    private void renderAbsorptionBar(GuiGraphics graphics, float x, float y, float absorption) {
        float barY = statusBarY(graphics, y);
        drawBarBase(x, barY, ABSORPTION_DARK);
        drawBarFill(x, barY, 1.0F, ABSORPTION, ABSORPTION_DARK);
        drawCenteredText(formatAmount(absorption), x, barY, DARK_TEXT);
    }

    public void renderArmorBar(GuiGraphics graphics, Player player, int x, int y) {
        if (!shouldReplaceStatusBars()) {
            return;
        }

        int armor = player.getArmorValue();
        if (armor <= 0) {
            return;
        }

        renderAmountBar(graphics, x, y, armor, MAX_ARMOR, ARMOR, ARMOR_DARK, HEALTH_TEXT);
    }

    public void renderFoodBar(GuiGraphics graphics, Player player, int rightEdge, int y) {
        if (!shouldReplaceStatusBars()) {
            return;
        }

        int food = player.getFoodData().getFoodLevel();
        boolean hunger = player.hasEffect(MobEffects.HUNGER);
        renderAmountBar(
            graphics,
            rightEdge - BAR_WIDTH,
            y,
            food,
            MAX_FOOD,
            hunger ? HUNGER : FOOD,
            hunger ? HUNGER_DARK : FOOD_DARK,
            DARK_TEXT
        );
    }

    public boolean shouldRenderAirBar(Player player) {
        int maxAir = Math.max(1, player.getMaxAirSupply());
        int air = Math.max(0, Math.min(player.getAirSupply(), maxAir));
        return player.isEyeInFluid(FluidTags.WATER) || air < maxAir;
    }

    public void renderAirBar(GuiGraphics graphics, Player player, int rightEdge, int y) {
        if (!shouldReplaceStatusBars() || !shouldRenderAirBar(player)) {
            return;
        }

        int maxAir = Math.max(1, player.getMaxAirSupply());
        int air = Math.max(0, Math.min(player.getAirSupply(), maxAir));
        float airBubbles = (float)air * MAX_AIR_BUBBLES / (float)maxAir;
        renderAmountBar(graphics, rightEdge - BAR_WIDTH, y, airBubbles, MAX_AIR_BUBBLES, AIR, AIR_DARK, DARK_TEXT);
    }

    private void renderAmountBar(GuiGraphics graphics, float x, float y, float value, float maxValue, int fillColor, int fillEndColor, int textColor) {
        float barY = statusBarY(graphics, y);
        drawBarBase(x, barY, fillEndColor);
        drawBarFill(x, barY, value / maxValue, fillColor, fillEndColor);
        drawCenteredText(formatAmount(value) + "/" + formatAmount(maxValue), x, barY, textColor);
    }

    public void renderHotbarBase(int centerX, int screenHeight, int selectedSlot, boolean offhandLeft, boolean hasOffhand) {
        if (!shouldReplaceHotbar()) {
            return;
        }

        float x = centerX - HOTBAR_WIDTH * 0.5F;
        float y = hotbarY(screenHeight);

        Render2DUtility.drawDropShadow(x, y, HOTBAR_WIDTH, HOTBAR_HEIGHT, HOTBAR_RADIUS, 0.0F, 1.0F, 12.0F, SHADOW);
        Render2DUtility.drawRoundedRect(x, y, HOTBAR_WIDTH, HOTBAR_HEIGHT, HOTBAR_RADIUS, BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(x, y, HOTBAR_WIDTH, HOTBAR_HEIGHT, HOTBAR_RADIUS, 1.0F, BORDER);

        for (int slot = 0; slot < 9; slot++) {
            float slotX = slotCellX(centerX, slot);
            float slotY = y + 2.0F;
            Render2DUtility.drawRoundedRect(slotX, slotY, HOTBAR_SLOT_SIZE, HOTBAR_SLOT_SIZE, 4.0F, HOTBAR_SLOT);
            Render2DUtility.drawOutlineRoundedRect(slotX, slotY, HOTBAR_SLOT_SIZE, HOTBAR_SLOT_SIZE, 4.0F, 1.0F, HOTBAR_SLOT_BORDER);
        }

        renderSelectedHotbarSlot(centerX, screenHeight, selectedSlot);
        if (hasOffhand) {
            renderOffhandSlot(centerX, screenHeight, offhandLeft);
        }
    }

    public void renderSelectedHotbarSlot(int centerX, int screenHeight, int selectedSlot) {
        if (selectedSlot < 0 || selectedSlot >= 9) {
            return;
        }

        float slotX = slotCellX(centerX, selectedSlot) - 2.0F;
        float slotY = hotbarY(screenHeight);
        Render2DUtility.drawDropShadow(slotX, slotY, HOTBAR_SELECTED_SIZE, HOTBAR_SELECTED_SIZE, HOTBAR_RADIUS, 0.0F, 1.0F, 9.0F, 0x553D81F7);
        Render2DUtility.drawRoundedRect(slotX, slotY, HOTBAR_SELECTED_SIZE, HOTBAR_SELECTED_SIZE, HOTBAR_RADIUS, HOTBAR_SELECTED);
        Render2DUtility.drawOutlineRoundedRect(slotX, slotY, HOTBAR_SELECTED_SIZE, HOTBAR_SELECTED_SIZE, HOTBAR_RADIUS, 1.0F, HOTBAR_SELECTED_BORDER);
        Render2DUtility.drawRoundedRect(slotX + 6.0F, slotY + HOTBAR_SELECTED_SIZE - 4.0F, HOTBAR_SELECTED_SIZE - 12.0F, 2.0F, 1.0F, HOTBAR_ACCENT);
    }

    public void renderOffhandSlot(int centerX, int screenHeight, boolean left) {
        float x = left
            ? centerX - HOTBAR_WIDTH * 0.5F - HOTBAR_OFFHAND_GAP - HOTBAR_OFFHAND_WIDTH
            : centerX + HOTBAR_WIDTH * 0.5F + HOTBAR_OFFHAND_GAP;
        float y = hotbarY(screenHeight);

        Render2DUtility.drawDropShadow(x, y, HOTBAR_OFFHAND_WIDTH, HOTBAR_HEIGHT, HOTBAR_RADIUS, 0.0F, 1.0F, 10.0F, SHADOW);
        Render2DUtility.drawRoundedRect(x, y, HOTBAR_OFFHAND_WIDTH, HOTBAR_HEIGHT, HOTBAR_RADIUS, BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(x, y, HOTBAR_OFFHAND_WIDTH, HOTBAR_HEIGHT, HOTBAR_RADIUS, 1.0F, BORDER);
    }

    public void renderAttackIndicator(int centerX, int screenHeight, boolean left, float progress) {
        if (!shouldReplaceHotbar() || progress >= 1.0F) {
            return;
        }

        float x = left
            ? centerX - HOTBAR_WIDTH * 0.5F - HOTBAR_OFFHAND_GAP - HOTBAR_OFFHAND_WIDTH
            : centerX + HOTBAR_WIDTH * 0.5F + HOTBAR_OFFHAND_GAP;
        float y = hotbarY(screenHeight);
        float inset = 4.0F;
        float barWidth = HOTBAR_OFFHAND_WIDTH - inset * 2.0F;
        float barHeight = 4.0F;
        float barY = y + HOTBAR_HEIGHT - inset - barHeight;

        Render2DUtility.drawDropShadow(x, y, HOTBAR_OFFHAND_WIDTH, HOTBAR_HEIGHT, HOTBAR_RADIUS, 0.0F, 1.0F, 10.0F, SHADOW);
        Render2DUtility.drawRoundedRect(x, y, HOTBAR_OFFHAND_WIDTH, HOTBAR_HEIGHT, HOTBAR_RADIUS, BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(x, y, HOTBAR_OFFHAND_WIDTH, HOTBAR_HEIGHT, HOTBAR_RADIUS, 1.0F, BORDER);
        Render2DUtility.drawRoundedRect(x + inset, barY, barWidth, barHeight, 2.0F, HOTBAR_ATTACK_TRACK);
        Render2DUtility.drawRoundedHorizontalGradientRect(x + inset, barY, barWidth * clamp(progress, 0.0F, 1.0F), barHeight, 2.0F, HOTBAR_ATTACK, HOTBAR_ACCENT);
    }

    public void renderContextualBarBackground(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!shouldRenderModernContextualBar()) {
            return;
        }

        ContextualBarMode mode = contextualBarMode();
        float x = contextualBarX(graphics);
        float y = contextualBarY(graphics);
        renderContextualBarFrame(x, y, mode == ContextualBarMode.LOCATOR ? LOCATOR_START : EXP_START);

        if (mode == ContextualBarMode.EXPERIENCE) {
            renderExperienceBarFill(x, y);
        } else if (mode == ContextualBarMode.LOCATOR) {
            renderLocatorBarScale(x, y);
        }
    }

    public void renderContextualBar(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!shouldRenderModernContextualBar() || contextualBarMode() != ContextualBarMode.LOCATOR) {
            return;
        }

        renderLocatorWaypoints(graphics, deltaTracker);
    }

    public void renderExperienceLevel(GuiGraphics graphics) {
        if (!shouldReplaceExperienceLevel()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        FontRenderer font = FontManager.getAppleTextRenderer(8.0F);
        String text = "LV " + player.experienceLevel;
        float textWidth = font.getStringWidth(text);
        float textHeight = font.getLineHeight();
        float x = graphics.guiWidth() * 0.5F - textWidth * 0.5F;
        float y = contextualBarY(graphics) - textHeight - 2.0F;
        font.drawString(text, x + 1.0F, y + 1.0F, 0xAA000000);
        font.drawString(text, x, y, EXP_START);
    }

    public void renderScoreboardSidebar(GuiGraphics graphics, Objective objective) {
        if (!shouldReplaceScoreboard()) {
            return;
        }

        float scale = SCOREBOARD_SCALE;
        FontRenderer font = FontManager.getAppleTextRenderer(9.0F * scale);
        Scoreboard sourceScoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);

        ScoreboardEntry[] entries = sourceScoreboard.listPlayerScores(objective)
            .stream()
            .filter(entry -> !entry.isHidden())
            .sorted(SCORE_DISPLAY_ORDER)
            .limit(15L)
            .map(entry -> {
                PlayerTeam team = sourceScoreboard.getPlayersTeam(entry.owner());
                TextSegment[] name = textSegments(PlayerTeam.formatNameForTeam(team, entry.ownerName()), SCOREBOARD_TEXT);
                TextSegment[] score = textSegments(entry.formatValue(numberFormat), SCOREBOARD_SCORE_TEXT);
                return new ScoreboardEntry(name, score, textWidth(font, name), textWidth(font, score));
            })
            .toArray(ScoreboardEntry[]::new);

        TextSegment[] title = textSegments(objective.getDisplayName(), SCOREBOARD_TEXT);
        float titleWidth = textWidth(font, title);
        float separatorWidth = font.getStringWidth(": ");
        float lineHeight = font.getLineHeight();
        float contentWidth = titleWidth;
        for (ScoreboardEntry entry : entries) {
            contentWidth = Math.max(contentWidth, entry.nameWidth + (entry.scoreWidth > 0 ? separatorWidth + entry.scoreWidth : 0));
        }

        float maxWidth = graphics.guiWidth() * SCOREBOARD_MAX_WIDTH_RATIO;
        float margin = SCOREBOARD_MARGIN * scale;
        float radius = SCOREBOARD_RADIUS * scale;
        float paddingTop = SCOREBOARD_PADDING_TOP * scale;
        float paddingRight = SCOREBOARD_PADDING_RIGHT * scale;
        float paddingBottom = SCOREBOARD_PADDING_BOTTOM * scale;
        float textInset = SCOREBOARD_TEXT_X * scale;
        float headerHeight = SCOREBOARD_HEADER_HEIGHT * scale;
        float rowHeight = SCOREBOARD_ROW_HEIGHT * scale;
        float entryGap = SCOREBOARD_ENTRY_GAP * scale;
        float width = Math.max(SCOREBOARD_MIN_WIDTH * scale, Math.min(contentWidth + textInset + paddingRight, maxWidth));
        float entriesHeight = entries.length * rowHeight;
        float height = paddingTop
            + headerHeight
            + (entries.length > 0 ? entryGap + entriesHeight : 0.0F)
            + paddingBottom;
        float x = graphics.guiWidth() - width - margin;
        float y = (graphics.guiHeight() - height) * 0.5F;
        y = clamp(y, margin, Math.max(margin, graphics.guiHeight() - height - margin));

        Render2DUtility.drawDropShadow(x, y, width, height, radius, 0.0F, 0.0F, 10.0F * scale, SHADOW);
        Render2DUtility.drawRoundedRect(x, y, width, height, radius, BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(x, y, width, height, radius, Math.max(0.75F, scale), BORDER);
        float textX = x + textInset;
        float contentRight = x + width - paddingRight;
        float titleY = y + paddingTop + (headerHeight - lineHeight) * 0.5F - 0.5F * scale;
        drawTrimmedTextSegments(font, title, textX, titleY, contentRight - textX);

        if (entries.length > 0) {
            float dividerY = y + paddingTop + headerHeight + entryGap * 0.5F;
            Render2DUtility.drawRoundedRect(textX, dividerY, contentRight - textX, Math.max(0.75F, scale), 0.5F * scale, SCOREBOARD_DIVIDER);
        }

        float rowY = y + paddingTop + headerHeight + entryGap;
        for (int index = 0; index < entries.length; index++) {
            ScoreboardEntry entry = entries[index];
            float currentY = rowY + index * rowHeight;
            float textY = currentY + (rowHeight - lineHeight) * 0.5F - 0.5F * scale;
            float scoreX = contentRight - entry.scoreWidth;
            float nameWidth = entry.scoreWidth > 0.0F ? scoreX - separatorWidth - textX : contentRight - textX;
            drawTrimmedTextSegments(font, entry.name, textX, textY, nameWidth);
            drawTrimmedTextSegments(font, entry.score, Math.max(textX, scoreX), textY, contentRight - Math.max(textX, scoreX));
        }
    }

    public void renderPotionEffects(GuiGraphics graphics) {
        if (!shouldReplacePotionEffects()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getActiveEffects().isEmpty()) {
            return;
        }

        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : Ordering.natural().reverse().sortedCopy(player.getActiveEffects())) {
            IClientMobEffectExtensions renderer = IClientMobEffectExtensions.of(effect);
            if (!renderer.isVisibleInGui(effect) || !effect.showIcon()) {
                continue;
            }

            effects.add(effect);
        }

        List<EffectEntry> entries = new ArrayList<>();
        float centerOffset = (effects.size() - 1) * 0.5F;
        for (int index = 0; index < effects.size(); index++) {
            entries.add(new EffectEntry(effects.get(index), index - centerOffset));
        }

        entries.stream()
            .sorted(Comparator.comparingDouble((EffectEntry entry) -> Math.abs(entry.slot)).reversed())
            .forEach(entry -> renderPotionEffect(graphics, player, entry.effect, entry.slot));
    }

    public void renderPlayerTabOverlay(
        GuiGraphics graphics,
        int screenWidth,
        Scoreboard scoreboard,
        @Nullable Objective objective,
        @Nullable Component header,
        @Nullable Component footer,
        List<PlayerInfo> players,
        Function<PlayerInfo, Component> nameProvider
    ) {
        if (!shouldReplaceTabList() || players.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }

        FontRenderer font = FontManager.getAppleTextRenderer(TAB_FONT_SIZE);
        TabListEntry[] entries = tabListEntries(font, scoreboard, objective, players, nameProvider);
        int rowCount = entries.length;
        int rowsPerColumn = rowCount;
        int columnCount;
        for (columnCount = 1; rowsPerColumn > 20; rowsPerColumn = (rowCount + columnCount - 1) / columnCount) {
            columnCount++;
        }

        boolean showHeads = minecraft.isLocalServer() || minecraft.getConnection().getConnection().isEncrypted();
        float maxNameWidth = 0.0F;
        float maxScoreWidth = 0.0F;
        for (TabListEntry entry : entries) {
            maxNameWidth = Math.max(maxNameWidth, entry.nameWidth);
            maxScoreWidth = Math.max(maxScoreWidth, entry.scoreWidth);
        }

        float scoreWidth = objective == null ? 0.0F
            : objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS ? TAB_SCORE_HEART_WIDTH : maxScoreWidth;
        float baseColumnWidth = (showHeads ? TAB_HEAD_SIZE + TAB_HEAD_GAP : 0.0F)
            + maxNameWidth
            + (scoreWidth > 0.0F ? TAB_TEXT_GAP + scoreWidth : 0.0F)
            + TAB_PING_WIDTH
            + TAB_TEXT_GAP * 2.0F;
        float availableWidth = Math.max(TAB_MIN_COLUMN_WIDTH, screenWidth - TAB_MAX_WIDTH_INSET - TAB_PADDING_X * 2.0F);
        float maxColumnWidth = Math.max(40.0F, (availableWidth - (columnCount - 1) * TAB_COLUMN_GAP) / columnCount);
        float columnWidth = Math.min(Math.max(TAB_MIN_COLUMN_WIDTH, baseColumnWidth), maxColumnWidth);
        float gridWidth = columnCount * columnWidth + (columnCount - 1) * TAB_COLUMN_GAP;

        String[] headerLines = messageLines(font, header, availableWidth);
        String[] footerLines = messageLines(font, footer, availableWidth);
        float messageLineHeight = font.getLineHeight() + TAB_MESSAGE_LINE_GAP;
        float headerWidth = maxLineWidth(font, headerLines);
        float footerWidth = maxLineWidth(font, footerLines);
        float contentWidth = Math.max(gridWidth, Math.max(headerWidth, footerWidth));
        float rowAreaHeight = rowsPerColumn * TAB_ROW_HEIGHT + Math.max(0, rowsPerColumn - 1) * TAB_ROW_GAP;
        float headerHeight = headerLines.length == 0 ? 0.0F : headerLines.length * messageLineHeight + TAB_MESSAGE_GAP;
        float footerHeight = footerLines.length == 0 ? 0.0F : TAB_MESSAGE_GAP + footerLines.length * messageLineHeight;
        float width = contentWidth + TAB_PADDING_X * 2.0F;
        float height = TAB_PADDING_TOP + headerHeight + rowAreaHeight + footerHeight + TAB_PADDING_BOTTOM;
        float x = screenWidth * 0.5F - width * 0.5F;
        float y = TAB_MARGIN_TOP;
        float contentX = x + TAB_PADDING_X;
        float cursorY = y + TAB_PADDING_TOP;

        Render2DUtility.drawDropShadow(x, y, width, height, TAB_RADIUS, 0.0F, 0.0F, 10.0F, SHADOW);
        Render2DUtility.drawRoundedRect(x, y, width, height, TAB_RADIUS, BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(x, y, width, height, TAB_RADIUS, 1.0F, BORDER);

        if (headerLines.length > 0) {
            renderCenteredLines(font, headerLines, x + width * 0.5F, cursorY, TAB_TEXT);
            cursorY += headerLines.length * messageLineHeight + TAB_MESSAGE_GAP * 0.5F;
            Render2DUtility.drawRoundedRect(contentX, cursorY, contentWidth, 0.75F, 0.5F, TAB_DIVIDER);
            cursorY += TAB_MESSAGE_GAP * 0.5F;
        }

        float gridX = x + (width - gridWidth) * 0.5F;
        for (int index = 0; index < rowCount; index++) {
            int column = index / rowsPerColumn;
            int row = index % rowsPerColumn;
            float rowX = gridX + column * (columnWidth + TAB_COLUMN_GAP);
            float rowY = cursorY + row * (TAB_ROW_HEIGHT + TAB_ROW_GAP);
            renderTabListRow(graphics, font, entries[index], objective, showHeads, rowX, rowY, columnWidth);
        }

        cursorY += rowAreaHeight;
        if (footerLines.length > 0) {
            cursorY += TAB_MESSAGE_GAP * 0.5F;
            Render2DUtility.drawRoundedRect(contentX, cursorY, contentWidth, 0.75F, 0.5F, TAB_DIVIDER);
            cursorY += TAB_MESSAGE_GAP * 0.5F;
            renderCenteredLines(font, footerLines, x + width * 0.5F, cursorY, TAB_SCORE_TEXT);
        }
    }

    private TabListEntry[] tabListEntries(
        FontRenderer font,
        Scoreboard scoreboard,
        @Nullable Objective objective,
        List<PlayerInfo> players,
        Function<PlayerInfo, Component> nameProvider
    ) {
        List<TabListEntry> entries = new ArrayList<>(players.size());
        for (PlayerInfo playerInfo : players) {
            int defaultNameColor = playerInfo.getGameMode() == GameType.SPECTATOR ? TAB_SPECTATOR_TEXT : TAB_TEXT;
            TextSegment[] name = textSegments(nameProvider.apply(playerInfo), defaultNameColor);
            int score = 0;
            TextSegment[] formattedScore = new TextSegment[0];
            float scoreWidth = 0.0F;

            if (objective != null) {
                ScoreHolder scoreHolder = ScoreHolder.fromGameProfile(playerInfo.getProfile());
                ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
                if (scoreInfo != null) {
                    score = scoreInfo.value();
                }

                if (objective.getRenderType() != ObjectiveCriteria.RenderType.HEARTS) {
                    formattedScore = textSegments(
                        ReadOnlyScoreInfo.safeFormatValue(scoreInfo, objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT)),
                        TAB_SCORE_TEXT
                    );
                    scoreWidth = textWidth(font, formattedScore);
                }
            }

            entries.add(new TabListEntry(playerInfo, name, score, formattedScore, textWidth(font, name), scoreWidth));
        }

        return entries.toArray(TabListEntry[]::new);
    }

    private void renderTabListRow(
        GuiGraphics graphics,
        FontRenderer font,
        TabListEntry entry,
        @Nullable Objective objective,
        boolean showHead,
        float x,
        float y,
        float width
    ) {
        Render2DUtility.drawRoundedRect(x, y, width, TAB_ROW_HEIGHT, 4.0F, TAB_ROW_BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(x, y, width, TAB_ROW_HEIGHT, 4.0F, 0.75F, TAB_ROW_BORDER);

        float textX = x + TAB_TEXT_GAP;
        if (showHead) {
            renderTabPlayerHead(graphics, entry.playerInfo, textX, y + (TAB_ROW_HEIGHT - TAB_HEAD_SIZE) * 0.5F);
            textX += TAB_HEAD_SIZE + TAB_HEAD_GAP;
        }

        float pingX = x + width - TAB_PING_WIDTH;
        float scoreRight = pingX - TAB_TEXT_GAP;
        float textY = y + (TAB_ROW_HEIGHT - font.getLineHeight()) * 0.5F - 0.5F;
        boolean canRenderScore = objective != null && entry.playerInfo.getGameMode() != GameType.SPECTATOR;
        float scoreWidth = canRenderScore
            ? objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS ? TAB_SCORE_HEART_WIDTH : entry.scoreWidth
            : 0.0F;
        float scoreLeft = scoreWidth > 0.0F ? scoreRight - scoreWidth : scoreRight;
        float nameRight = scoreWidth > 0.0F ? scoreLeft - TAB_TEXT_GAP : scoreRight - TAB_TEXT_GAP;
        drawTrimmedTextSegments(font, entry.name, textX, textY, Math.max(0.0F, nameRight - textX));

        if (canRenderScore && scoreWidth > 0.0F) {
            if (objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS) {
                renderTabHealthScore(entry.score, scoreLeft, y + (TAB_ROW_HEIGHT - TAB_SCORE_HEART_HEIGHT) * 0.5F, scoreWidth);
            } else {
                float scoreX = Math.max(textX, scoreLeft);
                drawTrimmedTextSegments(font, entry.formattedScore, scoreX, textY, scoreRight - scoreX);
            }
        }

        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            pingSprite(entry.playerInfo),
            Math.round(pingX + 1.0F),
            Math.round(y + (TAB_ROW_HEIGHT - 8.0F) * 0.5F),
            10,
            8
        );
    }

    private void renderTabPlayerHead(GuiGraphics graphics, PlayerInfo playerInfo, float x, float y) {
        Minecraft minecraft = Minecraft.getInstance();
        GameProfile profile = playerInfo.getProfile();
        Player player = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(profile.id());
        boolean upsideDown = player != null && AvatarRenderer.isPlayerUpsideDown(player);
        PlayerFaceRenderer.draw(
            graphics,
            playerInfo.getSkin().body().texturePath(),
            Math.round(x),
            Math.round(y),
            Math.round(TAB_HEAD_SIZE),
            playerInfo.showHat(),
            upsideDown,
            -1
        );
    }

    private void renderTabHealthScore(int score, float x, float y, float width) {
        float health = clamp(score, 0.0F, 20.0F);
        Render2DUtility.drawRoundedRect(x, y, width, TAB_SCORE_HEART_HEIGHT, TAB_SCORE_HEART_HEIGHT * 0.5F, TRACK);
        float fillWidth = width * health / 20.0F;
        if (fillWidth > 0.25F) {
            Render2DUtility.drawRoundedHorizontalGradientRect(
                x,
                y,
                fillWidth,
                TAB_SCORE_HEART_HEIGHT,
                TAB_SCORE_HEART_HEIGHT * 0.5F,
                HEALTH,
                HEALTH_DARK
            );
        }

        FontRenderer smallFont = FontManager.getAppleTextRenderer(6.5F);
        String text = formatAmount(score * 0.5F);
        smallFont.drawCenteredString(text, x + width * 0.5F, y - (smallFont.getLineHeight() - TAB_SCORE_HEART_HEIGHT) * 0.5F - 0.25F, TAB_TEXT);
    }

    private Identifier pingSprite(PlayerInfo playerInfo) {
        int latency = playerInfo.getLatency();
        if (latency < 0) {
            return PING_UNKNOWN_SPRITE;
        }
        if (latency < 150) {
            return PING_5_SPRITE;
        }
        if (latency < 300) {
            return PING_4_SPRITE;
        }
        if (latency < 600) {
            return PING_3_SPRITE;
        }
        if (latency < 1000) {
            return PING_2_SPRITE;
        }
        return PING_1_SPRITE;
    }

    private String[] messageLines(FontRenderer font, @Nullable Component component, float maxWidth) {
        if (component == null) {
            return new String[0];
        }

        String text = component.getString();
        if (text.isBlank()) {
            return new String[0];
        }

        String[] rawLines = text.split("\\R");
        List<String> lines = new ArrayList<>(rawLines.length);
        for (String rawLine : rawLines) {
            if (!rawLine.isBlank()) {
                lines.add(trimToWidth(font, rawLine, maxWidth));
            }
        }

        return lines.toArray(String[]::new);
    }

    private float maxLineWidth(FontRenderer font, String[] lines) {
        float width = 0.0F;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }
        return width;
    }

    private void renderCenteredLines(FontRenderer font, String[] lines, float centerX, float y, int color) {
        float lineHeight = font.getLineHeight() + TAB_MESSAGE_LINE_GAP;
        for (int index = 0; index < lines.length; index++) {
            font.drawCenteredString(lines[index], centerX, y + index * lineHeight, color);
        }
    }

    private void renderPotionEffect(GuiGraphics graphics, LocalPlayer player, MobEffectInstance effect, float slot) {
        float distance = Math.abs(slot);
        float scale = clamp(1.0F - distance * EFFECT_SCALE_STEP, EFFECT_MIN_SCALE, EFFECT_MAX_SCALE) * EFFECT_SIZE_SCALE;
        FontRenderer font = FontManager.getAppleTextRenderer(12.0F * scale);
        String duration = effectDuration(player, effect);
        String separator = " | ";
        String name = effectName(effect);
        float textStart = (EFFECT_ICON_LEFT + EFFECT_ICON_SIZE + EFFECT_ICON_GAP) * scale;
        float width = EFFECT_WIDTH * scale;
        float textMaxWidth = width - textStart - EFFECT_TEXT_RIGHT * scale;
        name = trimToWidth(font, name, Math.max(0.0F, textMaxWidth - font.getStringWidth(separator + duration)));
        float height = EFFECT_HEIGHT * scale;
        float x = EFFECT_LEFT_BASE - distance * EFFECT_LEFT_STEP;
        float y = graphics.guiHeight() * 0.5F + slot * EFFECT_ROW_STEP - height * 0.5F;

        if (x < 0.0F || y < 0.0F || x + width > graphics.guiWidth() || y + height > graphics.guiHeight()) {
            return;
        }

        float radius = EFFECT_RADIUS * scale;
        int accent = 0xFF000000 | effect.getEffect().value().getColor();
        Render2DUtility.drawDropShadow(x, y, width, height, radius, 0.0F, 0.0F, 10.0F * scale, SHADOW);
        Render2DUtility.drawRoundedRect(x, y, width, height, radius, BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(x, y, width, height, radius, Math.max(0.75F, scale), BORDER);
        Render2DUtility.drawRoundedRect(x + 5.0F * scale, y + 7.0F * scale, 3.0F * scale, height - 14.0F * scale, 1.5F * scale, accent);

        renderPotionIcon(graphics, effect, x + EFFECT_ICON_LEFT * scale, y + (height - EFFECT_ICON_SIZE * scale) * 0.5F, EFFECT_ICON_SIZE * scale);

        float textY = y + (height - font.getLineHeight()) * 0.5F - 0.5F * scale;
        float textX = x + textStart;
        font.drawString(name, textX, textY, EFFECT_TEXT);
        textX += font.getStringWidth(name);
        font.drawString(separator, textX, textY, EFFECT_DURATION_TEXT);
        textX += font.getStringWidth(separator);
        font.drawString(duration, textX, textY, EFFECT_DURATION_TEXT);
    }

    private void renderPotionIcon(GuiGraphics graphics, MobEffectInstance effect, float x, float y, float size) {
        int iconX = Math.round(x);
        int iconY = Math.round(y);
        int iconSize = Math.max(1, Math.round(size));
        Holder<MobEffect> holder = effect.getEffect();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(holder), iconX, iconY, iconSize, iconSize);
    }

    private String effectName(MobEffectInstance effect) {
        String name = effect.getEffect().value().getDisplayName().getString();
        int amplifier = effect.getAmplifier();
        if (amplifier >= 1 && amplifier <= 9) {
            name += " " + Component.translatable("enchantment.level." + (amplifier + 1)).getString();
        }

        return name;
    }

    private String effectDuration(Player player, MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) {
            return "Infinity";
        }

        if (effect.getDuration() > EFFECT_TOO_LONG_TICKS) {
            return "****";
        }

        float tickRate = player.level() == null ? 20.0F : Math.max(1.0F, player.level().tickRateManager().tickrate());
        return StringUtil.formatTickDuration(effect.getDuration(), tickRate);
    }

    private void renderContextualBarFrame(float x, float y, int accentColor) {
        Render2DUtility.drawDropShadow(x, y, CONTEXT_BAR_WIDTH, CONTEXT_BAR_HEIGHT, CONTEXT_BAR_RADIUS, 0.0F, 0.75F, 8.0F, SHADOW);
        Render2DUtility.drawRoundedRect(x, y, CONTEXT_BAR_WIDTH, CONTEXT_BAR_HEIGHT, CONTEXT_BAR_RADIUS, BACKGROUND);
        Render2DUtility.drawRoundedRect(
            x + CONTEXT_BAR_INSET,
            y + CONTEXT_BAR_INSET,
            CONTEXT_BAR_WIDTH - CONTEXT_BAR_INSET * 2.0F,
            CONTEXT_BAR_HEIGHT - CONTEXT_BAR_INSET * 2.0F,
            CONTEXT_BAR_RADIUS - 1.0F,
            CONTEXT_TRACK
        );
        Render2DUtility.drawOutlineRoundedRect(x, y, CONTEXT_BAR_WIDTH, CONTEXT_BAR_HEIGHT, CONTEXT_BAR_RADIUS, 1.0F, BORDER);
        Render2DUtility.drawRoundedRect(x + 2.0F, y + 2.0F, 2.0F, CONTEXT_BAR_HEIGHT - 4.0F, 1.0F, accentColor);
    }

    private void renderExperienceBarFill(float x, float y) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.getXpNeededForNextLevel() <= 0) {
            return;
        }

        float fillWidth = (CONTEXT_BAR_WIDTH - CONTEXT_BAR_INSET * 2.0F) * clamp(player.experienceProgress, 0.0F, 1.0F);
        if (fillWidth <= 0.25F) {
            return;
        }

        float fillX = x + CONTEXT_BAR_INSET;
        float fillY = y + CONTEXT_BAR_INSET;
        float fillHeight = CONTEXT_BAR_HEIGHT - CONTEXT_BAR_INSET * 2.0F;
        float radius = Math.min(CONTEXT_BAR_RADIUS - 1.0F, fillWidth * 0.5F);
        Render2DUtility.drawRoundedRect(fillX - 1.0F, fillY - 1.0F, fillWidth + 2.0F, fillHeight + 2.0F, radius + 1.0F, EXP_GLOW);
        Render2DUtility.drawRoundedHorizontalGradientRect(fillX, fillY, fillWidth, fillHeight, radius, EXP_START, EXP_END);
    }

    private void renderLocatorBarScale(float x, float y) {
        float trackX = x + CONTEXT_BAR_INSET;
        float trackY = y + CONTEXT_BAR_INSET;
        float trackWidth = CONTEXT_BAR_WIDTH - CONTEXT_BAR_INSET * 2.0F;
        float trackHeight = CONTEXT_BAR_HEIGHT - CONTEXT_BAR_INSET * 2.0F;
        Render2DUtility.drawRoundedHorizontalGradientRect(trackX, trackY, trackWidth, trackHeight, CONTEXT_BAR_RADIUS - 1.0F, LOCATOR_START, LOCATOR_END);

        float centerX = x + CONTEXT_BAR_WIDTH * 0.5F;
        Render2DUtility.drawRoundedRect(centerX - 0.5F, y + 1.0F, 1.0F, CONTEXT_BAR_HEIGHT - 2.0F, 0.5F, LOCATOR_CENTER);
        for (int index = 1; index <= 3; index++) {
            float offset = index * 24.0F;
            Render2DUtility.drawRoundedRect(centerX - offset - 0.5F, y + 2.5F, 1.0F, CONTEXT_BAR_HEIGHT - 5.0F, 0.5F, CONTEXT_TICK);
            Render2DUtility.drawRoundedRect(centerX + offset - 0.5F, y + 2.5F, 1.0F, CONTEXT_BAR_HEIGHT - 5.0F, 0.5F, CONTEXT_TICK);
        }
    }

    private void renderLocatorWaypoints(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.getCameraEntity();
        LocalPlayer player = minecraft.player;
        if (entity == null || player == null || player.connection == null) {
            return;
        }

        Level level = entity.level();
        TickRateManager tickRateManager = level.tickRateManager();
        PartialTickSupplier partialTicks = trackedEntity -> deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(trackedEntity));
        float centerX = graphics.guiWidth() * 0.5F;
        float y = contextualBarY(graphics);
        player.connection.getWaypointManager().forEachWaypoint(entity, waypoint -> {
            if (isSelfWaypoint(entity, waypoint)) {
                return;
            }

            double yaw = waypoint.yawAngleToCamera(level, minecraft.gameRenderer.getMainCamera(), partialTicks);
            if (yaw <= -LOCATOR_VISIBLE_DEGREES || yaw > LOCATOR_VISIBLE_DEGREES) {
                return;
            }

            float offset = (float)(yaw * (CONTEXT_BAR_WIDTH - LOCATOR_DOT_SIZE) * 0.5F / LOCATOR_VISIBLE_DEGREES);
            float dotX = centerX + offset - LOCATOR_DOT_SIZE * 0.5F;
            float dotY = y;
            Waypoint.Icon icon = waypoint.icon();
            WaypointStyle style = minecraft.getWaypointStyles().get(icon.style);
            Identifier sprite = style.sprite(Mth.sqrt((float)waypoint.distanceSquared(entity)));
            int color = icon.color.orElseGet(() -> waypoint.id().map(
                id -> ARGB.setBrightness(ARGB.color(255, id.hashCode()), 0.9F),
                name -> ARGB.setBrightness(ARGB.color(255, name.hashCode()), 0.9F)
            ));

            Render2DUtility.drawCircle(dotX + LOCATOR_DOT_SIZE * 0.5F, dotY + LOCATOR_DOT_SIZE * 0.5F, 5.5F, LOCATOR_DOT_BACKDROP);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, Math.round(dotX), Math.round(dotY), LOCATOR_DOT_SIZE, LOCATOR_DOT_SIZE, color);
            renderLocatorPitchArrow(graphics, waypoint, level, minecraft, partialTicks, dotX, y);
        });
    }

    private void renderLocatorPitchArrow(
        GuiGraphics graphics,
        TrackedWaypoint waypoint,
        Level level,
        Minecraft minecraft,
        PartialTickSupplier partialTicks,
        float dotX,
        float y
    ) {
        TrackedWaypoint.PitchDirection pitchDirection = waypoint.pitchDirectionToCamera(level, minecraft.gameRenderer, partialTicks);
        if (pitchDirection == TrackedWaypoint.PitchDirection.NONE) {
            return;
        }

        boolean down = pitchDirection == TrackedWaypoint.PitchDirection.DOWN;
        Identifier sprite = down ? LOCATOR_BAR_ARROW_DOWN : LOCATOR_BAR_ARROW_UP;
        int arrowY = Math.round(y + (down ? 8.0F : -5.0F));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, Math.round(dotX + 1.0F), arrowY, 7, 5);
    }

    private boolean isSelfWaypoint(Entity entity, TrackedWaypoint waypoint) {
        return waypoint.id().left().map(id -> id.equals(entity.getUUID())).orElse(false);
    }

    private ContextualBarMode contextualBarMode() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.gameMode == null || player == null || player.connection == null) {
            return ContextualBarMode.NONE;
        }

        boolean hasWaypoints = player.connection.getWaypointManager().hasWaypoints();
        boolean hasJumpableVehicle = player.jumpableVehicle() != null;
        boolean hasExperience = minecraft.gameMode.hasExperience();
        if (hasWaypoints) {
            if (hasJumpableVehicle && willPrioritizeJumpInfo(player)) {
                return ContextualBarMode.NONE;
            }
            return hasExperience && willPrioritizeExperienceInfo(player) ? ContextualBarMode.EXPERIENCE : ContextualBarMode.LOCATOR;
        }

        if (hasJumpableVehicle) {
            return ContextualBarMode.NONE;
        }

        return hasExperience ? ContextualBarMode.EXPERIENCE : ContextualBarMode.NONE;
    }

    private boolean willPrioritizeExperienceInfo(LocalPlayer player) {
        return player.experienceDisplayStartTick + 100 > player.tickCount;
    }

    private boolean willPrioritizeJumpInfo(LocalPlayer player) {
        PlayerRideableJumping jumpableVehicle = player.jumpableVehicle();
        return player.getJumpRidingScale() > 0.0F || jumpableVehicle != null && jumpableVehicle.getJumpCooldown() > 0;
    }

    private void drawBarBase(float x, float y, int accentColor) {
        Render2DUtility.drawDropShadow(x, y, BAR_WIDTH, BAR_HEIGHT, BAR_RADIUS, 0.0F, 0.5F, 6.0F, SHADOW);
        Render2DUtility.drawRoundedRect(x, y, BAR_WIDTH, BAR_HEIGHT, BAR_RADIUS, BACKGROUND);
        Render2DUtility.drawRoundedRect(x + BAR_INSET, y + BAR_INSET, BAR_WIDTH - BAR_INSET * 2.0F, BAR_HEIGHT - BAR_INSET * 2.0F, BAR_RADIUS - 1.0F, TRACK);
        Render2DUtility.drawOutlineRoundedRect(x, y, BAR_WIDTH, BAR_HEIGHT, BAR_RADIUS, 1.0F, BORDER);
        Render2DUtility.drawRoundedRect(x + 2.0F, y + 2.0F, 2.0F, BAR_HEIGHT - 4.0F, 1.0F, accentColor);
    }

    private void drawBarFill(float x, float y, float progress, int startColor, int endColor) {
        float fillWidth = (BAR_WIDTH - BAR_INSET * 2.0F) * clamp(progress, 0.0F, 1.0F);
        if (fillWidth <= 0.25F) {
            return;
        }

        float fillX = x + BAR_INSET;
        float fillY = y + BAR_INSET;
        float fillHeight = BAR_HEIGHT - BAR_INSET * 2.0F;
        float radius = Math.min(BAR_RADIUS - 1.0F, fillWidth * 0.5F);
        Render2DUtility.drawRoundedHorizontalGradientRect(fillX, fillY, fillWidth, fillHeight, radius, startColor, endColor);
    }

    private void drawCenteredText(String text, float x, float y, int color) {
        FontRenderer font = FontManager.getAppleTextRenderer(6.5F);
        float textWidth = font.getStringWidth(text);
        float textHeight = font.getLineHeight();
        float textX = x + BAR_WIDTH * 0.5F - textWidth * 0.5F;
        float textY = y + BAR_HEIGHT * 0.5F - textHeight * 0.5F - 0.25F;
        font.drawString(text, textX, textY, color);
    }

    private float hotbarY(int screenHeight) {
        return screenHeight - HOTBAR_HEIGHT - 1.0F;
    }

    private float slotCellX(int centerX, int slot) {
        return centerX - HOTBAR_WIDTH * 0.5F + 1.0F + slot * (HOTBAR_SLOT_SIZE + HOTBAR_SLOT_GAP);
    }

    private float contextualBarX(GuiGraphics graphics) {
        return graphics.guiWidth() * 0.5F - CONTEXT_BAR_WIDTH * 0.5F;
    }

    private float contextualBarY(GuiGraphics graphics) {
        return graphics.guiHeight() - CONTEXT_BAR_BOTTOM - CONTEXT_BAR_HEIGHT;
    }

    private float centeredBarY(float y) {
        return y + (BAR_ROW_HEIGHT - BAR_HEIGHT) * 0.5F;
    }

    private float statusBarY(GuiGraphics graphics, float y) {
        return centeredBarY(y) - statusContextualBarOffset(graphics);
    }

    private float statusContextualBarOffset(GuiGraphics graphics) {
        if (!shouldRenderModernContextualBar()) {
            return 0.0F;
        }

        float statusBottom = graphics.guiHeight() - STATUS_BAR_BOTTOM;
        float contextualTop = contextualBarY(graphics);
        return Math.max(0.0F, statusBottom - contextualTop + STATUS_CONTEXTUAL_BAR_GAP);
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

    private TextSegment[] textSegments(Component component, int defaultColor) {
        List<TextSegment> segments = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int[] activeColor = {defaultColor};

        component.getVisualOrderText().accept((index, style, codePoint) -> {
            int color = textColor(style, defaultColor);
            if (color != activeColor[0] && !builder.isEmpty()) {
                segments.add(new TextSegment(builder.toString(), activeColor[0]));
                builder.setLength(0);
            }

            builder.appendCodePoint(codePoint);
            activeColor[0] = color;
            return true;
        });

        if (!builder.isEmpty()) {
            segments.add(new TextSegment(builder.toString(), activeColor[0]));
        }

        return replaceServerAddresses(segments);
    }

    private int textColor(Style style, int defaultColor) {
        TextColor color = style.getColor();
        return color == null ? defaultColor : 0xFF000000 | color.getValue();
    }

    private TextSegment[] replaceServerAddresses(List<TextSegment> segments) {
        List<TextSegment> replaced = new ArrayList<>();
        boolean changed = false;
        for (TextSegment segment : segments) {
            Matcher matcher = SCOREBOARD_SERVER_ADDRESS_CANDIDATE.matcher(segment.text);
            int start = 0;
            while (matcher.find()) {
                if (!StringUtility.isIpAddress(matcher.group())) {
                    continue;
                }

                changed = true;
                if (matcher.start() > start) {
                    replaced.add(new TextSegment(segment.text.substring(start, matcher.start()), segment.color));
                }

                replaced.add(new TextSegment(SCOREBOARD_BRAND_TEXT, SCOREBOARD_BRAND_COLOR, true));
                start = matcher.end();
            }

            if (start < segment.text.length()) {
                replaced.add(new TextSegment(segment.text.substring(start), segment.color));
            }
        }

        return (changed ? replaced : segments).toArray(TextSegment[]::new);
    }

    private float textWidth(FontRenderer font, TextSegment[] segments) {
        float width = 0.0F;
        for (TextSegment segment : segments) {
            width += font.getStringWidth(segment.text);
        }
        return width;
    }

    private void drawTextSegments(FontRenderer font, TextSegment[] segments, float x, float y) {
        float currentX = x;
        for (TextSegment segment : segments) {
            drawTextSegment(font, segment, currentX, y);
            currentX += font.getStringWidth(segment.text);
        }
    }

    private void drawTextSegment(FontRenderer font, TextSegment segment, float x, float y) {
        if (!segment.glitch) {
            font.drawString(segment.text, x, y, segment.color);
            return;
        }

        drawGlitchText(font, segment.text, x, y);
    }

    private void drawGlitchText(FontRenderer font, String text, float x, float y) {
        long frame = System.currentTimeMillis() / SCOREBOARD_GLITCH_FRAME_MS;
        int phase = (int)(frame % SCOREBOARD_GLITCH_PHASES);
        boolean hardFlash = phase == 1 || phase == 5 || phase == 8;
        boolean split = phase == 0 || phase == 2 || phase == 4 || phase == 7;

        if (split) {
            float cyanOffset = phase == 4 || phase == 7 ? 1.0F : -1.0F;
            float magentaOffset = phase == 7 ? -1.0F : 1.0F;
            font.drawString(text, x + cyanOffset, y, SCOREBOARD_BRAND_CYAN);
            font.drawString(text, x + magentaOffset, y + (phase == 0 ? 0.5F : 0.0F), SCOREBOARD_BRAND_MAGENTA);
        }

        font.drawString(text, x, y, hardFlash ? SCOREBOARD_BRAND_DIM : SCOREBOARD_BRAND_COLOR);
    }

    private void drawTrimmedTextSegments(FontRenderer font, TextSegment[] segments, float x, float y, float maxWidth) {
        if (maxWidth <= 0.5F) {
            return;
        }

        if (textWidth(font, segments) <= maxWidth) {
            drawTextSegments(font, segments, x, y);
            return;
        }

        float currentX = x;
        float remainingWidth = maxWidth;
        for (TextSegment segment : segments) {
            if (remainingWidth <= 0.5F) {
                return;
            }

            float segmentWidth = font.getStringWidth(segment.text);
            if (segmentWidth <= remainingWidth) {
                drawTextSegment(font, segment, currentX, y);
                currentX += segmentWidth;
                remainingWidth -= segmentWidth;
                continue;
            }

            drawTextSegment(font, new TextSegment(trimToWidth(font, segment.text, remainingWidth), segment.color, segment.glitch), currentX, y);
            return;
        }
    }

    private String trimToWidth(FontRenderer font, String text, float maxWidth) {
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

    private record EffectEntry(MobEffectInstance effect, float slot) {
    }

    private record ScoreboardEntry(TextSegment[] name, TextSegment[] score, float nameWidth, float scoreWidth) {
    }

    private record TabListEntry(PlayerInfo playerInfo, TextSegment[] name, int score, TextSegment[] formattedScore, float nameWidth, float scoreWidth) {
    }

    private record TextSegment(String text, int color, boolean glitch) {
        private TextSegment(String text, int color) {
            this(text, color, false);
        }
    }

    private enum ContextualBarMode {
        NONE,
        EXPERIENCE,
        LOCATOR
    }
}
