package io.github.seraphina.nyx.client.ui.clickgui;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.manager.FontManager;
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
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.utility.web.MicrosoftUtility;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaValue;

import javax.annotation.Nullable;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.seraphina.nyx.client.utility.MathUtility.animateExp;
import static io.github.seraphina.nyx.client.utility.MathUtility.clamp;
import static io.github.seraphina.nyx.client.utility.MathUtility.easeOutCubic;
import static io.github.seraphina.nyx.client.utility.MathUtility.isInsideExclusive;
import static io.github.seraphina.nyx.client.utility.MathUtility.lerp;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetKey;

public class ClickGuiUI extends LuaScreen {
    public static final ClickGuiUI INSTANCE = new ClickGuiUI();
    private static final float PANEL_WIDTH = 820.0F;
    private static final float PANEL_HEIGHT = 540.0F;
    private static final float UI_SCALE = 1.7F;
    private static final float MIN_MARGIN = 16.0F;
    private static final float SIDEBAR_WIDTH = 220.0F;
    private static final float SIDEBAR_WIDTH_COMPACT = 172.0F;
    private static final float HEADER_HEIGHT = 64.0F;
    private static final int AVATAR_TEXTURE_SIZE = 128;
    private static final int AVATAR_SUPERSAMPLE = 4;
    private static final long CATEGORY_SWITCH_ANIMATION_NANOS = 260_000_000L;
    private static final long SCREEN_TRANSITION_ANIMATION_NANOS = 180_000_000L;
    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;
    private static final float MODULE_EXPAND_ANIMATION_SPEED = 13.0F;
    private static final float MODULE_TOGGLE_ANIMATION_SPEED = 16.0F;
    private static final float SCREEN_TRANSITION_MIN_SCALE = 0.86F;
    private static final int BORDER = 0x1AFFFFFF;
    private static final int CONTROL_HOVER = 0xFF181B24;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF4B5263;

    private static final AtomicInteger AVATAR_TEXTURE_IDS = new AtomicInteger();

    private final Map<Module, Boolean> expandedModules = new IdentityHashMap<>();
    private final Map<Module, ModuleAnimationState> moduleAnimations = new IdentityHashMap<>();
    private final Map<AbstractValue<?>, AbstractComponent> valueComponents = new IdentityHashMap<>();
    private final List<ModuleRowLayout> renderedModuleRows = new ArrayList<>();
    private final List<ValueComponentLayout> renderedValueComponents = new ArrayList<>();
    private final String userName;
    @Nullable
    private final BufferedImage avatarImage;

    private Category selectedCategory = Category.COMBAT;
    @Nullable
    private Category previousCategory;
    private long categorySwitchStartedAtNanos;
    private int categorySwitchDirection = 1;
    private float categorySwitchProgress = 1.0F;
    private long screenTransitionStartedAtNanos;
    private float screenTransitionProgress = 1.0F;
    private boolean closing;
    private boolean closingCompleted;
    private long lastAnimationFrameNanos;
    private float animationFrameSeconds = DEFAULT_FRAME_SECONDS;
    private float panelOffsetX;
    private float panelOffsetY;
    private boolean draggingPanel;
    private float dragOffsetX;
    private float dragOffsetY;
    private float dragBaseX;
    private float dragBaseY;
    @Nullable
    private AbstractComponent capturedComponent;
    @Nullable
    private DynamicTexture avatarTexture;

    public ClickGuiUI() {
        super("nyxclient:ui/screen/clickgui.lua", Component.empty());
        this.userName = MicrosoftUtility.getCurrentComputerUserName();
        this.avatarImage = loadAvatarImage();
    }

    @Override
    protected void init() {
        ensureVisibleCategory();
        clampPanelOffsets();
        if (this.screenTransitionStartedAtNanos == 0L && !this.closing) {
            beginOpenAnimation();
        }
        super.init();
    }

