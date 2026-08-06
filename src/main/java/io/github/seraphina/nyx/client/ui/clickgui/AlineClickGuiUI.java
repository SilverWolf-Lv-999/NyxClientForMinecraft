package io.github.seraphina.nyx.client.ui.clickgui;

import io.github.seraphina.nyx.client.manager.ModuleManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.ui.LuaScreen;
import io.github.seraphina.nyx.client.value.AbstractValue;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ButtonValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import io.github.seraphina.nyx.client.value.impl.KeyBindValue;
import io.github.seraphina.nyx.client.value.impl.StringValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaValue;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.seraphina.nyx.client.utility.MathUtility.animateExp;
import static io.github.seraphina.nyx.client.utility.MathUtility.clamp;
import static io.github.seraphina.nyx.client.utility.MathUtility.easeOutBack;
import static io.github.seraphina.nyx.client.utility.MathUtility.easeOutCubic;
import static io.github.seraphina.nyx.client.utility.MathUtility.isInsideExclusive;
import static io.github.seraphina.nyx.client.utility.MathUtility.lerp;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetKey;
import static org.lwjgl.glfw.GLFW.glfwGetKeyName;

/**
 * Compact Alien-style ClickGUI. This screen owns its setting rendering model instead of
 * delegating values to the ArrayList ClickGUI component hierarchy.
 */
public final class AlineClickGuiUI extends LuaScreen {
    public static final AlineClickGuiUI INSTANCE = new AlineClickGuiUI();

