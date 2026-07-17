package io.github.seraphina.nyx.client.ui;

import com.mojang.logging.LogUtils;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.ui.mainui.MainUI;
import io.github.seraphina.nyx.client.utility.LuaUtility;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.seraphina.nyx.client.utility.MathUtility.animateExp;
import static io.github.seraphina.nyx.client.utility.MathUtility.clamp;
import static io.github.seraphina.nyx.client.utility.MathUtility.isInside;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/**
 * A Minecraft screen whose layout and presentation are defined by a Lua resource.
 * Subclasses expose controller state and translate named Lua actions into game logic.
 */
public class LuaScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;

    private final String scriptPath;
    private final List<Hitbox> hitboxes = new ArrayList<>();
    private final List<ScrollArea> scrollAreas = new ArrayList<>();
    private final Map<String, Float> scrollOffsets = new HashMap<>();
    private final Map<String, ManagedInput> inputs = new HashMap<>();
    private final Map<String, Float> buttonHoverProgress = new HashMap<>();
    private final Set<String> visibleInputs = new HashSet<>();

    private Globals globals;
    private LuaValue module = LuaValue.NIL;
    private LuaValue ui = LuaValue.NIL;
    private ManagedInput focusedInput;
    private Hitbox capturedHitbox;
    private boolean backgroundSelectorVisible;
    private long lastFrameNanos;
    private float frameSeconds = DEFAULT_FRAME_SECONDS;
    private int luaMouseX;
    private int luaMouseY;
    private float luaPartialTick;

    public LuaScreen(String path) {
        this(path, Component.empty());
    }

    public LuaScreen(String path, Component title) {
        super(title);
        this.scriptPath = path;
    }

    @Override
    protected void init() {
        super.init();
        this.lastFrameNanos = 0L;
        loadScript();
        invoke("init", createState());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateFrameTime();
        this.luaMouseX = mouseX;
        this.luaMouseY = mouseY;
        this.luaPartialTick = partialTick;
        this.hitboxes.clear();
        this.scrollAreas.clear();
        this.visibleInputs.clear();
        this.backgroundSelectorVisible = false;

        Render2DUtility.withGuiGraphics(guiGraphics, () -> {
            beforeLuaRender(guiGraphics, mouseX, mouseY, partialTick);
            invoke("render", createState());
            afterLuaRender(guiGraphics, mouseX, mouseY, partialTick);
        });

        this.inputs.entrySet().removeIf(entry -> {
            if (this.visibleInputs.contains(entry.getKey())) {
                return false;
            }
            if (entry.getValue() == this.focusedInput) {
                this.focusedInput = null;
            }
            return true;
        });
    }

    @Override
    public void tick() {
        super.tick();
        invoke("tick", createState());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.luaMouseX = (int)event.x();
        this.luaMouseY = (int)event.y();
        if (event.button() != GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        if (this.backgroundSelectorVisible
            && MainUI.mouseClickedSharedBackgroundSelector(event, this.width, this.height)) {
            return true;
        }

        for (int index = this.hitboxes.size() - 1; index >= 0; index--) {
            Hitbox hitbox = this.hitboxes.get(index);
            if (!hitbox.active || !hitbox.contains(event.x(), event.y())) {
                continue;
            }

            if (hitbox.inputId != null) {
                focusInput(hitbox.inputId, event.x());
            } else {
                clearInputFocus();
            }
            if (hitbox.capture) {
                this.capturedHitbox = hitbox;
            }
            playClickSound();
            dispatchAction(doubleClick && !hitbox.doubleAction.isBlank() ? hitbox.doubleAction : hitbox.action, hitbox.payload);
            return true;
        }

        clearInputFocus();
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && this.capturedHitbox != null) {
            this.capturedHitbox = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        this.luaMouseX = (int)event.x();
        this.luaMouseY = (int)event.y();
        if (event.button() == GLFW_MOUSE_BUTTON_LEFT && this.capturedHitbox != null) {
            dispatchAction(this.capturedHitbox.action, this.capturedHitbox.payload);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.backgroundSelectorVisible
            && MainUI.mouseScrolledSharedBackgroundSelector(mouseX, mouseY, scrollY, this.width, this.height)) {
            return true;
        }

        for (int index = this.scrollAreas.size() - 1; index >= 0; index--) {
            ScrollArea area = this.scrollAreas.get(index);
            if (!area.contains(mouseX, mouseY)) {
                continue;
            }

            float current = this.scrollOffsets.getOrDefault(area.id, 0.0F);
            this.scrollOffsets.put(area.id, clamp(current - (float)scrollY * area.step, 0.0F, area.maximum));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && this.backgroundSelectorVisible && MainUI.closeSharedBackgroundSelector()) {
            return true;
        }

        if (this.focusedInput != null && handleInputKey(this.focusedInput, event)) {
            return true;
        }

        LuaTable key = new LuaTable();
        key.set("code", event.key());
        key.set("escape", LuaValue.valueOf(event.isEscape()));
        key.set("selection", LuaValue.valueOf(event.isSelection()));
        key.set("left", LuaValue.valueOf(event.isLeft()));
        key.set("right", LuaValue.valueOf(event.isRight()));
        key.set("select_all", LuaValue.valueOf(event.isSelectAll()));
        key.set("copy", LuaValue.valueOf(event.isCopy()));
        key.set("cut", LuaValue.valueOf(event.isCut()));
        key.set("paste", LuaValue.valueOf(event.isPaste()));
        if (invokeBoolean("key_pressed", createState(), key)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.focusedInput != null && event.isAllowedChatCharacter()) {
            insert(this.focusedInput, event.codepointAsString());
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        invoke("removed", createState());
        this.capturedHitbox = null;
        this.focusedInput = null;
        this.inputs.clear();
        super.removed();
    }

    protected void appendLuaState(Map<String, Object> state) {
    }

    protected boolean onLuaAction(String action, LuaValue payload) {
        return false;
    }

    protected void onLuaInputChanged(String id, String value) {
    }

    protected void renderLuaCustom(String name, LuaValue[] args) {
    }

    protected void beforeLuaRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    protected void afterLuaRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    protected final int luaMouseX() {
        return this.luaMouseX;
    }

    protected final int luaMouseY() {
        return this.luaMouseY;
    }

    protected final float luaFrameSeconds() {
        return this.frameSeconds;
    }

    protected final float luaScrollOffset(String id) {
        return this.scrollOffsets.getOrDefault(id, 0.0F);
    }

    protected final void setLuaScrollOffset(String id, float value) {
        if (id != null && !id.isBlank()) {
            this.scrollOffsets.put(id, Math.max(0.0F, value));
        }
    }

    protected final void resetLuaScroll(String id) {
        setLuaScrollOffset(id, 0.0F);
    }

    protected final void setLuaInputValue(String id, String value) {
        ManagedInput input = this.inputs.get(id);
        if (input != null) {
            input.setValue(value);
        }
    }

    protected final void clearLuaInputFocus() {
        clearInputFocus();
    }

    private void loadScript() {
        try {
            this.globals = LuaUtility.createGlobals();
            this.ui = createUiApi();
            this.globals.set("ui", this.ui);
            this.globals.set("include", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue path) {
                    try {
                        return LuaUtility.load(LuaScreen.this.globals, path.checkjstring());
                    } catch (IOException | LuaError exception) {
                        throw new LuaError("Unable to include " + path.tojstring() + ": " + exception.getMessage());
                    }
                }
            });
            this.module = LuaUtility.load(this.globals, this.scriptPath);
            if (this.module.isnil()) {
                this.module = this.globals;
            }
        } catch (IOException | LuaError exception) {
            this.module = LuaValue.NIL;
            LOGGER.error("Unable to load Lua screen {}", this.scriptPath, exception);
        }
    }

    private LuaTable createState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("width", this.width);
        state.put("height", this.height);
        state.put("mouse_x", this.luaMouseX);
        state.put("mouse_y", this.luaMouseY);
        state.put("partial_tick", this.luaPartialTick);
        state.put("frame_seconds", this.frameSeconds);
        state.put("time_seconds", System.nanoTime() / 1_000_000_000.0D);
        state.put("scroll_offsets", this.scrollOffsets);

        Map<String, String> inputValues = new HashMap<>();
        for (Map.Entry<String, ManagedInput> entry : this.inputs.entrySet()) {
            inputValues.put(entry.getKey(), entry.getValue().value);
        }
        state.put("input_values", inputValues);
        appendLuaState(state);
        return (LuaTable)LuaUtility.toLua(state);
    }

    private LuaTable createUiApi() {
        LuaTable api = new LuaTable();

        api.set("rect", function(args -> {
            Render2DUtility.drawRect(f(args, 1), f(args, 2), f(args, 3), f(args, 4), color(args.arg(5), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("rounded_rect", function(args -> {
            Render2DUtility.drawRoundedRect(f(args, 1), f(args, 2), f(args, 3), f(args, 4), f(args, 5),
                color(args.arg(6), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("outline", function(args -> {
            Render2DUtility.drawOutlineRoundedRect(f(args, 1), f(args, 2), f(args, 3), f(args, 4), f(args, 5),
                f(args, 6), color(args.arg(7), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("shadow", function(args -> {
            Render2DUtility.drawDropShadow(f(args, 1), f(args, 2), f(args, 3), f(args, 4), f(args, 5),
                f(args, 6), f(args, 7), f(args, 8), color(args.arg(9), 0x66000000));
            return LuaValue.NONE;
        }));
        api.set("panel", function(args -> {
            Render2DUtility.drawGaussianBlurredPanel(
                f(args, 1), f(args, 2), f(args, 3), f(args, 4), f(args, 5), f(args, 6),
                color(args.arg(7), 0xE6FFFFFF), color(args.arg(8), 0xB80A0C12),
                f(args, 9), color(args.arg(10), 0x66FFFFFF)
            );
            return LuaValue.NONE;
        }));
        api.set("vertical_gradient", function(args -> {
            Render2DUtility.drawVerticalGradientRect(f(args, 1), f(args, 2), f(args, 3), f(args, 4),
                color(args.arg(5), 0xFFFFFFFF), color(args.arg(6), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("horizontal_gradient", function(args -> {
            Render2DUtility.drawHorizontalGradientRect(f(args, 1), f(args, 2), f(args, 3), f(args, 4),
                color(args.arg(5), 0xFFFFFFFF), color(args.arg(6), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("rounded_horizontal_gradient", function(args -> {
            Render2DUtility.drawRoundedHorizontalGradientRect(f(args, 1), f(args, 2), f(args, 3), f(args, 4), f(args, 5),
                color(args.arg(6), 0xFFFFFFFF), color(args.arg(7), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("circle", function(args -> {
            Render2DUtility.drawCircle(f(args, 1), f(args, 2), f(args, 3), color(args.arg(4), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("text", function(args -> {
            Render2DUtility.drawText(s(args, 1), f(args, 2), f(args, 3), f(args, 4), color(args.arg(5), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("centered_text", function(args -> {
            Render2DUtility.drawCenteredText(s(args, 1), f(args, 2), f(args, 3), f(args, 4), color(args.arg(5), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("centered_text_in_rect", function(args -> {
            Render2DUtility.drawCenteredTextInRect(s(args, 1), f(args, 2), f(args, 3), f(args, 4), f(args, 5), f(args, 6),
                color(args.arg(7), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("text_width", function(args -> LuaValue.valueOf(Render2DUtility.getTextWidth(s(args, 1), f(args, 2)))));
        api.set("display_text", function(args -> {
            displayFont(f(args, 4)).drawString(s(args, 1), f(args, 2), f(args, 3), color(args.arg(5), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("display_centered_text", function(args -> {
            displayFont(f(args, 4)).drawCenteredString(s(args, 1), f(args, 2), f(args, 3), color(args.arg(5), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("text_font", function(args -> {
            textFont(f(args, 4)).drawString(s(args, 1), f(args, 2), f(args, 3), color(args.arg(5), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("text_centered", function(args -> {
            textFont(f(args, 4)).drawCenteredString(s(args, 1), f(args, 2), f(args, 3), color(args.arg(5), 0xFFFFFFFF));
            return LuaValue.NONE;
        }));
        api.set("text_centered_in_rect", function(args -> {
            FontRenderer font = textFont(f(args, 6));
            font.drawCenteredString(
                s(args, 1),
                f(args, 2) + f(args, 4) * 0.5F,
                f(args, 3) + (f(args, 5) - font.getLineHeight()) * 0.5F,
                color(args.arg(7), 0xFFFFFFFF)
            );
            return LuaValue.NONE;
        }));
        api.set("font_width", function(args ->
            LuaValue.valueOf(font(opts(args, 1, "text"), f(args, 3)).getStringWidth(s(args, 2)))
        ));
        api.set("font_height", function(args ->
            LuaValue.valueOf(font(opts(args, 1, "text"), f(args, 2)).getLineHeight())
        ));
        api.set("trim_text", function(args ->
            LuaValue.valueOf(trimToWidth(font(opts(args, 1, "text"), f(args, 3)), s(args, 2), f(args, 4)))
        ));
        api.set("opacity", function(args -> LuaValue.valueOf(Integer.toUnsignedLong(
            Render2DUtility.applyOpacity(color(args.arg(1), 0xFFFFFFFF), f(args, 2))
        ))));
        api.set("mix", function(args -> LuaValue.valueOf(Integer.toUnsignedLong(
            Render2DUtility.mix(color(args.arg(1), 0xFFFFFFFF), color(args.arg(2), 0xFFFFFFFF), f(args, 3))
        ))));
        api.set("hovered", function(args -> LuaValue.valueOf(isInside(
            LuaScreen.this.luaMouseX, LuaScreen.this.luaMouseY, f(args, 1), f(args, 2), f(args, 3), f(args, 4)
        ))));
        api.set("clip", function(args -> {
            LuaValue callback = args.arg(5);
            Render2DUtility.withClip(f(args, 1), f(args, 2), f(args, 3), f(args, 4), callback::call);
            return LuaValue.NONE;
        }));
        api.set("scale", function(args -> {
            LuaValue callback = args.arg(5);
            Render2DUtility.withScale(f(args, 1), f(args, 2), f(args, 3), f(args, 4), callback::call);
            return LuaValue.NONE;
        }));
        api.set("rotate", function(args -> {
            LuaValue callback = args.arg(4);
            Render2DUtility.withRotation(f(args, 1), f(args, 2), f(args, 3), callback::call);
            return LuaValue.NONE;
        }));
        api.set("vertical_perspective_flip", function(args -> {
            LuaValue callback = args.arg(6);
            Render2DUtility.withVerticalPerspectiveFlip(
                f(args, 1), f(args, 2), f(args, 3), f(args, 4), f(args, 5), callback::call
            );
            return LuaValue.NONE;
        }));
        api.set("horizontal_reflection", function(args -> {
            LuaValue callback = args.arg(2);
            Render2DUtility.withHorizontalVertexReflection(f(args, 1), callback::call);
            return LuaValue.NONE;
        }));
        api.set("vertical_flip_scale", function(args ->
            LuaValue.valueOf(Render2DUtility.verticalFlipScale(f(args, 1)))
        ));
        api.set("vertical_flip_back_face", function(args ->
            LuaValue.valueOf(Render2DUtility.isVerticalFlipBackFace(f(args, 1)))
        ));
        api.set("hitbox", function(args -> {
            registerHitbox(args, null);
            return LuaValue.NONE;
        }));
        api.set("button", function(args -> {
            drawButton(args);
            return LuaValue.NONE;
        }));
        api.set("input", function(args -> drawInput(args)));
        api.set("scroll", function(args -> LuaValue.valueOf(registerScrollArea(args))));
        api.set("action", function(args -> LuaValue.valueOf(dispatchAction(s(args, 1), args.arg(2)))));
        api.set("shared_background", function(args -> {
            MainUI.renderSharedBackground(LuaScreen.this.width, LuaScreen.this.height);
            return LuaValue.NONE;
        }));
        api.set("shared_user_card", function(args -> {
            MainUI.renderSharedUserCard(LuaScreen.this.width, LuaScreen.this.height, optf(args, 1, 0.0F), optf(args, 2, 1.0F));
            return LuaValue.NONE;
        }));
        api.set("shared_user_card_width", function(args -> LuaValue.valueOf(MainUI.sharedUserCardWidth(LuaScreen.this.width))));
        api.set("background_selector", function(args -> {
            LuaScreen.this.backgroundSelectorVisible = true;
            MainUI.renderSharedBackgroundSelector(
                LuaScreen.this.width, LuaScreen.this.height, LuaScreen.this.luaMouseX, LuaScreen.this.luaMouseY,
                LuaScreen.this.frameSeconds
            );
            return LuaValue.NONE;
        }));
        api.set("custom", function(args -> {
            LuaValue[] customArgs = new LuaValue[Math.max(0, args.narg() - 1)];
            for (int index = 0; index < customArgs.length; index++) {
                customArgs[index] = args.arg(index + 2);
            }
            renderLuaCustom(s(args, 1), customArgs);
            return LuaValue.NONE;
        }));
        return api;
    }

    private void drawButton(Varargs args) {
        String label = s(args, 1);
        float x = f(args, 2);
        float y = f(args, 3);
        float width = f(args, 4);
        float height = f(args, 5);
        String action = s(args, 6);
        LuaValue payload = args.arg(7);
        boolean active = optb(args, 8, true);
        boolean accent = optb(args, 9, false);
        float fontSize = optf(args, 10, 12.0F);
        String animationId = opts(args, 11, action + "\u0000" + label);
        boolean hovered = active && isInside(this.luaMouseX, this.luaMouseY, x, y, width, height);
        float hoverProgress = animateExp(
            this.buttonHoverProgress.getOrDefault(animationId, 0.0F),
            hovered ? 1.0F : 0.0F,
            16.0F,
            this.frameSeconds
        );
        this.buttonHoverProgress.put(animationId, hoverProgress);
        int base = accent ? 0xFF276DD8 : 0xAA0E1118;
        int hover = accent ? 0xFF3D81F7 : 0xD7191D28;
        float opacity = active ? 1.0F : 0.48F;
        int fill = Render2DUtility.mix(base, hover, hoverProgress);
        int border = Render2DUtility.mix(0x22FFFFFF, accent ? 0x884E94FF : 0x663D81F7, hoverProgress);
        int text = active ? Render2DUtility.mix(0xFFE2E6EF, 0xFFFFFFFF, hoverProgress) : 0xFF687181;

        Render2DUtility.drawDropShadow(
            x, y, width, height, 8.0F, 0.0F, 5.0F, 12.0F,
            Render2DUtility.applyOpacity(0x55000000, opacity * (0.55F + hoverProgress * 0.45F))
        );
        Render2DUtility.drawRoundedRect(x, y, width, height, 8.0F, Render2DUtility.applyOpacity(fill, opacity));
        Render2DUtility.drawOutlineRoundedRect(
            x, y, width, height, 8.0F, 1.0F, Render2DUtility.applyOpacity(border, opacity)
        );
        FontRenderer font = textFont(fontSize);
        font.drawCenteredString(
            trimToWidth(font, label, width - 18.0F),
            x + width * 0.5F,
            y + (height - font.getLineHeight()) * 0.5F,
            Render2DUtility.applyOpacity(text, opacity)
        );
        this.hitboxes.add(new Hitbox(x, y, width, height, action, payload, active, false, "", null));
    }

    private LuaValue drawInput(Varargs args) {
        String id = s(args, 1);
        float x = f(args, 2);
        float y = f(args, 3);
        float width = f(args, 4);
        float height = f(args, 5);
        String externalValue = opts(args, 6, "");
        String placeholder = opts(args, 7, "");
        int maxLength = Math.max(1, args.arg(8).optint(256));
        String submitAction = opts(args, 9, "");
        boolean enabled = optb(args, 10, true);
        boolean secret = optb(args, 11, false);
        String label = opts(args, 12, "");
        float fontSize = optf(args, 13, 10.0F);

        ManagedInput input = this.inputs.computeIfAbsent(id, ignored -> new ManagedInput(id));
        if (input != this.focusedInput && !input.value.equals(externalValue)) {
            input.setValue(externalValue);
        }
        input.x = x;
        input.y = y;
        input.width = width;
        input.height = height;
        input.maxLength = maxLength;
        input.submitAction = submitAction;
        input.enabled = enabled;
        input.fontSize = fontSize;
        this.visibleInputs.add(id);

        boolean focused = input == this.focusedInput;
        boolean hovered = enabled && isInside(this.luaMouseX, this.luaMouseY, x, y, width, height);
        input.hoverProgress = animateExp(input.hoverProgress, hovered ? 1.0F : 0.0F, 16.0F, this.frameSeconds);
        input.focusProgress = animateExp(input.focusProgress, focused ? 1.0F : 0.0F, 16.0F, this.frameSeconds);
        input.selectionProgress = animateExp(
            input.selectionProgress,
            focused && input.selectedAll ? 1.0F : 0.0F,
            16.0F,
            this.frameSeconds
        );
        float interaction = Math.max(input.hoverProgress * 0.65F, input.focusProgress);
        float borderProgress = Math.max(input.hoverProgress, input.focusProgress);
        int fill = Render2DUtility.mix(0xAA0E1118, 0xD7191D28, interaction);
        int border = Render2DUtility.mix(0x22FFFFFF, 0x663D81F7, borderProgress);
        FontRenderer font = textFont(fontSize);
        if (!label.isBlank()) {
            font.drawString(label, x, y - 14.0F, Render2DUtility.mix(0xFF687181, 0xFFA8AFBE, input.focusProgress));
        }
        float opacity = enabled ? 1.0F : 0.48F;
        Render2DUtility.drawDropShadow(
            x, y, width, height, 8.0F, 0.0F, 5.0F, 12.0F,
            Render2DUtility.applyOpacity(0x55000000, opacity * (0.36F + borderProgress * 0.26F))
        );
        Render2DUtility.drawRoundedRect(x, y, width, height, 8.0F, Render2DUtility.applyOpacity(fill, opacity));
        Render2DUtility.drawOutlineRoundedRect(
            x, y, width, height, 8.0F, 1.0F, Render2DUtility.applyOpacity(border, opacity)
        );

        String visibleValue = secret && !input.value.isEmpty() ? "*".repeat(input.value.codePointCount(0, input.value.length())) : input.value;
        String shown = visibleValue.isEmpty() ? placeholder : trimTail(font, visibleValue, width - 18.0F);
        int textColor = visibleValue.isEmpty()
            ? 0xFF687181
            : enabled ? Render2DUtility.mix(0xFFE2E6EF, 0xFFFFFFFF, input.focusProgress) : 0xFFA8AFBE;
        if (input.selectionProgress > 0.001F && !input.value.isEmpty()) {
            Render2DUtility.drawRoundedRect(
                x + 7.0F, y + 6.0F, Math.max(2.0F, width - 14.0F), height - 12.0F, 3.0F,
                Render2DUtility.applyOpacity(0xFF3D81F7, input.selectionProgress * 0.36F)
            );
        }
        font.drawString(shown, x + 9.0F, y + (height - font.getLineHeight()) * 0.5F, Render2DUtility.applyOpacity(textColor, opacity));
        if (focused && !input.selectedAll && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float cursorX = input.cursor >= input.value.length() || !shown.equals(visibleValue)
                ? x + 9.0F + font.getStringWidth(shown)
                : x + 9.0F + font.getStringWidth(secret ? "*".repeat(input.cursor) : visibleValue.substring(0, input.cursor));
            Render2DUtility.drawRect(
                Math.min(cursorX, x + width - 7.0F), y + 7.0F, 1.0F, height - 14.0F,
                Render2DUtility.applyOpacity(0xFFFFFFFF, Math.max(0.55F, input.focusProgress))
            );
        }

        this.hitboxes.add(new Hitbox(x, y, width, height, "", LuaValue.NIL, enabled, false, "", id));
        return LuaValue.valueOf(input.value);
    }

    private void registerHitbox(Varargs args, String inputId) {
        this.hitboxes.add(new Hitbox(
            f(args, 1), f(args, 2), f(args, 3), f(args, 4), s(args, 5), args.arg(6),
            optb(args, 7, true), optb(args, 8, false), opts(args, 9, ""), inputId
        ));
    }

    private float registerScrollArea(Varargs args) {
        String id = s(args, 1);
        float x = f(args, 2);
        float y = f(args, 3);
        float width = f(args, 4);
        float height = f(args, 5);
        float contentHeight = Math.max(0.0F, f(args, 6));
        float step = Math.max(1.0F, optf(args, 7, 32.0F));
        float maximum = Math.max(0.0F, contentHeight - Math.max(0.0F, height));
        float offset = clamp(this.scrollOffsets.getOrDefault(id, 0.0F), 0.0F, maximum);
        this.scrollOffsets.put(id, offset);
        this.scrollAreas.add(new ScrollArea(id, x, y, width, height, maximum, step));
        return offset;
    }

    private boolean handleInputKey(ManagedInput input, KeyEvent event) {
        if (event.isEscape()) {
            clearInputFocus();
            return true;
        }
        if (event.isSelectAll()) {
            input.selectedAll = true;
            input.cursor = input.value.length();
            return true;
        }
        if (event.isCopy()) {
            setClipboard(input.value);
            return true;
        }
        if (event.isCut()) {
            setClipboard(input.value);
            input.setValue("");
            notifyInputChanged(input);
            return true;
        }
        if (event.isPaste()) {
            insert(input, getClipboard());
            return true;
        }

        switch (event.key()) {
            case GLFW_KEY_BACKSPACE -> {
                delete(input, -1);
                return true;
            }
            case GLFW_KEY_DELETE -> {
                delete(input, 1);
                return true;
            }
            case GLFW_KEY_HOME -> {
                input.cursor = 0;
                input.selectedAll = false;
                return true;
            }
            case GLFW_KEY_END -> {
                input.cursor = input.value.length();
                input.selectedAll = false;
                return true;
            }
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> {
                if (!input.submitAction.isBlank()) {
                    dispatchAction(input.submitAction, LuaValue.valueOf(input.value));
                }
                return true;
            }
            default -> {
                if (event.isLeft()) {
                    input.cursor = Math.max(0, input.cursor - 1);
                    input.selectedAll = false;
                    return true;
                }
                if (event.isRight()) {
                    input.cursor = Math.min(input.value.length(), input.cursor + 1);
                    input.selectedAll = false;
                    return true;
                }
                return false;
            }
        }
    }

    private void focusInput(String id, double mouseX) {
        ManagedInput input = this.inputs.get(id);
        if (input == null || !input.enabled) {
            return;
        }
        this.focusedInput = input;
        input.selectedAll = false;
        FontRenderer font = textFont(input.fontSize);
        float localX = (float)mouseX - input.x - 9.0F;
        int best = 0;
        for (int index = 1; index <= input.value.length(); index++) {
            if (font.getStringWidth(input.value.substring(0, index)) <= localX) {
                best = index;
            }
        }
        input.cursor = best;
    }

    private void clearInputFocus() {
        if (this.focusedInput != null) {
            this.focusedInput.selectedAll = false;
            this.focusedInput = null;
        }
    }

    private void insert(ManagedInput input, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String clean = text.replace("\r", "").replace("\n", "");
        if (clean.isEmpty()) {
            return;
        }

        String current = input.selectedAll ? "" : input.value;
        int cursor = input.selectedAll ? 0 : Math.max(0, Math.min(input.cursor, current.length()));
        int room = Math.max(0, input.maxLength - current.length());
        if (room == 0) {
            return;
        }
        if (clean.length() > room) {
            clean = clean.substring(0, room);
        }
        input.setValue(current.substring(0, cursor) + clean + current.substring(cursor));
        input.cursor = cursor + clean.length();
        notifyInputChanged(input);
    }

    private void delete(ManagedInput input, int direction) {
        if (input.selectedAll) {
            input.setValue("");
            notifyInputChanged(input);
            return;
        }

        if (direction < 0 && input.cursor > 0) {
            int previous = input.value.offsetByCodePoints(input.cursor, -1);
            input.setValue(input.value.substring(0, previous) + input.value.substring(input.cursor));
            input.cursor = previous;
            notifyInputChanged(input);
        } else if (direction > 0 && input.cursor < input.value.length()) {
            int next = input.value.offsetByCodePoints(input.cursor, 1);
            input.setValue(input.value.substring(0, input.cursor) + input.value.substring(next));
            notifyInputChanged(input);
        }
    }

    private void notifyInputChanged(ManagedInput input) {
        onLuaInputChanged(input.id, input.value);
        invoke("input_changed", createState(), LuaValue.valueOf(input.id), LuaValue.valueOf(input.value));
    }

    private boolean dispatchAction(String action, LuaValue payload) {
        return action != null && !action.isBlank() && onLuaAction(action, payload == null ? LuaValue.NIL : payload);
    }

    private void invoke(String name, LuaValue... arguments) {
        LuaValue function = this.module.get(name);
        if (!function.isfunction()) {
            return;
        }
        try {
            function.invoke(LuaValue.varargsOf(arguments));
        } catch (LuaError exception) {
            LOGGER.error("Lua screen {} failed in {}", this.scriptPath, name, exception);
        }
    }

    private boolean invokeBoolean(String name, LuaValue... arguments) {
        LuaValue function = this.module.get(name);
        if (!function.isfunction()) {
            return false;
        }
        try {
            return function.invoke(LuaValue.varargsOf(arguments)).arg1().optboolean(false);
        } catch (LuaError exception) {
            LOGGER.error("Lua screen {} failed in {}", this.scriptPath, name, exception);
            return false;
        }
    }

    private void updateFrameTime() {
        long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) {
            this.frameSeconds = DEFAULT_FRAME_SECONDS;
        } else {
            this.frameSeconds = clamp((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.0F, MAX_FRAME_SECONDS);
        }
        this.lastFrameNanos = now;
    }

    private void playClickSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static String trimTail(FontRenderer renderer, String value, float width) {
        if (value == null || value.isEmpty() || renderer.getStringWidth(value) <= width) {
            return value == null ? "" : value;
        }
        String prefix = "...";
        int start = 0;
        while (start < value.length() && renderer.getStringWidth(prefix + value.substring(start)) > width) {
            start++;
        }
        return prefix + value.substring(Math.min(start, value.length()));
    }

    private static FontRenderer font(String kind, float size) {
        return "display".equalsIgnoreCase(kind) ? displayFont(size) : textFont(size);
    }

    private static FontRenderer displayFont(float size) {
        return FontManager.getAppleDisplayRenderer(size);
    }

    private static FontRenderer textFont(float size) {
        return FontManager.getAppleTextRenderer(size);
    }

    private static String trimToWidth(FontRenderer renderer, String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) {
            return "";
        }
        if (renderer.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        float suffixWidth = renderer.getStringWidth(suffix);
        if (suffixWidth > maxWidth) {
            return "";
        }

        int end = text.length();
        while (end > 0 && renderer.getStringWidth(text.substring(0, end)) + suffixWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + suffix;
    }

    private static String getClipboard() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
    }

    private static void setClipboard(String value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.keyboardHandler.setClipboard(value == null ? "" : value);
        }
    }

    private static VarArgFunction function(LuaFunctionBody body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return body.invoke(args);
            }
        };
    }

    private static float f(Varargs args, int index) {
        return (float)args.arg(index).checkdouble();
    }

    private static float optf(Varargs args, int index, float fallback) {
        return (float)args.arg(index).optdouble(fallback);
    }

    private static boolean optb(Varargs args, int index, boolean fallback) {
        return args.arg(index).optboolean(fallback);
    }

    private static String s(Varargs args, int index) {
        return args.arg(index).checkjstring();
    }

    private static String opts(Varargs args, int index, String fallback) {
        return args.arg(index).optjstring(fallback);
    }

    private static int color(LuaValue value, int fallback) {
        return value.isnil() ? fallback : (int)value.checklong();
    }

    @FunctionalInterface
    private interface LuaFunctionBody {
        Varargs invoke(Varargs args);
    }

    private record Hitbox(
        float x,
        float y,
        float width,
        float height,
        String action,
        LuaValue payload,
        boolean active,
        boolean capture,
        String doubleAction,
        String inputId
    ) {
        private boolean contains(double mouseX, double mouseY) {
            return isInside(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }

    private record ScrollArea(
        String id,
        float x,
        float y,
        float width,
        float height,
        float maximum,
        float step
    ) {
        private boolean contains(double mouseX, double mouseY) {
            return isInside(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }

    private static final class ManagedInput {
        private final String id;
        private String value = "";
        private String submitAction = "";
        private int maxLength = 256;
        private int cursor;
        private float x;
        private float y;
        private float width;
        private float height;
        private float fontSize = 10.0F;
        private float hoverProgress;
        private float focusProgress;
        private float selectionProgress;
        private boolean enabled = true;
        private boolean selectedAll;

        private ManagedInput(String id) {
            this.id = id;
        }

        private void setValue(String value) {
            this.value = value == null ? "" : value;
            if (this.value.length() > this.maxLength) {
                this.value = this.value.substring(0, this.maxLength);
            }
            this.cursor = Math.max(0, Math.min(this.cursor, this.value.length()));
            this.selectedAll = false;
        }
    }
}