    public void beginOpenAnimation() {
        this.closing = false;
        this.closingCompleted = false;
        this.screenTransitionProgress = 0.0F;
        this.screenTransitionStartedAtNanos = System.nanoTime();
        this.lastAnimationFrameNanos = 0L;
        this.draggingPanel = false;
        this.capturedComponent = null;
        blurComponentsExcept(null);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ensureVisibleCategory();
        clampPanelOffsets();
        updateAnimationFrame();
        updateScreenTransitionAnimation();
        if (finishClosingIfNeeded()) {
            return;
        }
        updateCategorySwitchAnimation();
        updateModuleAnimations();
        this.renderedModuleRows.clear();
        this.renderedValueComponents.clear();
        float coordinateScale = coordinateScale();
        Render2DUtility.withGuiGraphics(guiGraphics, () ->
            Render2DUtility.withScale(
                1.0F / coordinateScale,
                1.0F / coordinateScale,
                0.0F,
                0.0F,
                () -> super.render(guiGraphics, mouseX, mouseY, partialTick)
            )
        );
    }

    @Override
    protected void appendLuaState(Map<String, Object> state) {
        state.put("width", fixedScreenWidth());
        state.put("height", fixedScreenHeight());
        state.put("mouse_x", fixedMouseX(luaMouseX()));
        state.put("mouse_y", fixedMouseY(luaMouseY()));
        state.put("coordinate_scale", coordinateScale());
        state.put("client_name", NyxClient.CLIENT_NAME);
        state.put("version", NyxClient.VERSION);
        state.put("user_name", this.userName == null || this.userName.isBlank() ? "User" : this.userName);
        state.put("selected_category", categoryId(this.selectedCategory));
        state.put("previous_category", this.previousCategory == null ? "" : categoryId(this.previousCategory));
        state.put("category_progress", this.categorySwitchProgress);
        state.put("category_direction", this.categorySwitchDirection);
        state.put("screen_visibility", screenTransitionVisibility());
        state.put("screen_scale", screenTransitionScale());
        state.put("interactive", isInteractive());
        state.put("panel_offset_x", this.panelOffsetX);
        state.put("panel_offset_y", this.panelOffsetY);

        List<Map<String, Object>> categories = new ArrayList<>();
        for (Category category : Category.values()) {
            List<Module> modules = ModuleManager.getModules(category);
            if (modules.isEmpty()) {
                continue;
            }

            Map<String, Object> categoryState = new LinkedHashMap<>();
            categoryState.put("id", categoryId(category));
            categoryState.put("label", categoryLabel(category));
            categoryState.put("initial", category.name().substring(0, 1));
            categoryState.put("active_progress", categoryActiveAmount(category));

            List<Map<String, Object>> moduleStates = new ArrayList<>();
            for (Module module : modules) {
                moduleStates.add(moduleState(module));
            }
            categoryState.put("modules", moduleStates);
            categories.add(categoryState);
        }
        state.put("categories", categories);
    }

    @Override
    protected boolean onLuaAction(String action, LuaValue payload) {
        return switch (action) {
            case "category" -> {
                Category category = categoryById(payload.optjstring(""));
                if (category != null) {
                    blurComponentsExcept(null);
                    selectCategory(category);
                }
                yield true;
            }
            case "toggle_module" -> {
                Module module = moduleAt(payload.optint(-1));
                if (module != null && isInteractive()) {
                    module.toggle();
                }
                yield true;
            }
            case "drag_panel" -> {
                beginPanelDrag(payload);
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
            case "avatar" -> renderAvatar(
                luaFloat(args, 0),
                luaFloat(args, 1),
                luaFloat(args, 2),
                luaFloat(args, 3, 1.0F)
            );
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

        double mouseX = fixedMouseX(event.x());
        double mouseY = fixedMouseY(event.y());
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
                fixedMouseX(event.x()),
                fixedMouseY(event.y()),
                event.button()
            );
        }

        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && this.draggingPanel) {
            this.draggingPanel = false;
            handled = true;
        }