    private static final float PANEL_WIDTH = 98.0F;
    private static final float PANEL_GAP = 5.0F;
    private static final float PANEL_HEADER_HEIGHT = 18.0F;
    private static final float MODULE_ROW_HEIGHT = 18.0F;
    private static final float VALUE_ROW_HEIGHT = 17.0F;
    private static final float COLOR_PICKER_HEIGHT = 47.0F;
    private static final float PANEL_START_X = 30.0F;
    private static final float PANEL_START_Y = 50.0F;
    private static final float SCREEN_MARGIN = 12.0F;
    private static final float CANVAS_WIDTH = PANEL_WIDTH * Category.values().length
        + PANEL_GAP * (Category.values().length - 1);
    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;
    private static final float MODULE_EXPAND_ANIMATION_SPEED = 16.0F;
    private static final float MODULE_TOGGLE_ANIMATION_SPEED = 18.0F;
    private static final long OPEN_ANIMATION_NANOS = 260_000_000L;
    private static final long CLOSE_ANIMATION_NANOS = 190_000_000L;
    private static final Comparator<Module> MODULE_NAME_ORDER =
        Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER);

    private final Map<Category, PanelState> panels = new EnumMap<>(Category.class);
    private final Map<Module, Boolean> expandedModules = new IdentityHashMap<>();
    private final Map<AbstractValue<?>, Boolean> expandedValues = new IdentityHashMap<>();
    private final Map<Module, ModuleAnimationState> moduleAnimations = new IdentityHashMap<>();
    private final List<ModuleRowLayout> renderedModuleRows = new ArrayList<>();
    private final List<SettingRowLayout> renderedSettingRows = new ArrayList<>();

    private float globalScale = 1.0F;
    private long transitionStartedAtNanos;
    private float transitionProgress = 1.0F;
    private float screenVisibility = 1.0F;
    private float screenScale = 1.0F;
    private long lastAnimationFrameNanos;
    private float animationFrameSeconds = DEFAULT_FRAME_SECONDS;
    private boolean closing;
    private boolean closingCompleted;
    @Nullable
    private Category focusedCategory;
    @Nullable
    private Category draggingCategory;
    private float dragOffsetX;
    private float dragOffsetY;
    @Nullable
    private KeyBindValue editingKeyBind;
    @Nullable
    private StringValue editingString;

    private AlineClickGuiUI() {
        super("nyxclient:ui/screen/aline.lua", Component.empty());
        for (Category category : Category.values()) {
            this.panels.put(category, new PanelState(category));
        }
    }

    @Override
    protected void init() {
        updateGlobalScale();
        layoutPanels();
        if (this.transitionStartedAtNanos == 0L && !this.closing) {
            beginOpenAnimation();
        }
        super.init();
    }

    public void beginOpenAnimation() {
        this.closing = false;
        this.closingCompleted = false;
        this.transitionProgress = 0.0F;
        this.screenVisibility = 0.0F;
        this.screenScale = 0.56F;
        this.transitionStartedAtNanos = System.nanoTime();
        this.lastAnimationFrameNanos = 0L;
        this.draggingCategory = null;
        clearEditors();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateGlobalScale();
        updateAnimationFrame();
        updateTransitionAnimation();
        if (finishClosingIfNeeded()) {
            return;
        }

        updateModuleAnimations();
        preparePanelGeometry();
        updateFocusedPanel(logicalMouseX(mouseX), logicalMouseY(mouseY));
        this.renderedModuleRows.clear();
        this.renderedSettingRows.clear();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void appendLuaState(Map<String, Object> state) {
        preparePanelGeometry();
        state.put("screen_width", this.width);
        state.put("screen_height", this.height);
        state.put("width", logicalScreenWidth());
        state.put("height", logicalScreenHeight());
        state.put("mouse_x", logicalMouseX(luaMouseX()));
        state.put("mouse_y", logicalMouseY(luaMouseY()));
        state.put("global_scale", this.globalScale);
        state.put("screen_visibility", this.screenVisibility);
        state.put("screen_scale", this.screenScale);
        state.put("interactive", isInteractive());

        List<Map<String, Object>> panelStates = new ArrayList<>();
        for (PanelState panel : panelsInRenderOrder()) {
            List<Module> modules = modulesByName(panel.category);
            if (modules.isEmpty()) {
                continue;
            }

            Map<String, Object> panelState = new LinkedHashMap<>();
            panelState.put("id", categoryId(panel.category));
            panelState.put("label", categoryLabel(panel.category));
            panelState.put("x", panel.x);
            panelState.put("y", panel.y);
            panelState.put("width", PANEL_WIDTH);
            panelState.put("height", panel.height);
            panelState.put("focused", panel.category == this.focusedCategory);
            panelState.put("content_height", panel.contentHeight);

            List<Map<String, Object>> moduleStates = new ArrayList<>();
            for (Module module : modules) {
                moduleStates.add(moduleState(module));
            }
            panelState.put("modules", moduleStates);
            panelStates.add(panelState);
        }
        state.put("panels", panelStates);
    }

    @Override
    protected boolean onLuaAction(String action, LuaValue payload) {
        return switch (action) {
            case "toggle_module" -> {
                Module module = moduleAt(payload.optint(-1));
                if (module != null && isInteractive()) {
                    module.toggle();
                    clearEditors();
                }
                yield true;
            }
            case "drag_panel" -> {
                dragPanel(payload);
                yield true;
            }
            case "setting_click" -> {
                handleSettingClick(payload);
                yield true;
            }
            case "number_drag" -> {
                updateNumberValue(payload);
                yield true;
            }
            case "enum_select" -> {
                selectEnumValue(payload);
                yield true;
            }
            case "color_edit" -> {
                updateColorValue(payload);
                yield true;
            }
            case "close" -> {
                beginCloseAnimation();
                yield true;
            }
            default -> false;
        };
    }

    @Override
    protected void renderLuaCustom(String name, LuaValue[] args) {
        switch (name) {
            case "module_bounds" -> registerModuleBounds(args);
            case "setting_bounds" -> registerSettingBounds(args);
            default -> {
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isInteractive()) {
            return true;
        }

        double mouseX = logicalMouseX(event.x());
        double mouseY = logicalMouseY(event.y());
        SettingRowLayout setting = settingAt(mouseX, mouseY);
        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && setting != null && isLeftShiftDown()
            && setting.value().isSerializable()) {
            resetValue(setting.value());
            clearEditors();
            return true;
        }

        if (event.button() == GLFW_MOUSE_BUTTON_RIGHT) {
            if (setting != null) {
                handleSettingRightClick(setting.value());
                return true;
            }

            ModuleRowLayout row = moduleRowAt(mouseX, mouseY);
            if (row != null) {
                this.focusedCategory = row.module().getCategory();
                if (canExpandModule(row.module())) {
                    setModuleExpanded(row.module(), !isExpanded(row.module()));
                }
                clearEditors();
                return true;
            }
        }

        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && setting == null) {
            clearEditors();
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW_MOUSE_BUTTON_LEFT) {
            this.draggingCategory = null;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.editingKeyBind != null) {
            if (!event.isEscape()) {
                this.editingKeyBind.setValue(
                    event.key() == GLFW_KEY_BACKSPACE || event.key() == GLFW_KEY_DELETE ? -1 : event.key()
                );
            }
            this.editingKeyBind = null;
            return true;
        }
        if (this.editingString != null && handleStringKey(this.editingString, event)) {
            return true;
        }
        if (event.isEscape()) {
            beginCloseAnimation();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.editingString != null && event.isAllowedChatCharacter()) {
            this.editingString.setValue(safeString(this.editingString) + event.codepointAsString());
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        this.draggingCategory = null;
        clearEditors();
        this.closing = false;
        this.closingCompleted = false;
        this.transitionStartedAtNanos = 0L;
        this.transitionProgress = 1.0F;
        this.screenVisibility = 1.0F;
        this.screenScale = 1.0F;
        this.lastAnimationFrameNanos = 0L;
        super.removed();
    }

    @Override
    public void onClose() {
        beginCloseAnimation();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    private Map<String, Object> moduleState(Module module) {
        ModuleAnimationState animation = moduleAnimation(module);
        List<AbstractValue<?>> visibleValues = visibleValues(module);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("index", moduleIndex(module));
        state.put("name", module.getName());
        state.put("description", module.getDescription());
        state.put("enabled_progress", animation.enabledProgress);
        state.put("expand_progress", animation.expandProgress);
        state.put("expandable", !visibleValues.isEmpty());
        state.put("expanded", isExpanded(module));

        List<Map<String, Object>> values = new ArrayList<>();
        float valuesHeight = 0.0F;
        for (AbstractValue<?> value : visibleValues) {
            float height = settingHeight(value);
            valuesHeight += height;
            values.add(valueState(module, value, height));
        }
        state.put("values", values);
        state.put("values_height", valuesHeight);
        state.put("row_height", MODULE_ROW_HEIGHT + valuesHeight * animation.expandProgress);
        return state;
    }

    private Map<String, Object> valueState(Module module, AbstractValue<?> value, float height) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("index", module.getValues().indexOf(value) + 1);
        state.put("name", value.getDisplayName());
        state.put("height", height);
        state.put("type", valueType(value));
        state.put("display", valueDisplay(value));
        state.put("editing", value == this.editingKeyBind || value == this.editingString);

        if (value instanceof BoolValue boolValue) {
            state.put("enabled", boolValue.getValue());
        } else if (value instanceof KeyBindValue keyBindValue) {
            state.put("bound", keyName(keyBindValue.getValue()));
        } else if (value instanceof IntValue intValue) {
            state.put("progress", numberProgress(intValue.getValue(), intValue.getMin(), intValue.getMax()));
        } else if (value instanceof DoubleValue doubleValue) {
            state.put("progress", numberProgress(doubleValue.getValue(), doubleValue.getMin(), doubleValue.getMax()));
        } else if (value instanceof EnumValue<?> enumValue) {
            boolean open = isValueExpanded(value);
            state.put("open", open);
            List<Map<String, Object>> options = new ArrayList<>();
            for (Enum<?> mode : enumValue.getModes()) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("value", mode.name());
                option.put("label", mode.toString());
                option.put("selected", mode == enumValue.getValue());
                options.add(option);
            }
            state.put("options", options);
        } else if (value instanceof ColorValue colorValue) {
            Color color = colorValue.getValue();
            float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
            state.put("open", isValueExpanded(value));
            state.put("color", color.getRGB());
            state.put("hue_color", Color.HSBtoRGB(hsb[0], 1.0F, 1.0F));
            state.put("saturation", hsb[1]);
            state.put("brightness", hsb[2]);
            state.put("hue", hsb[0]);
            state.put("alpha", color.getAlpha() / 255.0F);
            state.put("allow_alpha", colorValue.isAllowAlpha());
            state.put("alpha_transparent", color.getRGB() & 0x00FFFFFF);
            state.put("alpha_opaque", color.getRGB() | 0xFF000000);
            state.put("hue_steps", hueSteps());
        }
        return state;
    }

    private void registerModuleBounds(LuaValue[] args) {
        if (!luaBoolean(args, 5, false)) {
            return;
        }

        Module module = moduleAt(luaInt(args, 0, -1));
        if (module != null) {
            this.renderedModuleRows.add(new ModuleRowLayout(
                module,
                luaFloat(args, 1),
                luaFloat(args, 2),
                luaFloat(args, 3),
                luaFloat(args, 4)
            ));
        }
    }

    private void registerSettingBounds(LuaValue[] args) {
        Module module = moduleAt(luaInt(args, 0, -1));
        if (module == null) {
            return;
        }

        AbstractValue<?> value = valueAt(module, luaInt(args, 1, -1));
        float height = luaFloat(args, 5);
        if (value != null && value.isVisible() && height > 0.0F) {
            this.renderedSettingRows.add(new SettingRowLayout(
                module,
                value,
                luaFloat(args, 2),
                luaFloat(args, 3),
                luaFloat(args, 4),
                height
            ));
        }
    }

    private void dragPanel(LuaValue payload) {
        if (!isInteractive() || !payload.istable()) {
            return;
        }

        Category category = categoryById(payload.get("id").optjstring(""));
        PanelState panel = category == null ? null : this.panels.get(category);
        if (panel == null) {
            return;
        }

        float mouseX = logicalMouseX(luaMouseX());
        float mouseY = logicalMouseY(luaMouseY());
        if (this.draggingCategory != category) {
            this.draggingCategory = category;
            this.focusedCategory = category;
            this.dragOffsetX = mouseX - panel.x;
            this.dragOffsetY = mouseY - panel.y;
        }

        panel.x = mouseX - this.dragOffsetX;
        panel.y = mouseY - this.dragOffsetY;
        clampPanel(panel);
        clearEditors();
    }

    private void handleSettingClick(LuaValue payload) {
        if (!isInteractive()) {
            return;
        }

        AbstractValue<?> value = valueFromPayload(payload);
        if (value instanceof BoolValue boolValue) {
            boolValue.setValue(!boolValue.getValue());
            clearEditors();
        } else if (value instanceof EnumValue<?> enumValue) {
            cycleEnum(enumValue, 1);
            clearEditors();
        } else if (value instanceof KeyBindValue keyBindValue) {
            this.editingKeyBind = keyBindValue;
            this.editingString = null;
        } else if (value instanceof StringValue stringValue) {
            this.editingString = stringValue;
            this.editingKeyBind = null;
        } else if (value instanceof ButtonValue buttonValue) {
            buttonValue.press();
            clearEditors();
        } else if (value instanceof ColorValue) {
            setValueExpanded(value, !isValueExpanded(value));
            clearEditors();
        }
    }

    private void handleSettingRightClick(AbstractValue<?> value) {
        if (value instanceof EnumValue<?> || value instanceof ColorValue) {
            setValueExpanded(value, !isValueExpanded(value));
        } else if (value instanceof ButtonValue buttonValue) {
            buttonValue.rightClick();
        }
        clearEditors();
    }

    private void updateNumberValue(LuaValue payload) {
        if (!isInteractive()) {
            return;
        }

        AbstractValue<?> value = valueFromPayload(payload);
        float x = (float)payload.get("x").optdouble(0.0D);
        float width = Math.max(1.0F, (float)payload.get("width").optdouble(1.0D));
        float progress = clamp((logicalMouseX(luaMouseX()) - x) / width, 0.0F, 1.0F);
        if (value instanceof IntValue intValue && !(value instanceof KeyBindValue)) {
            int step = Math.max(1, Math.abs(intValue.getStep()));
            int range = intValue.getMax() - intValue.getMin();
            intValue.setValue(intValue.getMin() + Math.round(progress * range / step) * step);
        } else if (value instanceof DoubleValue doubleValue) {
            double raw = doubleValue.getMin() + (doubleValue.getMax() - doubleValue.getMin()) * progress;
            double step = Math.abs(doubleValue.getStep());
            if (step > 0.0D) {
                raw = doubleValue.getMin() + Math.round((raw - doubleValue.getMin()) / step) * step;
            }
            doubleValue.setValue(raw);
        }
    }

    private void selectEnumValue(LuaValue payload) {
        AbstractValue<?> value = valueFromPayload(payload);
        if (value instanceof EnumValue<?> enumValue) {
            enumValue.setMode(payload.get("mode").optjstring(""));
            setValueExpanded(value, false);
        }
    }

    private void updateColorValue(LuaValue payload) {
        if (!isInteractive()) {
            return;
        }

        AbstractValue<?> value = valueFromPayload(payload);
        if (!(value instanceof ColorValue colorValue)) {
            return;
        }

        String part = payload.get("part").optjstring("");
        float x = (float)payload.get("x").optdouble(0.0D);
        float y = (float)payload.get("y").optdouble(0.0D);
        float width = Math.max(1.0F, (float)payload.get("width").optdouble(1.0D));
        float height = Math.max(1.0F, (float)payload.get("height").optdouble(1.0D));
        Color current = colorValue.getValue();
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        float mouseX = logicalMouseX(luaMouseX());
        float mouseY = logicalMouseY(luaMouseY());

        switch (part) {
            case "sv" -> {
                float saturation = clamp((mouseX - x) / width, 0.0F, 1.0F);
                float brightness = 1.0F - clamp((mouseY - y) / height, 0.0F, 1.0F);
                int rgb = Color.HSBtoRGB(hsb[0], saturation, brightness);
                colorValue.setValue(new Color((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF, current.getAlpha()));
            }
            case "hue" -> {
                float hue = clamp((mouseY - y) / height, 0.0F, 1.0F);
                int rgb = Color.HSBtoRGB(hue, hsb[1], hsb[2]);
                colorValue.setValue(new Color((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF, current.getAlpha()));
            }
            case "alpha" -> {
                if (colorValue.isAllowAlpha()) {
                    int alpha = Math.round(clamp((mouseY - y) / height, 0.0F, 1.0F) * 255.0F);
                    colorValue.setValue(new Color(current.getRed(), current.getGreen(), current.getBlue(), alpha));
                }
            }
            default -> {
            }
        }
    }

    @Nullable
    private SettingRowLayout settingAt(double mouseX, double mouseY) {
        for (int index = this.renderedSettingRows.size() - 1; index >= 0; index--) {
            SettingRowLayout row = this.renderedSettingRows.get(index);
            if (isInsideExclusive(mouseX, mouseY, row.x(), row.y(), row.width(), row.height())) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    private ModuleRowLayout moduleRowAt(double mouseX, double mouseY) {
        for (int index = this.renderedModuleRows.size() - 1; index >= 0; index--) {
            ModuleRowLayout row = this.renderedModuleRows.get(index);
            if (isInsideExclusive(mouseX, mouseY, row.x(), row.y(), row.width(), row.height())) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    private AbstractValue<?> valueFromPayload(LuaValue payload) {
        if (!payload.istable()) {
            return null;
        }
        Module module = moduleAt(payload.get("module").optint(-1));
        return module == null ? null : valueAt(module, payload.get("value").optint(-1));
    }

    private static <T> void resetValue(AbstractValue<T> value) {
        value.setValue(value.getDefaultValue());
    }

    private List<AbstractValue<?>> visibleValues(Module module) {
        List<AbstractValue<?>> values = new ArrayList<>();
        for (AbstractValue<?> value : module.getValues()) {
            if (value.isVisible()) {
                values.add(value);
            }
        }
        return values;
    }

    private float settingHeight(AbstractValue<?> value) {
        if (value instanceof EnumValue<?> enumValue && isValueExpanded(value)) {
            return VALUE_ROW_HEIGHT + enumValue.getModes().length * VALUE_ROW_HEIGHT;
        }
        if (value instanceof ColorValue && isValueExpanded(value)) {
            return VALUE_ROW_HEIGHT + COLOR_PICKER_HEIGHT;
        }
        return VALUE_ROW_HEIGHT;
    }

    private boolean canExpandModule(Module module) {
        return !visibleValues(module).isEmpty();
    }

    private void setModuleExpanded(Module module, boolean expanded) {
        moduleAnimation(module);
        if (expanded && canExpandModule(module)) {
            this.expandedModules.put(module, true);
        } else {
            this.expandedModules.remove(module);
            for (AbstractValue<?> value : module.getValues()) {
                this.expandedValues.remove(value);
            }
        }
    }

    private boolean isExpanded(Module module) {
        return this.expandedModules.getOrDefault(module, false);
    }

    private boolean isValueExpanded(AbstractValue<?> value) {
        return this.expandedValues.getOrDefault(value, false);
    }

    private void setValueExpanded(AbstractValue<?> value, boolean expanded) {
        if (expanded) {
            this.expandedValues.put(value, true);
        } else {
            this.expandedValues.remove(value);
        }
    }

    private ModuleAnimationState moduleAnimation(Module module) {
        return this.moduleAnimations.computeIfAbsent(module, ModuleAnimationState::new);
    }

    private void updateModuleAnimations() {
        for (Module module : ModuleManager.getModules()) {
            ModuleAnimationState animation = moduleAnimation(module);
            boolean expandable = canExpandModule(module);
            if (!expandable) {
                this.expandedModules.remove(module);
            }
            animation.expandProgress = animateExp(
                animation.expandProgress,
                expandable && isExpanded(module) ? 1.0F : 0.0F,
                MODULE_EXPAND_ANIMATION_SPEED,
                this.animationFrameSeconds
            );
            animation.enabledProgress = animateExp(
                animation.enabledProgress,
                module.isEnabled() ? 1.0F : 0.0F,
                MODULE_TOGGLE_ANIMATION_SPEED,
                this.animationFrameSeconds
            );
        }
    }

    private void updateAnimationFrame() {
        long now = System.nanoTime();
        if (this.lastAnimationFrameNanos == 0L) {
            this.animationFrameSeconds = DEFAULT_FRAME_SECONDS;
        } else {
            this.animationFrameSeconds = clamp(
                (now - this.lastAnimationFrameNanos) / 1_000_000_000.0F,
                0.0F,
                MAX_FRAME_SECONDS
            );
        }
        this.lastAnimationFrameNanos = now;
    }

    private void updateTransitionAnimation() {
        if (this.transitionStartedAtNanos == 0L) {
            this.transitionProgress = 1.0F;
            this.screenVisibility = 1.0F;
            this.screenScale = 1.0F;
            return;
        }

        long duration = this.closing ? CLOSE_ANIMATION_NANOS : OPEN_ANIMATION_NANOS;
        float raw = clamp(
            (System.nanoTime() - this.transitionStartedAtNanos) / (float)duration,
            0.0F,
            1.0F
        );
        this.transitionProgress = raw;
        if (this.closing) {
            this.screenVisibility = 1.0F - easeOutCubic(raw);
            this.screenScale = lerp(0.88F, 1.0F, this.screenVisibility);
            if (raw >= 1.0F) {
                this.closingCompleted = true;
            }
        } else {
            this.screenVisibility = easeOutCubic(raw);
            this.screenScale = lerp(0.56F, 1.0F, easeOutBack(raw));
        }
    }

    private boolean finishClosingIfNeeded() {
        if (!this.closingCompleted) {
            return false;
        }

        this.closingCompleted = false;
        if (this.minecraft != null && this.minecraft.screen == this) {
            this.minecraft.setScreen(null);
        }
        return true;
    }

    private void beginCloseAnimation() {
        if (this.closing) {
            return;
        }

        this.closing = true;
        this.closingCompleted = false;
        this.transitionProgress = 0.0F;
        this.transitionStartedAtNanos = System.nanoTime();
        this.lastAnimationFrameNanos = 0L;
        this.draggingCategory = null;
        clearEditors();
    }

    private boolean isInteractive() {
        return !this.closing && this.transitionProgress >= 1.0F;
    }

    private void updateGlobalScale() {
        float availableWidth = Math.max(1.0F, this.width - SCREEN_MARGIN * 2.0F);
        this.globalScale = Math.min(1.0F, availableWidth / (CANVAS_WIDTH + SCREEN_MARGIN * 2.0F));
        this.globalScale = Math.max(0.25F, this.globalScale);
    }

    private void layoutPanels() {
        List<Category> visibleCategories = visibleCategories();
        float x = PANEL_START_X;
        float y = PANEL_START_Y;
        for (Category category : visibleCategories) {
            PanelState panel = this.panels.get(category);
            panel.x = x;
            panel.y = y;
            panel.height = PANEL_HEADER_HEIGHT + maxPanelBodyHeight();
            panel.contentHeight = maxPanelBodyHeight();
            x += PANEL_WIDTH + PANEL_GAP;
        }
        this.focusedCategory = visibleCategories.isEmpty() ? null : visibleCategories.getFirst();
    }

    private void preparePanelGeometry() {
        for (Category category : visibleCategories()) {
            PanelState panel = this.panels.get(category);
            float contentHeight = 0.0F;
            for (Module module : modulesByName(category)) {
                float valuesHeight = 0.0F;
                for (AbstractValue<?> value : visibleValues(module)) {
                    valuesHeight += settingHeight(value);
                }
                contentHeight += MODULE_ROW_HEIGHT + valuesHeight * moduleAnimation(module).expandProgress;
            }
            panel.contentHeight = contentHeight;
            panel.height = PANEL_HEADER_HEIGHT + Math.min(
                maxPanelBodyHeight(panel.y),
                Math.max(MODULE_ROW_HEIGHT, contentHeight)
            );
            clampPanel(panel);
        }
    }

    private float maxPanelBodyHeight() {
        return Math.max(MODULE_ROW_HEIGHT, logicalScreenHeight() - PANEL_START_Y - SCREEN_MARGIN);
    }

    private float maxPanelBodyHeight(float panelY) {
        return Math.max(
            MODULE_ROW_HEIGHT,
            logicalScreenHeight() - panelY - PANEL_HEADER_HEIGHT - SCREEN_MARGIN
        );
    }

    private void updateFocusedPanel(double mouseX, double mouseY) {
        if (this.draggingCategory != null) {
            this.focusedCategory = this.draggingCategory;
            return;
        }

        List<PanelState> ordered = panelsInRenderOrder();
        for (int index = ordered.size() - 1; index >= 0; index--) {
            PanelState panel = ordered.get(index);
            if (isInsideExclusive(mouseX, mouseY, panel.x, panel.y, PANEL_WIDTH, panel.height)) {
                this.focusedCategory = panel.category;
                return;
            }
        }
    }

    private void clampPanel(PanelState panel) {
        float screenWidth = logicalScreenWidth();
        float screenHeight = logicalScreenHeight();
        panel.x = clampPanelAxis(panel.x, PANEL_WIDTH, screenWidth);
        panel.y = clampPanelAxis(panel.y, panel.height, screenHeight);
    }

    private static float clampPanelAxis(float position, float panelSize, float screenSize) {
        if (screenSize <= panelSize + SCREEN_MARGIN * 2.0F) {
            return (screenSize - panelSize) * 0.5F;
        }
        return clamp(position, SCREEN_MARGIN, screenSize - panelSize - SCREEN_MARGIN);
    }

    private List<Category> visibleCategories() {
        List<Category> categories = new ArrayList<>();
        for (Category category : Category.values()) {
            if (!ModuleManager.getModules(category).isEmpty()) {
                categories.add(category);
            }
        }
        return categories;
    }

    private static List<Module> modulesByName(Category category) {
        List<Module> modules = new ArrayList<>(ModuleManager.getModules(category));
        modules.sort(MODULE_NAME_ORDER);
        return modules;
    }

    private List<PanelState> panelsInRenderOrder() {
        List<PanelState> ordered = new ArrayList<>();
        for (Category category : visibleCategories()) {
            ordered.add(this.panels.get(category));
        }
        ordered.sort(Comparator.comparing(panel -> panel.category == this.focusedCategory));
        return ordered;
    }

    private float logicalMouseX(double mouseX) {
        return (float)(mouseX / this.globalScale);
    }

    private float logicalMouseY(double mouseY) {
        return (float)(mouseY / this.globalScale);
    }

    private float logicalScreenWidth() {
        return this.width / this.globalScale;
    }

    private float logicalScreenHeight() {
        return this.height / this.globalScale;
    }

    private boolean isLeftShiftDown() {
        return this.minecraft != null
            && this.minecraft.getWindow() != null
            && glfwGetKey(this.minecraft.getWindow().handle(), GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS;
    }

    private void clearEditors() {
        this.editingKeyBind = null;
        this.editingString = null;
    }

    private boolean handleStringKey(StringValue value, KeyEvent event) {
        if (event.isEscape() || event.key() == GLFW_KEY_ENTER || event.key() == GLFW_KEY_KP_ENTER) {
            this.editingString = null;
            return true;
        }
        if (event.isPaste()) {
            String clipboard = this.minecraft == null ? "" : this.minecraft.keyboardHandler.getClipboard();
            value.setValue(safeString(value) + clipboard.replace("\r", "").replace("\n", ""));
            return true;
        }
        if (event.isSelectAll()) {
            value.setValue("");
            return true;
        }
        if (event.key() == GLFW_KEY_BACKSPACE || event.key() == GLFW_KEY_DELETE) {
            value.setValue(deleteLastCodePoint(safeString(value)));
            return true;
        }
        return false;
    }

    private int moduleIndex(Module target) {
        List<Module> modules = List.copyOf(ModuleManager.getModules());
        int index = modules.indexOf(target);
        return index < 0 ? -1 : index + 1;
    }

    @Nullable
    private Module moduleAt(int index) {
        List<Module> modules = List.copyOf(ModuleManager.getModules());
        return index <= 0 || index > modules.size() ? null : modules.get(index - 1);
    }

    @Nullable
    private static AbstractValue<?> valueAt(Module module, int index) {
        List<AbstractValue<?>> values = module.getValues();
        return index <= 0 || index > values.size() ? null : values.get(index - 1);
    }

    @Nullable
    private static Category categoryById(String id) {
        for (Category category : Category.values()) {
            if (categoryId(category).equalsIgnoreCase(id)) {
                return category;
            }
        }
        return null;
    }

    private static String valueType(AbstractValue<?> value) {
        if (value instanceof BoolValue) {
            return "bool";
        }
        if (value instanceof ButtonValue) {
            return "button";
        }
        if (value instanceof ColorValue) {
            return "color";
        }
        if (value instanceof KeyBindValue) {
            return "keybind";
        }
        if (value instanceof IntValue || value instanceof DoubleValue) {
            return "number";
        }
        if (value instanceof EnumValue<?>) {
            return "enum";
        }
        if (value instanceof StringValue) {
            return "string";
        }
        return "simple";
    }

    private static String valueDisplay(AbstractValue<?> value) {
        if (value instanceof BoolValue boolValue) {
            return boolValue.getValue() ? "On" : "Off";
        }
        if (value instanceof KeyBindValue keyBindValue) {
            return keyName(keyBindValue.getValue());
        }
        if (value instanceof IntValue intValue) {
            return Integer.toString(intValue.getValue());
        }
        if (value instanceof DoubleValue doubleValue) {
            return formatNumber(doubleValue.getValue());
        }
        if (value instanceof EnumValue<?> enumValue) {
            return enumValue.getValue().toString();
        }
        if (value instanceof StringValue stringValue) {
            return safeString(stringValue);
        }
        if (value instanceof ButtonValue) {
            return "Run";
        }
        Object current = value.getValue();
        return current == null ? "" : String.valueOf(current);
    }

    private static float numberProgress(double current, double min, double max) {
        if (max <= min) {
            return 0.0F;
        }
        return clamp((float)((current - min) / (max - min)), 0.0F, 1.0F);
    }

    private static List<Integer> hueSteps() {
        List<Integer> colors = new ArrayList<>(16);
        for (int index = 0; index < 16; index++) {
            colors.add(Color.HSBtoRGB(index / 15.0F, 1.0F, 1.0F));
        }
        return colors;
    }

    private static void cycleEnum(EnumValue<?> value, int direction) {
        Enum<?>[] modes = value.getModes();
        if (modes.length == 0) {
            return;
        }
        int next = Math.floorMod(value.getModeIndex() + direction, modes.length);
        value.setMode(modes[next].name());
    }

    private static String keyName(int key) {
        if (key < 0) {
            return "None";
        }
        String name = glfwGetKeyName(key, 0);
        return name == null || name.isBlank() ? "Key " + key : name.toUpperCase(Locale.ROOT);
    }

    private static String formatNumber(double value) {
        if (Math.rint(value) == value) {
            return Long.toString(Math.round(value));
        }
        String formatted = String.format(Locale.ROOT, "%.3f", value);
        int end = formatted.length();
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        return formatted.substring(0, end);
    }

    private static String safeString(StringValue value) {
        return value.getValue() == null ? "" : value.getValue();
    }

    private static String deleteLastCodePoint(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, text.offsetByCodePoints(text.length(), -1));
    }

    private static String categoryId(Category category) {
        return category.name().toLowerCase(Locale.ROOT);
    }

    private static String categoryLabel(Category category) {
        String id = categoryId(category);
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    private static float luaFloat(LuaValue[] args, int index) {
        return luaFloat(args, index, 0.0F);
    }

    private static float luaFloat(LuaValue[] args, int index, float fallback) {
        return index >= 0 && index < args.length ? (float)args[index].optdouble(fallback) : fallback;
    }

    private static int luaInt(LuaValue[] args, int index, int fallback) {
        return index >= 0 && index < args.length ? args[index].optint(fallback) : fallback;
    }

    private static boolean luaBoolean(LuaValue[] args, int index, boolean fallback) {
        return index >= 0 && index < args.length ? args[index].optboolean(fallback) : fallback;
    }

    private static final class PanelState {
        private final Category category;
        private float x;
        private float y;
        private float height = PANEL_HEADER_HEIGHT + MODULE_ROW_HEIGHT;
        private float contentHeight = MODULE_ROW_HEIGHT;

        private PanelState(Category category) {
            this.category = category;
        }
    }

    private static final class ModuleAnimationState {
        private float enabledProgress;
        private float expandProgress;

        private ModuleAnimationState(Module module) {
            this.enabledProgress = module.isEnabled() ? 1.0F : 0.0F;
        }
    }

    private record ModuleRowLayout(Module module, float x, float y, float width, float height) {
    }

    private record SettingRowLayout(Module module, AbstractValue<?> value, float x, float y, float width, float height) {
    }
}
