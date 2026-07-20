package io.github.seraphina.nyx.client.ui.clickgui;

import io.github.seraphina.nyx.client.manager.ModuleManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.ui.LuaScreen;
import io.github.seraphina.nyx.client.ui.clickgui.component.BoolComponent;
import io.github.seraphina.nyx.client.ui.clickgui.component.ButtonComponent;
import io.github.seraphina.nyx.client.ui.clickgui.component.ColorComponent;
import io.github.seraphina.nyx.client.ui.clickgui.component.DoubleComponent;
import io.github.seraphina.nyx.client.ui.clickgui.component.EnumComponent;
import io.github.seraphina.nyx.client.ui.clickgui.component.IntComponent;
import io.github.seraphina.nyx.client.ui.clickgui.component.SimpleValueComponent;
import io.github.seraphina.nyx.client.ui.clickgui.component.StringComponent;
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
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetKey;

public class ArrayListClickGui extends LuaScreen {
    public static final ArrayListClickGui INSTANCE = new ArrayListClickGui();

    private static final float PANEL_WIDTH = 120.0F;
    private static final float PANEL_GAP = 8.0F;
    private static final float PANEL_HEADER_HEIGHT = 20.0F;
    private static final float MODULE_ROW_HEIGHT = 20.0F;
    private static final float VALUE_VERTICAL_PADDING = 6.0F;
    private static final int ACCENT_COLOR = 0xFF2DE8CA;
    private static final float MAX_PANEL_BODY_HEIGHT = 240.0F;
    private static final float SCREEN_MARGIN = 12.0F;
    private static final float CANVAS_WIDTH = PANEL_WIDTH * Category.values().length
        + PANEL_GAP * (Category.values().length - 1);
    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;
    private static final float MODULE_EXPAND_ANIMATION_SPEED = 14.0F;
    private static final float MODULE_TOGGLE_ANIMATION_SPEED = 17.0F;
    private static final long OPEN_ANIMATION_NANOS = 320_000_000L;
    private static final long CLOSE_ANIMATION_NANOS = 220_000_000L;

    private final Map<Category, PanelState> panels = new EnumMap<>(Category.class);
    private final Map<Module, Boolean> expandedModules = new IdentityHashMap<>();
    private final Map<Module, ModuleAnimationState> moduleAnimations = new IdentityHashMap<>();
    private final Map<AbstractValue<?>, AbstractComponent> valueComponents = new IdentityHashMap<>();
    private final List<ModuleRowLayout> renderedModuleRows = new ArrayList<>();
    private final List<ValueComponentLayout> renderedValueComponents = new ArrayList<>();

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
    private AbstractComponent capturedComponent;

