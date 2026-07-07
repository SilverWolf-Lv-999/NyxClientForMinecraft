package io.github.seraphina.nyx.client.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import io.github.seraphina.nyx.client.ui.UIComponent;
import io.github.seraphina.nyx.client.utility.IMinecraft;
import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HUDManager implements IMinecraft {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CONFIG_VERSION = 1;
    private static final float MIN_SCALE = 0.5F;
    private static final float MAX_SCALE = 3.0F;
    private static final float SCALE_STEP = 0.08F;
    private static final int EDIT_OUTLINE = 0xAA3D81F7;
    private static final int EDIT_HOVER_OUTLINE = 0xFFFFFFFF;
    private static final int EDIT_FILL = 0x183D81F7;
    private static final Path HUD_FILE = PathManager.HUD_PATH.resolve("hud.json");

    private static final Map<String, Layout> layouts = new LinkedHashMap<>();
    private static UIComponent draggingComponent;
    private static float dragOffsetX;
    private static float dragOffsetY;
    private static boolean loaded;
    private static boolean dirty;
    private static boolean shutdownHookRegistered;

    private HUDManager() {
    }

    public static void load() {
        try {
            PathManager.init();
        } catch (IOException exception) {
            exception.printStackTrace();
            return;
        }

        ensureDefaultLayouts();
        load(HUD_FILE);
        ensureDefaultLayouts();
        loaded = true;

        if (!Files.exists(HUD_FILE)) {
            save();
        }
        registerShutdownHook();
    }

    public static void load(Path hudFile) {
        if (hudFile == null || !Files.exists(hudFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(hudFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();
            JsonObject components = root.has("components") && root.get("components").isJsonObject()
                ? root.getAsJsonObject("components")
                : root;

            for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                Layout layout = readLayout(entry.getValue().getAsJsonObject());
                if (layout != null) {
                    layouts.put(entry.getKey(), layout);
                }
            }
        } catch (IOException | JsonParseException exception) {
            exception.printStackTrace();
        }
    }

    public static void save() {
        save(HUD_FILE);
        dirty = false;
    }

    public static void save(Path hudFile) {
        if (hudFile == null) {
            return;
        }

        try {
            writeConfig(hudFile, createRoot());
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public static List<UIComponent> getVisibleComponents() {
        if (!HUD.INSTANCE.isEnabled()) {
            return Collections.emptyList();
        }

        return HUD.components.stream()
            .filter(UIComponent::isVisible)
            .toList();
    }

    public static void render(GuiGraphics graphics, float partialTicks) {
        if (!HUD.INSTANCE.isEnabled()) {
            return;
        }

        Render2DUtility.withGuiGraphics(graphics, () -> {
            for (UIComponent component : getVisibleComponents()) {
                renderComponent(graphics, component, partialTicks);
            }
        });
    }

    public static void renderEditor(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!HUD.INSTANCE.isEnabled()) {
            stopDragging();
            return;
        }

        Render2DUtility.withGuiGraphics(graphics, () -> {
            for (UIComponent component : getVisibleComponents()) {
                AABB bounds = getDisplayBounds(component);
                float x = (float)bounds.minX;
                float y = (float)bounds.minY;
                float width = (float)bounds.getXsize();
                float height = (float)bounds.getYsize();
                boolean active = component == draggingComponent || isInside(mouseX, mouseY, bounds);
                int outline = active ? EDIT_HOVER_OUTLINE : EDIT_OUTLINE;

                if (active) {
                    Render2DUtility.drawRoundedRect(x, y, width, height, 4.0F, EDIT_FILL);
                }
                Render2DUtility.drawOutlineRoundedRect(x, y, width, height, 4.0F, 1.0F, outline);
            }
        });
    }

    public static boolean startDragging(double mouseX, double mouseY, int button) {
        if (button != 0 || !HUD.INSTANCE.isEnabled()) {
            return false;
        }

        UIComponent component = getComponentAt(mouseX, mouseY);
        if (component == null) {
            return false;
        }

        Layout layout = layoutFor(component);
        draggingComponent = component;
        dragOffsetX = (float)mouseX - layout.x;
        dragOffsetY = (float)mouseY - layout.y;
        return true;
    }

    public static boolean drag(double mouseX, double mouseY) {
        if (draggingComponent == null) {
            return false;
        }

        Layout layout = layoutFor(draggingComponent);
        layout.x = (float)mouseX - dragOffsetX;
        layout.y = (float)mouseY - dragOffsetY;
        clampLayoutToScreen(draggingComponent, layout);
        dirty = true;
        return true;
    }

    public static boolean stopDragging() {
        if (draggingComponent == null) {
            return false;
        }

        draggingComponent = null;
        if (dirty) {
            save();
        }
        return true;
    }

    public static boolean scaleHovered(double mouseX, double mouseY, double scrollY) {
        if (!HUD.INSTANCE.isEnabled() || scrollY == 0.0D) {
            return false;
        }

        UIComponent component = getComponentAt(mouseX, mouseY);
        if (component == null) {
            return false;
        }

        Layout layout = layoutFor(component);
        float oldScale = layout.scale;
        float newScale = MathUtility.clamp(oldScale + (float)Math.signum(scrollY) * SCALE_STEP, MIN_SCALE, MAX_SCALE);
        if (oldScale == newScale) {
            return true;
        }

        float localMouseX = ((float)mouseX - layout.x) / oldScale;
        float localMouseY = ((float)mouseY - layout.y) / oldScale;
        layout.x = (float)mouseX - localMouseX * newScale;
        layout.y = (float)mouseY - localMouseY * newScale;
        layout.scale = newScale;
        clampLayoutToScreen(component, layout);
        dirty = true;
        save();
        return true;
    }

    public static AABB getDisplayBounds(UIComponent component) {
        Layout layout = layoutFor(component);
        AABB base = component.getBoundingBox();
        float scale = layout.scale;
        return new AABB(
            layout.x + base.minX * scale,
            layout.y + base.minY * scale,
            0.0D,
            layout.x + base.maxX * scale,
            layout.y + base.maxY * scale,
            1.0D
        );
    }

    public static float getScale(UIComponent component) {
        return layoutFor(component).scale;
    }

    private static void renderComponent(GuiGraphics graphics, UIComponent component, float partialTicks) {
        Layout layout = layoutFor(component);
        Render2DUtility.withTranslation(layout.x, layout.y, () ->
            Render2DUtility.withScale(layout.scale, layout.scale, 0.0F, 0.0F, () ->
                component.render(graphics, partialTicks, layout.scale)
            )
        );
    }

    private static UIComponent getComponentAt(double mouseX, double mouseY) {
        List<UIComponent> components = getVisibleComponents();
        for (int index = components.size() - 1; index >= 0; index--) {
            UIComponent component = components.get(index);
            if (isInside(mouseX, mouseY, getDisplayBounds(component))) {
                return component;
            }
        }
        return null;
    }

    private static boolean isInside(double mouseX, double mouseY, AABB bounds) {
        return mouseX >= bounds.minX && mouseX <= bounds.maxX && mouseY >= bounds.minY && mouseY <= bounds.maxY;
    }

    private static Layout layoutFor(UIComponent component) {
        Layout layout = layouts.computeIfAbsent(component.getId(), ignored -> defaultLayout(component));
        if (clampLayoutToScreen(component, layout)) {
            dirty = true;
        }
        return layout;
    }

    private static void ensureDefaultLayouts() {
        for (UIComponent component : HUD.components) {
            layoutFor(component);
        }
    }

    private static Layout defaultLayout(UIComponent component) {
        return new Layout(component.getDefaultX(), component.getDefaultY(), component.getDefaultScale());
    }

    private static Layout readLayout(JsonObject object) {
        Float x = readFloat(object, "x");
        Float y = readFloat(object, "y");
        Float scale = readFloat(object, "scale");
        if (scale == null) {
            scale = readFloat(object, "size");
        }

        if (x == null || y == null) {
            return null;
        }

        return new Layout(
            x,
            y,
            MathUtility.clamp(scale == null ? 1.0F : scale, MIN_SCALE, MAX_SCALE)
        );
    }

    private static Float readFloat(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return element.getAsFloat();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static JsonObject createRoot() {
        JsonObject root = new JsonObject();
        JsonObject components = new JsonObject();

        ensureDefaultLayouts();
        for (UIComponent component : HUD.components) {
            Layout layout = layoutFor(component);
            JsonObject object = new JsonObject();
            object.addProperty("x", layout.x);
            object.addProperty("y", layout.y);
            object.addProperty("scale", layout.scale);
            components.add(component.getId(), object);
        }

        root.addProperty("version", CONFIG_VERSION);
        root.add("components", components);
        return root;
    }

    private static boolean clampLayoutToScreen(UIComponent component, Layout layout) {
        if (mc.getWindow() == null) {
            return false;
        }

        float oldX = layout.x;
        float oldY = layout.y;
        AABB base = component.getBoundingBox();
        float width = Math.max(1.0F, (float)base.getXsize() * layout.scale);
        float height = Math.max(1.0F, (float)base.getYsize() * layout.scale);
        float minX = (float)(-base.minX * layout.scale);
        float minY = (float)(-base.minY * layout.scale);
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();

        if (screenWidth > width) {
            layout.x = MathUtility.clamp(layout.x, minX, screenWidth - width + minX);
        } else {
            layout.x = minX;
        }

        if (screenHeight > height) {
            layout.y = MathUtility.clamp(layout.y, minY, screenHeight - height + minY);
        } else {
            layout.y = minY;
        }

        return oldX != layout.x || oldY != layout.y;
    }

    private static void writeConfig(Path configFile, JsonObject root) throws IOException {
        Path target = configFile.toAbsolutePath();
        Path directory = target.getParent();
        if (directory == null) {
            throw new FileNotFoundException("HUD config path has no parent: " + configFile);
        }

        Files.createDirectories(directory);
        Path tempFile = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        boolean moved = false;

        try {
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            try {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (loaded && dirty) {
                save();
            }
        }, "NyxClient HUD Save"));
        shutdownHookRegistered = true;
    }

    private static final class Layout {
        private float x;
        private float y;
        private float scale;

        private Layout(float x, float y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = MathUtility.clamp(scale, MIN_SCALE, MAX_SCALE);
        }
    }
}