        return super.mouseReleased(event) || handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!isInteractive()) {
            return true;
        }

        if (this.capturedComponent != null
            && this.capturedComponent.mouseDragged(
                fixedMouseX(event.x()),
                fixedMouseY(event.y()),
                event.button(),
                dragX * coordinateScale(),
                dragY * coordinateScale()
            )) {
            return true;
        }

        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && this.draggingPanel) {
            this.panelOffsetX = fixedMouseX(event.x()) - this.dragOffsetX - this.dragBaseX;
            this.panelOffsetY = fixedMouseY(event.y()) - this.dragOffsetY - this.dragBaseY;
            clampPanelOffsets();
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isInteractive()) {
            return true;
        }

        double fixedMouseX = fixedMouseX(mouseX);
        double fixedMouseY = fixedMouseY(mouseY);
        AbstractComponent component = componentAt(fixedMouseX, fixedMouseY);
        if (component != null && component.mouseScrolled(fixedMouseX, fixedMouseY, scrollY)) {
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
        this.draggingPanel = false;
        this.capturedComponent = null;
        blurComponentsExcept(null);
        closeAvatarTexture();
        this.closing = false;
        this.closingCompleted = false;
        this.screenTransitionStartedAtNanos = 0L;
        this.screenTransitionProgress = 1.0F;
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
        Map<String, Object> state = new LinkedHashMap<>();
        ModuleAnimationState animation = moduleAnimation(module);
        List<AbstractValue<?>> visibleValues = visibleValues(module);

        state.put("index", moduleIndex(module));
        state.put("name", module.getName());
        state.put("description", module.getDescription());
        state.put("enabled_progress", animation.enabledProgress);
        state.put("expand_progress", animation.expandProgress);
        state.put("expandable", !visibleValues.isEmpty());
        state.put("expanded", isExpanded(module));

        List<Map<String, Object>> values = new ArrayList<>();
        float valuesHeight = 16.0F;
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
        state.put("values_height", visibleValues.isEmpty() ? 0.0F : valuesHeight);
        state.put(
            "row_height",
            50.0F + (visibleValues.isEmpty() ? 0.0F : valuesHeight) * animation.expandProgress
        );
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
            Math.round(fixedMouseX(luaMouseX())),
            Math.round(fixedMouseY(luaMouseY())),
            0.0F
        );
        if (interactive && visibleHeight > 0.0F) {
            this.renderedValueComponents.add(new ValueComponentLayout(
                component,
                x,
                y,
                width,
                Math.min(component.getHeight(), visibleHeight)
            ));
        }
    }

    private void beginPanelDrag(LuaValue payload) {
        if (!isInteractive() || !payload.istable()) {
            return;
        }

        this.draggingPanel = true;
        float panelX = (float)payload.get("x").optdouble(0.0D);
        float panelY = (float)payload.get("y").optdouble(0.0D);
        this.dragBaseX = (float)payload.get("base_x").optdouble(panelX);
        this.dragBaseY = (float)payload.get("base_y").optdouble(panelY);
        this.dragOffsetX = fixedMouseX(luaMouseX()) - panelX;
        this.dragOffsetY = fixedMouseY(luaMouseY()) - panelY;
        blurComponentsExcept(null);
    }

    @Nullable
    private AbstractComponent componentAt(double mouseX, double mouseY) {
        PanelBounds panel = panelBounds();
        if (!isInsideExclusive(
            mouseX,
            mouseY,
            panel.mainX(),
            panel.y() + HEADER_HEIGHT,
            panel.mainWidth(),
            panel.height() - HEADER_HEIGHT
        )) {
            return null;
        }

        for (int index = this.renderedValueComponents.size() - 1; index >= 0; index--) {
            ValueComponentLayout layout = this.renderedValueComponents.get(index);
            if (isInsideExclusive(mouseX, mouseY, layout.x(), layout.y(), layout.width(), layout.height())) {
                layout.component().setBounds(layout.x(), layout.y(), layout.width());
                return layout.component();
            }
        }
        return null;
    }

    @Nullable
    private ModuleRowLayout moduleRowAt(double mouseX, double mouseY) {
        PanelBounds panel = panelBounds();
        if (!isInsideExclusive(
            mouseX,
            mouseY,
            panel.mainX(),
            panel.y() + HEADER_HEIGHT,
            panel.mainWidth(),
            panel.height() - HEADER_HEIGHT
        )) {
            return null;
        }

        for (int index = this.renderedModuleRows.size() - 1; index >= 0; index--) {
            ModuleRowLayout row = this.renderedModuleRows.get(index);
            if (isInsideExclusive(mouseX, mouseY, row.x(), row.y(), row.width(), Math.min(50.0F, row.height()))) {
                return row;
            }
        }
        return null;
    }

    private AbstractComponent componentFor(AbstractValue<?> value) {
        return this.valueComponents.computeIfAbsent(value, this::createComponent);
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

    private void selectCategory(Category category) {
        String scrollId = categoryScrollId(category);
        if (category == this.selectedCategory) {
            if (this.previousCategory == null) {
                resetLuaScroll(scrollId);
            }
            return;
        }

        Category oldCategory = this.selectedCategory;
        this.previousCategory = oldCategory;
        this.selectedCategory = category;
        resetLuaScroll(scrollId);
        this.categorySwitchDirection = categorySwitchDirection(oldCategory, category);
        this.categorySwitchStartedAtNanos = System.nanoTime();
        this.categorySwitchProgress = 0.0F;
    }

    private void ensureVisibleCategory() {
        if (!ModuleManager.getModules(this.selectedCategory).isEmpty()) {
            return;
        }

        for (Category category : Category.values()) {
            if (!ModuleManager.getModules(category).isEmpty()) {
                this.selectedCategory = category;
                cancelCategorySwitchAnimation();
                return;
            }
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

    private void updateScreenTransitionAnimation() {
        if (this.screenTransitionStartedAtNanos == 0L) {
            this.screenTransitionProgress = 1.0F;
            return;
        }

        float rawProgress = (float)(
            (System.nanoTime() - this.screenTransitionStartedAtNanos)
                / (double)SCREEN_TRANSITION_ANIMATION_NANOS
        );
        if (rawProgress >= 1.0F) {
            this.screenTransitionProgress = 1.0F;
            if (this.closing) {
                this.closingCompleted = true;
            }
            return;
        }
        this.screenTransitionProgress = easeOutCubic(clamp(rawProgress, 0.0F, 1.0F));
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
        this.screenTransitionProgress = 0.0F;
        this.screenTransitionStartedAtNanos = System.nanoTime();
        this.lastAnimationFrameNanos = 0L;
        this.draggingPanel = false;
        this.capturedComponent = null;
        blurComponentsExcept(null);
    }

    private boolean isInteractive() {
        return !this.closing && this.screenTransitionProgress >= 1.0F;
    }

    private float screenTransitionVisibility() {
        return this.closing ? 1.0F - this.screenTransitionProgress : this.screenTransitionProgress;
    }

    private float screenTransitionScale() {
        return lerp(SCREEN_TRANSITION_MIN_SCALE, 1.0F, screenTransitionVisibility());
    }

    private void updateCategorySwitchAnimation() {
        if (this.previousCategory == null) {
            this.categorySwitchProgress = 1.0F;
            return;
        }

        float rawProgress = (float)(
            (System.nanoTime() - this.categorySwitchStartedAtNanos)
                / (double)CATEGORY_SWITCH_ANIMATION_NANOS
        );
        if (rawProgress >= 1.0F) {
            this.previousCategory = null;
            this.categorySwitchProgress = 1.0F;
            return;
        }
        this.categorySwitchProgress = easeOutCubic(clamp(rawProgress, 0.0F, 1.0F));
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

    private void cancelCategorySwitchAnimation() {
        this.previousCategory = null;
        this.categorySwitchProgress = 1.0F;
        this.categorySwitchDirection = 1;
    }

    private float categoryActiveAmount(Category category) {
        if (this.previousCategory == null) {
            return this.selectedCategory == category ? 1.0F : 0.0F;
        }
        if (category == this.selectedCategory) {
            return this.categorySwitchProgress;
        }
        if (category == this.previousCategory) {
            return 1.0F - this.categorySwitchProgress;
        }
        return 0.0F;
    }

    private int categorySwitchDirection(Category from, Category to) {
        int direction = Integer.compare(visibleCategoryIndex(to), visibleCategoryIndex(from));
        return direction == 0 ? 1 : direction;
    }

    private int visibleCategoryIndex(Category target) {
        int index = 0;
        for (Category category : Category.values()) {
            if (ModuleManager.getModules(category).isEmpty()) {
                continue;
            }
            if (category == target) {
                return index;
            }
            index++;
        }
        return target.ordinal();
    }

    private void clampPanelOffsets() {
        float screenWidth = fixedScreenWidth();
        float screenHeight = fixedScreenHeight();
        float panelWidth = Math.max(1.0F, Math.min(PANEL_WIDTH, screenWidth - MIN_MARGIN * 2.0F));
        float panelHeight = Math.max(1.0F, Math.min(PANEL_HEIGHT, screenHeight - MIN_MARGIN * 2.0F));
        float baseX = (screenWidth - panelWidth) * 0.5F;
        float baseY = (screenHeight - panelHeight) * 0.5F;
        float panelX = clampPanelAxis(baseX + this.panelOffsetX, panelWidth, screenWidth);
        float panelY = clampPanelAxis(baseY + this.panelOffsetY, panelHeight, screenHeight);
        this.panelOffsetX = panelX - baseX;
        this.panelOffsetY = panelY - baseY;
    }

    private PanelBounds panelBounds() {
        float screenWidth = fixedScreenWidth();
        float screenHeight = fixedScreenHeight();
        float width = Math.max(1.0F, Math.min(PANEL_WIDTH, screenWidth - MIN_MARGIN * 2.0F));
        float height = Math.max(1.0F, Math.min(PANEL_HEIGHT, screenHeight - MIN_MARGIN * 2.0F));
        float x = (screenWidth - width) * 0.5F + this.panelOffsetX;
        float y = (screenHeight - height) * 0.5F + this.panelOffsetY;
        float sidebar = width < 520.0F
            ? 138.0F
            : width < 760.0F ? SIDEBAR_WIDTH_COMPACT : SIDEBAR_WIDTH;
        return new PanelBounds(x, y, width, height, x + sidebar, width - sidebar);
    }

    private static float clampPanelAxis(float position, float panelSize, float screenSize) {
        if (screenSize <= panelSize + MIN_MARGIN * 2.0F) {
            return (screenSize - panelSize) * 0.5F;
        }
        return clamp(position, MIN_MARGIN, screenSize - panelSize - MIN_MARGIN);
    }

    private void renderAvatar(float x, float y, float size, float alpha) {
        if (this.avatarImage != null) {
            ensureAvatarTexture();
        }

        int tint = Render2DUtility.applyOpacity(TEXT, alpha);
        int border = Render2DUtility.applyOpacity(BORDER, alpha);
        if (this.avatarTexture != null) {
            Render2DUtility.drawTexture(this.avatarTexture.getTextureView(), x, y, size, size, tint);
            Render2DUtility.drawOutlineCircle(x + size * 0.5F, y + size * 0.5F, size * 0.5F, 1.0F, border);
            return;
        }

        Render2DUtility.drawCircle(
            x + size * 0.5F,
            y + size * 0.5F,
            size * 0.5F,
            Render2DUtility.applyOpacity(CONTROL_HOVER, alpha)
        );
        Render2DUtility.drawOutlineCircle(x + size * 0.5F, y + size * 0.5F, size * 0.5F, 1.0F, border);
        FontRenderer font = FontManager.getClickGuiRenderer(14.0F);
        String initial = this.userName == null || this.userName.isBlank()
            ? "U"
            : this.userName.substring(0, 1).toUpperCase(Locale.ROOT);
        font.drawCenteredString(
            initial,
            x + size * 0.5F,
            y + (size - font.getLineHeight()) * 0.5F,
            Render2DUtility.applyOpacity(TEXT_DIM, alpha)
        );
    }

    private void ensureAvatarTexture() {
        if (this.avatarTexture != null || this.avatarImage == null) {
            return;
        }
        this.avatarTexture = new DynamicTexture(
            () -> "nyx-clickgui-avatar-" + AVATAR_TEXTURE_IDS.incrementAndGet(),
            toNativeImage(this.avatarImage)
        );
    }

    private void closeAvatarTexture() {
        if (this.avatarTexture != null) {
            this.avatarTexture.close();
            this.avatarTexture = null;
        }
    }

    @Nullable
    private static BufferedImage loadAvatarImage() {
        try {
            return MicrosoftUtility.getCurrentMicrosoftAccountAvatarImage()
                .map(image -> circularImage(image, AVATAR_TEXTURE_SIZE))
                .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static BufferedImage circularImage(BufferedImage source, int size) {
        int safeSize = Math.max(1, size);
        int supersampledSize = safeSize * AVATAR_SUPERSAMPLE;
        BufferedImage image = new BufferedImage(supersampledSize, supersampledSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            applyAvatarRenderingHints(graphics);
            int sourceSize = Math.min(source.getWidth(), source.getHeight());
            int sourceX = (source.getWidth() - sourceSize) / 2;
            int sourceY = (source.getHeight() - sourceSize) / 2;
            graphics.drawImage(
                source,
                0,
                0,
                supersampledSize,
                supersampledSize,
                sourceX,
                sourceY,
                sourceX + sourceSize,
                sourceY + sourceSize,
                null
            );
        } finally {
            graphics.dispose();
        }

        applyCircleAlphaMask(image);
        return scaleAvatarImage(image, safeSize);
    }

    private static void applyCircleAlphaMask(BufferedImage image) {
        int size = image.getWidth();
        BufferedImage mask = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = mask.createGraphics();
        try {
            applyAvatarRenderingHints(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillOval(0, 0, size, size);
        } finally {
            graphics.dispose();
        }

        WritableRaster alpha = image.getAlphaRaster();
        WritableRaster maskRaster = mask.getRaster();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int maskedAlpha = alpha.getSample(x, y, 0) * maskRaster.getSample(x, y, 0) / 255;
                alpha.setSample(x, y, 0, maskedAlpha);
            }
        }
    }

    private static BufferedImage scaleAvatarImage(BufferedImage source, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            applyAvatarRenderingHints(graphics);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void applyAvatarRenderingHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(
            RenderingHints.KEY_ALPHA_INTERPOLATION,
            RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
        );
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                nativeImage.setPixel(x, y, image.getRGB(x, y));
            }
        }
        return nativeImage;
    }

    private boolean isLeftShiftDown() {
        return this.minecraft != null
            && this.minecraft.getWindow() != null
            && glfwGetKey(this.minecraft.getWindow().handle(), GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS;
    }

    private float fixedMouseX(double mouseX) {
        return (float)(mouseX * coordinateScale());
    }

    private float fixedMouseY(double mouseY) {
        return (float)(mouseY * coordinateScale());
    }

    private float fixedScreenWidth() {
        return this.width * coordinateScale();
    }

    private float fixedScreenHeight() {
        return this.height * coordinateScale();
    }

    private float coordinateScale() {
        return guiScale() / UI_SCALE;
    }

    private int guiScale() {
        return this.minecraft == null ? 1 : Math.max(1, this.minecraft.getWindow().getGuiScale());
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
        String value = categoryId(category);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String categoryScrollId(Category category) {
        return "clickgui:" + categoryId(category);
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

    private static final class ModuleAnimationState {
        private float expandProgress;
        private float enabledProgress;

        private ModuleAnimationState(Module module) {
            this.enabledProgress = module.isEnabled() ? 1.0F : 0.0F;
        }
    }

    private record ModuleRowLayout(Module module, float x, float y, float width, float height) {
    }

    private record ValueComponentLayout(AbstractComponent component, float x, float y, float width, float height) {
    }

    private record PanelBounds(
        float x,
        float y,
        float width,
        float height,
        float mainX,
        float mainWidth
    ) {
    }
}