    private ArrayListClickGui() {
        super("nyxclient:ui/screen/arraylist.lua", Component.empty());
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
        this.screenScale = 0.4F;
        this.transitionStartedAtNanos = System.nanoTime();
        this.lastAnimationFrameNanos = 0L;
        this.draggingCategory = null;
        this.capturedComponent = null;
        blurComponentsExcept(null);
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
        this.renderedValueComponents.clear();
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
            List<Module> modules = ModuleManager.getModules(panel.category);
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
                }
                yield true;
            }
            case "drag_panel" -> {
                dragPanel(payload);
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
            case "value" -> renderValueComponent(args);
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
        AbstractComponent component = componentAt(mouseX, mouseY);
        if (component != null) {
            if (event.button() == GLFW_MOUSE_BUTTON_LEFT && isLeftShiftDown() && component.value().isSerializable()) {
                blurComponentsExcept(null);
                resetValue(component.value());
                return true;
            }

            blurComponentsExcept(component);
            if (component.mouseClicked(mouseX, mouseY, event.button())) {
                this.capturedComponent = component;
                return true;
            }
        } else if (event.button() == GLFW_MOUSE_BUTTON_LEFT) {
            blurComponentsExcept(null);
        }

        ModuleRowLayout row = moduleRowAt(mouseX, mouseY);
        if (row != null && event.button() == GLFW_MOUSE_BUTTON_RIGHT) {
            this.focusedCategory = row.module().getCategory();
            if (canExpandModule(row.module())) {
                setModuleExpanded(row.module(), !isExpanded(row.module()));
            }
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = false;
        if (this.capturedComponent != null) {
            AbstractComponent component = this.capturedComponent;
            this.capturedComponent = null;
            handled = component.mouseReleased(
                logicalMouseX(event.x()),
                logicalMouseY(event.y()),
                event.button()
            );
        }

        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && this.draggingCategory != null) {
            this.draggingCategory = null;
            handled = true;
        }
        return super.mouseReleased(event) || handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!isInteractive()) {
            return true;
        }

        if (this.capturedComponent != null && this.capturedComponent.mouseDragged(
            logicalMouseX(event.x()),
            logicalMouseY(event.y()),
            event.button(),
            dragX / this.globalScale,
            dragY / this.globalScale
        )) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isInteractive()) {
            return true;
        }

        double logicalMouseX = logicalMouseX(mouseX);
        double logicalMouseY = logicalMouseY(mouseY);
        AbstractComponent component = componentAt(logicalMouseX, logicalMouseY);
        if (component != null && component.mouseScrolled(logicalMouseX, logicalMouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        for (AbstractComponent component : this.valueComponents.values()) {
            if (component.keyPressed(event)) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (AbstractComponent component : this.valueComponents.values()) {
            if (component.charTyped(event)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void tick() {
        super.tick();
        for (AbstractComponent component : this.valueComponents.values()) {
            component.tick();
        }
    }

    @Override
    public void removed() {
        this.draggingCategory = null;
        this.capturedComponent = null;
        blurComponentsExcept(null);
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
        float valuesHeight = visibleValues.isEmpty() ? 0.0F : VALUE_VERTICAL_PADDING;
        for (AbstractValue<?> value : visibleValues) {
            AbstractComponent component = componentFor(value);
            float height = component.getHeight();
            valuesHeight += height;
            Map<String, Object> valueState = new LinkedHashMap<>();
            valueState.put("index", module.getValues().indexOf(value) + 1);
            valueState.put("height", height);
            values.add(valueState);
        }
        state.put("values", values);
        state.put("values_height", valuesHeight);
        state.put("row_height", MODULE_ROW_HEIGHT + valuesHeight * animation.expandProgress);
        return state;
    }

    private void registerModuleBounds(LuaValue[] args) {
        if (!luaBoolean(args, 5, false)) {
            return;
        }

        Module module = moduleAt(luaInt(args, 0, -1));
        if (module == null) {
            return;
        }
        this.renderedModuleRows.add(new ModuleRowLayout(
            module,
            luaFloat(args, 1),
            luaFloat(args, 2),
            luaFloat(args, 3),
            luaFloat(args, 4)
        ));
    }

    private void renderValueComponent(LuaValue[] args) {
        Module module = moduleAt(luaInt(args, 0, -1));
        if (module == null) {
            return;
        }

        AbstractValue<?> value = valueAt(module, luaInt(args, 1, -1));
        if (value == null || !value.isVisible()) {
            return;
        }

        float x = luaFloat(args, 2);
        float y = luaFloat(args, 3);
        float width = luaFloat(args, 4);
        float visibleHeight = luaFloat(args, 5);
        boolean interactive = luaBoolean(args, 6, false);
        AbstractComponent component = componentFor(value);
        component.render(
            x,
            y,
            width,
            Math.round(logicalMouseX(luaMouseX())),
            Math.round(logicalMouseY(luaMouseY())),
            0.0F
        );
        if (interactive && visibleHeight > 0.0F) {
            this.renderedValueComponents.add(new ValueComponentLayout(
                module,
                component,
                x,
                y,
                width,
                Math.min(component.getHeight(), visibleHeight)
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
        blurComponentsExcept(null);
    }

    @Nullable
    private AbstractComponent componentAt(double mouseX, double mouseY) {
        for (int index = this.renderedValueComponents.size() - 1; index >= 0; index--) {
            ValueComponentLayout layout = this.renderedValueComponents.get(index);
            PanelState panel = this.panels.get(layout.module().getCategory());
            if (panel == null || !insidePanelBody(mouseX, mouseY, panel)) {
                continue;
            }
            if (isInsideExclusive(mouseX, mouseY, layout.x(), layout.y(), layout.width(), layout.height())) {
                layout.component().setBounds(layout.x(), layout.y(), layout.width());
                return layout.component();
            }
        }
        return null;
    }

    @Nullable
    private ModuleRowLayout moduleRowAt(double mouseX, double mouseY) {
        for (int index = this.renderedModuleRows.size() - 1; index >= 0; index--) {
            ModuleRowLayout row = this.renderedModuleRows.get(index);
            PanelState panel = this.panels.get(row.module().getCategory());
            if (panel == null || !insidePanelBody(mouseX, mouseY, panel)) {
                continue;
            }
            if (isInsideExclusive(
                mouseX,
                mouseY,
                row.x(),
                row.y(),
                row.width(),
                Math.min(MODULE_ROW_HEIGHT, row.height())
            )) {
                return row;
            }
        }
        return null;
    }

    private AbstractComponent componentFor(AbstractValue<?> value) {
        AbstractComponent component = this.valueComponents.computeIfAbsent(value, this::createComponent);
        component.setAccentColor(ACCENT_COLOR);
        component.setCompactLayout(true);
        return component;
    }

    private AbstractComponent createComponent(AbstractValue<?> value) {
        if (value instanceof BoolValue boolValue) {
            return new BoolComponent(boolValue);
        }
        if (value instanceof ButtonValue buttonValue) {
            return new ButtonComponent(buttonValue);
        }
        if (value instanceof ColorValue colorValue) {
            return new ColorComponent(colorValue);
        }
        if (value instanceof DoubleValue doubleValue) {
            return new DoubleComponent(doubleValue);
        }
        if (value instanceof KeyBindValue keyBindValue) {
            return new SimpleValueComponent(keyBindValue);
        }
        if (value instanceof IntValue intValue) {
            return new IntComponent(intValue);
        }
        if (value instanceof StringValue stringValue) {
            return new StringComponent(stringValue);
        }
        if (value instanceof EnumValue<?> enumValue) {
            return new EnumComponent(enumValue);
        }
        return new SimpleValueComponent(value);
    }

    private void blurComponentsExcept(@Nullable AbstractComponent keepFocused) {
        if (this.capturedComponent != keepFocused) {
            this.capturedComponent = null;
        }
        for (AbstractComponent component : this.valueComponents.values()) {
            if (component != keepFocused) {
                component.blur();
            }
        }
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

    private boolean canExpandModule(Module module) {
        return !visibleValues(module).isEmpty();
    }

    private void setModuleExpanded(Module module, boolean expanded) {
        moduleAnimation(module);
        if (expanded && canExpandModule(module)) {
            this.expandedModules.put(module, true);
        } else {
            this.expandedModules.remove(module);
        }
    }

    private boolean isExpanded(Module module) {
        return this.expandedModules.getOrDefault(module, false);
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
            this.screenScale = lerp(0.82F, 1.0F, this.screenVisibility);
            if (raw >= 1.0F) {
                this.closingCompleted = true;
            }
        } else {
            this.screenVisibility = easeOutCubic(raw);
            this.screenScale = lerp(0.4F, 1.0F, easeOutBack(raw));
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
        this.capturedComponent = null;
        blurComponentsExcept(null);
    }

    private boolean isInteractive() {
        return !this.closing && this.transitionProgress >= 1.0F;
    }

    private void updateGlobalScale() {
        float availableWidth = Math.max(1.0F, this.width - SCREEN_MARGIN * 2.0F);
        this.globalScale = Math.min(1.0F, availableWidth / CANVAS_WIDTH);
        this.globalScale = Math.max(0.25F, this.globalScale);
    }

    private void layoutPanels() {
        List<Category> visibleCategories = visibleCategories();
        float totalWidth = visibleCategories.size() * PANEL_WIDTH
            + Math.max(0, visibleCategories.size() - 1) * PANEL_GAP;
        float x = (logicalScreenWidth() - totalWidth) * 0.5F;
        float y = 36.0F;
        for (Category category : visibleCategories) {
            PanelState panel = this.panels.get(category);
            panel.x = x;
            panel.y = y;
            panel.height = PANEL_HEADER_HEIGHT + MAX_PANEL_BODY_HEIGHT;
            panel.contentHeight = MAX_PANEL_BODY_HEIGHT;
            x += PANEL_WIDTH + PANEL_GAP;
        }
        this.focusedCategory = visibleCategories.isEmpty() ? null : visibleCategories.getFirst();
    }

    private void preparePanelGeometry() {
        for (Category category : visibleCategories()) {
            PanelState panel = this.panels.get(category);
            float contentHeight = 0.0F;
            for (Module module : ModuleManager.getModules(category)) {
                float valuesHeight = 0.0F;
                List<AbstractValue<?>> values = visibleValues(module);
                if (!values.isEmpty()) {
                    valuesHeight = VALUE_VERTICAL_PADDING;
                    for (AbstractValue<?> value : values) {
                        valuesHeight += componentFor(value).getHeight();
                    }
                }
                contentHeight += MODULE_ROW_HEIGHT + valuesHeight * moduleAnimation(module).expandProgress;
            }
            panel.contentHeight = contentHeight;
            panel.height = PANEL_HEADER_HEIGHT + Math.min(MAX_PANEL_BODY_HEIGHT, Math.max(MODULE_ROW_HEIGHT, contentHeight));
            clampPanel(panel);
        }
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

    private boolean insidePanelBody(double mouseX, double mouseY, PanelState panel) {
        return isInsideExclusive(
            mouseX,
            mouseY,
            panel.x,
            panel.y + PANEL_HEADER_HEIGHT,
            PANEL_WIDTH,
            panel.height - PANEL_HEADER_HEIGHT
        );
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
        private float height = PANEL_HEADER_HEIGHT + MAX_PANEL_BODY_HEIGHT;
        private float contentHeight = MAX_PANEL_BODY_HEIGHT;

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

    private record ValueComponentLayout(
        Module module,
        AbstractComponent component,
        float x,
        float y,
        float width,
        float height
    ) {
    }
}
