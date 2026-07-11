package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.manager.ModuleManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.value.AbstractValue;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ButtonValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import io.github.seraphina.nyx.client.value.impl.KeyBindValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ModuleInfo(name = "nyxclient.module.modulelist.name", description = "nyxclient.module.modulelist.description", category = Category.VISUAL)
public class ModuleList extends Module {
    public static final ModuleList INSTANCE = new ModuleList();

    private static final float SCALE = 0.5F;
    private static final float TOP_PADDING = 5.0F * SCALE;
    private static final float RIGHT_PADDING = 5.0F * SCALE;
    private static final float HORIZONTAL_PADDING = 7.0F * SCALE;
    private static final float ROW_HEIGHT = 17.0F * SCALE;
    private static final float ROW_GAP = 1.0F * SCALE;
    private static final float RADIUS = 5.0F * SCALE;
    private static final float ACCENT_WIDTH = 2.0F * SCALE;
    private static final float ACCENT_INSET = 4.0F * SCALE;
    private static final float SLIDE_DISTANCE = 12.0F * SCALE;
    private static final float EDGE_EPSILON = 0.5F * SCALE;
    private static final float MAX_FRAME_SECONDS = 0.05F;
    private static final float ANIMATION_SPEED = 7.5F;
    private static final int BACKGROUND = 0xCC0C0D11;
    private static final int BORDER = 0x22FFFFFF;
    private static final int SHADOW = 0x74000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUFFIX_TEXT = 0xFFD0D4DE;
    private static final int ACCENT_START = 0xFF57C7FF;
    private static final int ACCENT_MIDDLE = 0xFF3D81F7;
    private static final int ACCENT_END = 0xFFFF4FD8;
    private static final Comparator<RenderEntry> ENTRY_ORDER = Comparator
        .comparingDouble((RenderEntry entry) -> entry.textWidth)
        .reversed()
        .thenComparing(entry -> entry.fullText, String.CASE_INSENSITIVE_ORDER);

    public final EnumValue<Type> type = ValueBuild.enumSetting("type", Type.ALL, this);
    public final BoolValue suffix = ValueBuild.boolSetting("suffix", true, this);

    private final Map<Module, AnimationState> animations = new HashMap<>();
    private long lastFrameNanos;

    @Override
    public void onEnable() {
        animations.clear();
        lastFrameNanos = 0L;
    }

    @Override
    public void onDisable() {
        animations.clear();
        lastFrameNanos = 0L;
    }

    @EventTarget
    public void onRender2D(Render2DEvent.HUD event) {
        if (mc.player == null || mc.level == null || mc.screen instanceof ChatScreen) {
            return;
        }

        float frameSeconds = frameSeconds();
        updateAnimations(frameSeconds);

        FontRenderer font = font();
        List<RenderEntry> entries = collectEntries(font);
        if (entries.isEmpty()) {
            return;
        }

        entries.sort(ENTRY_ORDER);
        layoutEntries(event.getGuiGraphics(), entries);
        updateCornerStates(entries);

        Render2DUtility.withGuiGraphics(event.getGuiGraphics(), () -> {
            renderBackgrounds(entries);
            renderText(font, entries);
        });
    }

    public enum Type {
        ALL,
        NO_VISUAL
    }

    private void updateAnimations(float frameSeconds) {
        for (Module module : ModuleManager.getModules()) {
            AnimationState state = animations.computeIfAbsent(module, ignored -> new AnimationState());
            float target = shouldDisplay(module) && module.isEnabled() ? 1.0F : 0.0F;
            state.progress = MathUtility.animateExp(state.progress, target, ANIMATION_SPEED, frameSeconds);
        }

        Iterator<Map.Entry<Module, AnimationState>> iterator = animations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Module, AnimationState> entry = iterator.next();
            if (!entry.getKey().isEnabled() && entry.getValue().progress <= 0.0F) {
                iterator.remove();
            }
        }
    }

    private List<RenderEntry> collectEntries(FontRenderer font) {
        List<RenderEntry> entries = new ArrayList<>();
        for (Map.Entry<Module, AnimationState> animationEntry : animations.entrySet()) {
            float progress = animationEntry.getValue().progress;
            if (progress <= 0.001F) {
                continue;
            }

            Module module = animationEntry.getKey();
            String moduleName = module.getName();
            if (moduleName == null || moduleName.isBlank()) {
                continue;
            }

            String suffixText = suffix.getValue() ? suffixText(module) : "";
            String fullText = moduleName + suffixText;
            RenderEntry entry = new RenderEntry();
            entry.fullText = fullText;
            entry.moduleName = moduleName;
            entry.suffixText = suffixText;
            entry.nameWidth = font.getStringWidth(moduleName);
            entry.textWidth = font.getStringWidth(fullText);
            entry.rowWidth = entry.textWidth + HORIZONTAL_PADDING * 2.0F + ACCENT_WIDTH;
            entry.easedProgress = MathUtility.easeOutCubic(progress);
            entries.add(entry);
        }
        return entries;
    }

    private void layoutEntries(GuiGraphics graphics, List<RenderEntry> entries) {
        float screenWidth = graphics.guiWidth();
        for (int index = 0; index < entries.size(); index++) {
            RenderEntry entry = entries.get(index);
            entry.y = TOP_PADDING + index * (ROW_HEIGHT + ROW_GAP);
            entry.x = screenWidth - RIGHT_PADDING - entry.rowWidth + (1.0F - entry.easedProgress) * (entry.rowWidth + SLIDE_DISTANCE);
            float colorProgress = entries.size() <= 1 ? 0.0F : index / (float)(entries.size() - 1);
            entry.accentColor = accentColor(colorProgress);
        }
    }

    private void renderBackgrounds(List<RenderEntry> entries) {
        for (RenderEntry entry : entries) {
            int shadow = Render2DUtility.applyOpacity(SHADOW, entry.easedProgress);
            int background = Render2DUtility.applyOpacity(BACKGROUND, entry.easedProgress);
            int border = Render2DUtility.applyOpacity(BORDER, entry.easedProgress);
            int accent = Render2DUtility.applyOpacity(entry.accentColor, entry.easedProgress);

            Render2DUtility.drawDropShadow(entry.x, entry.y, entry.rowWidth, ROW_HEIGHT, RADIUS, 0.0F, 1.0F * SCALE, 8.0F * SCALE, shadow);
            Render2DUtility.drawRoundedRect(
                entry.x,
                entry.y,
                entry.rowWidth,
                ROW_HEIGHT,
                entry.topLeftRounded ? RADIUS : 0.0F,
                entry.topRightRounded ? RADIUS : 0.0F,
                entry.bottomRightRounded ? RADIUS : 0.0F,
                entry.bottomLeftRounded ? RADIUS : 0.0F,
                background
            );
            Render2DUtility.drawOutlineRoundedRect(
                entry.x,
                entry.y,
                entry.rowWidth,
                ROW_HEIGHT,
                RADIUS,
                1.0F * SCALE,
                border
            );
            Render2DUtility.drawRoundedRect(
                entry.x + 4.0F * SCALE,
                entry.y + ACCENT_INSET,
                ACCENT_WIDTH,
                ROW_HEIGHT - ACCENT_INSET * 2.0F,
                ACCENT_WIDTH * 0.5F,
                accent
            );
        }
    }

    private void renderText(FontRenderer font, List<RenderEntry> entries) {
        float textYAdjust = -0.75F * SCALE;
        float textY = (ROW_HEIGHT - font.getLineHeight()) * 0.5F + textYAdjust;
        for (RenderEntry entry : entries) {
            float textX = entry.x + HORIZONTAL_PADDING + ACCENT_WIDTH;
            float y = entry.y + textY;
            int textColor = Render2DUtility.applyOpacity(TEXT, entry.easedProgress);
            int suffixColor = Render2DUtility.applyOpacity(SUFFIX_TEXT, entry.easedProgress);

            font.drawString(entry.moduleName, textX, y, textColor);
            if (!entry.suffixText.isEmpty()) {
                font.drawString(entry.suffixText, textX + entry.nameWidth, y, suffixColor);
            }
        }
    }

    private void updateCornerStates(List<RenderEntry> entries) {
        for (int index = 0; index < entries.size(); index++) {
            RenderEntry previous = index > 0 ? entries.get(index - 1) : null;
            RenderEntry current = entries.get(index);
            RenderEntry next = index + 1 < entries.size() ? entries.get(index + 1) : null;

            current.topLeftRounded = !coversX(previous, current.x);
            current.topRightRounded = !coversX(previous, current.x + current.rowWidth);
            current.bottomLeftRounded = !coversX(next, current.x);
            current.bottomRightRounded = !coversX(next, current.x + current.rowWidth);
        }
    }

    private boolean coversX(RenderEntry entry, float x) {
        return entry != null && x >= entry.x - EDGE_EPSILON && x <= entry.x + entry.rowWidth + EDGE_EPSILON;
    }

    private boolean shouldDisplay(Module module) {
        return type.getValue() != Type.NO_VISUAL || module.getCategory() != Category.VISUAL;
    }

    private String suffixText(Module module) {
        for (AbstractValue<?> value : module.getValues()) {
            if (!shouldUseSuffixValue(value)) {
                continue;
            }

            String text = formatValue(value);
            if (text != null && !text.isBlank()) {
                return " " + text;
            }
        }
        return "";
    }

    private boolean shouldUseSuffixValue(AbstractValue<?> value) {
        return value != null
            && value.isVisible()
            && !value.isDefault()
            && !(value instanceof BoolValue)
            && !(value instanceof ButtonValue)
            && !(value instanceof ColorValue)
            && !(value instanceof KeyBindValue);
    }

    private String formatValue(AbstractValue<?> value) {
        Object rawValue = value.getValue();
        if (rawValue == null) {
            return null;
        }

        if (value instanceof DoubleValue doubleValue) {
            return formatNumber(doubleValue.getValue(), doubleValue.isPercentageMode());
        }

        if (value instanceof IntValue intValue) {
            return formatNumber(intValue.getValue(), intValue.isPercentageMode());
        }

        if (rawValue instanceof Enum<?> enumValue) {
            return formatEnum(enumValue);
        }

        return String.valueOf(rawValue);
    }

    private String formatNumber(Number number, boolean percentage) {
        double value = number.doubleValue();
        if (percentage) {
            value *= 100.0D;
        }

        String text;
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            text = String.valueOf((long)Math.rint(value));
        } else {
            text = BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
        }
        return percentage ? text + "%" : text;
    }

    private String formatEnum(Enum<?> value) {
        String normalized = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == ' ') {
                builder.append(character);
                capitalize = true;
                continue;
            }

            builder.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = false;
        }
        return builder.toString();
    }

    private int accentColor(float progress) {
        if (progress < 0.5F) {
            return Render2DUtility.mix(ACCENT_START, ACCENT_MIDDLE, progress * 2.0F);
        }
        return Render2DUtility.mix(ACCENT_MIDDLE, ACCENT_END, (progress - 0.5F) * 2.0F);
    }

    private float frameSeconds() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 1.0F / 60.0F;
        }

        float seconds = (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        return Math.min(Math.max(seconds, 0.0F), MAX_FRAME_SECONDS);
    }

    private FontRenderer font() {
        return FontManager.getAppleTextRenderer(10.0F * SCALE);
    }

    private static final class AnimationState {
        private float progress;
    }

    private static final class RenderEntry {
        private String fullText;
        private String moduleName;
        private String suffixText;
        private float nameWidth;
        private float textWidth;
        private float rowWidth;
        private float easedProgress;
        private float x;
        private float y;
        private int accentColor;
        private boolean topLeftRounded;
        private boolean topRightRounded;
        private boolean bottomRightRounded;
        private boolean bottomLeftRounded;
    }
}
