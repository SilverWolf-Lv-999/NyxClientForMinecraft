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
    private static final float GUIDE_GRID_SPACING = 16.0F;
    private static final int GUIDE_GRID_MAJOR_INTERVAL = 4;
    private static final float GUIDE_GRID_DASH = 4.0F;
    private static final float GUIDE_GRID_GAP = 6.0F;
    private static final float GUIDE_CENTER_DASH = 8.0F;
    private static final float GUIDE_CENTER_GAP = 5.0F;
    private static final float SNAP_GRID_RANGE = 3.0F;
    private static final float SNAP_ALIGNMENT_RANGE = 6.0F;
    private static final float SNAP_CENTER_RANGE = 8.0F;
    private static final int EDIT_OUTLINE = 0xAA3D81F7;
    private static final int EDIT_HOVER_OUTLINE = 0xFFFFFFFF;
    private static final int EDIT_FILL = 0x183D81F7;
    private static final int GUIDE_GRID = 0x303D81F7;
    private static final int GUIDE_GRID_MAJOR = 0x4C3D81F7;
    private static final int GUIDE_CENTER = 0xB83D81F7;
    private static final int GUIDE_SNAP = 0xE8FFD166;
    private static final Path HUD_FILE = PathManager.HUD_PATH.resolve("hud.json");

    private static final Map<String, Layout> layouts = new LinkedHashMap<>();
    private static UIComponent<?> draggingComponent;
    private static float dragOffsetX;
    private static float dragOffsetY;
    private static float activeVerticalSnapLine = Float.NaN;
    private static float activeHorizontalSnapLine = Float.NaN;
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

    public static List<UIComponent<?>> getVisibleComponents() {
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
            for (UIComponent<?> component : getVisibleComponents()) {
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
            if (draggingComponent != null) {
                renderDragGuides();
            }

            for (UIComponent<?> component : getVisibleComponents()) {
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

        UIComponent<?> component = getComponentAt(mouseX, mouseY);
        if (component == null) {
            return false;
        }

        Layout layout = layoutFor(component);
        draggingComponent = component;
        dragOffsetX = (float)mouseX - layout.x;
        dragOffsetY = (float)mouseY - layout.y;
        clearSnapGuides();
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
        applySnapping(draggingComponent, layout);
        clampLayoutToScreen(draggingComponent, layout);
        dirty = true;
        return true;
    }

    public static boolean stopDragging() {
        if (draggingComponent == null) {
            return false;
        }

        draggingComponent = null;
        clearSnapGuides();
        if (dirty) {
            save();
        }
        return true;
    }

    public static boolean scaleHovered(double mouseX, double mouseY, double scrollY) {
        if (!HUD.INSTANCE.isEnabled() || scrollY == 0.0D) {
            return false;
        }

        UIComponent<?> component = getComponentAt(mouseX, mouseY);
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

    public static AABB getDisplayBounds(UIComponent<?> component) {
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

    public static float getScale(UIComponent<?> component) {
        return layoutFor(component).scale;
    }

    private static void renderDragGuides() {
        if (mc.getWindow() == null) {
            return;
        }

        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        renderGuideGrid(screenWidth, screenHeight);
        renderCenterCross(screenWidth, screenHeight);
        renderActiveSnapGuides(screenWidth, screenHeight);
    }

    private static void renderGuideGrid(float screenWidth, float screenHeight) {
        for (int index = 1; ; index++) {
            float x = index * GUIDE_GRID_SPACING;
            if (x >= screenWidth) {
                break;
            }

            boolean major = index % GUIDE_GRID_MAJOR_INTERVAL == 0;
            drawDashedVerticalLine(x, 0.0F, screenHeight, GUIDE_GRID_DASH, GUIDE_GRID_GAP, 0.75F, major ? GUIDE_GRID_MAJOR : GUIDE_GRID);
        }

        for (int index = 1; ; index++) {
            float y = index * GUIDE_GRID_SPACING;
            if (y >= screenHeight) {
                break;
            }

            boolean major = index % GUIDE_GRID_MAJOR_INTERVAL == 0;
            drawDashedHorizontalLine(0.0F, screenWidth, y, GUIDE_GRID_DASH, GUIDE_GRID_GAP, 0.75F, major ? GUIDE_GRID_MAJOR : GUIDE_GRID);
        }
    }

    private static void renderCenterCross(float screenWidth, float screenHeight) {
        float centerX = screenWidth * 0.5F;
        float centerY = screenHeight * 0.5F;
        drawDashedVerticalLine(centerX, 0.0F, screenHeight, GUIDE_CENTER_DASH, GUIDE_CENTER_GAP, 1.25F, GUIDE_CENTER);
        drawDashedHorizontalLine(0.0F, screenWidth, centerY, GUIDE_CENTER_DASH, GUIDE_CENTER_GAP, 1.25F, GUIDE_CENTER);
    }

    private static void renderActiveSnapGuides(float screenWidth, float screenHeight) {
        if (!Float.isNaN(activeVerticalSnapLine)) {
            drawDashedVerticalLine(activeVerticalSnapLine, 0.0F, screenHeight, GUIDE_CENTER_DASH, GUIDE_CENTER_GAP, 1.4F, GUIDE_SNAP);
        }

        if (!Float.isNaN(activeHorizontalSnapLine)) {
            drawDashedHorizontalLine(0.0F, screenWidth, activeHorizontalSnapLine, GUIDE_CENTER_DASH, GUIDE_CENTER_GAP, 1.4F, GUIDE_SNAP);
        }
    }

    private static void drawDashedVerticalLine(float x, float startY, float endY, float dashLength, float gapLength, float strokeWidth, int color) {
        float step = dashLength + gapLength;
        for (float y = startY; y < endY; y += step) {
            Render2DUtility.drawLine(x, y, x, Math.min(y + dashLength, endY), strokeWidth, color);
        }
    }

    private static void drawDashedHorizontalLine(float startX, float endX, float y, float dashLength, float gapLength, float strokeWidth, int color) {
        float step = dashLength + gapLength;
        for (float x = startX; x < endX; x += step) {
            Render2DUtility.drawLine(x, y, Math.min(x + dashLength, endX), y, strokeWidth, color);
        }
    }

    private static void applySnapping(UIComponent<?> component, Layout layout) {
        clearSnapGuides();
        SnapCandidate horizontal = findHorizontalSnap(component, layout);
        if (horizontal != null) {
            layout.x += horizontal.delta;
            activeVerticalSnapLine = horizontal.guide;
        }

        SnapCandidate vertical = findVerticalSnap(component, layout);
        if (vertical != null) {
            layout.y += vertical.delta;
            activeHorizontalSnapLine = vertical.guide;
        }
    }

    private static SnapCandidate findHorizontalSnap(UIComponent<?> component, Layout layout) {
        if (mc.getWindow() == null) {
            return null;
        }

        AABB base = component.getBoundingBox();
        float scale = layout.scale;
        float left = layout.x + (float)base.minX * scale;
        float width = Math.max(1.0F, (float)base.getXsize() * scale);
        float center = left + width * 0.5F;
        float right = left + width;
        float screenWidth = mc.getWindow().getGuiScaledWidth();

        SnapCandidate best = null;
        best = considerSnap(best, left, 0.0F, SNAP_ALIGNMENT_RANGE, 30);
        best = considerSnap(best, right, screenWidth, SNAP_ALIGNMENT_RANGE, 30);
        best = considerSnap(best, center, screenWidth * 0.5F, SNAP_CENTER_RANGE, 40);
        best = considerGridSnap(best, left, center, right, screenWidth);
        best = considerComponentSnaps(best, component, left, center, right, true);
        return best;
    }

    private static SnapCandidate findVerticalSnap(UIComponent<?> component, Layout layout) {
        if (mc.getWindow() == null) {
            return null;
        }

        AABB base = component.getBoundingBox();
        float scale = layout.scale;
        float top = layout.y + (float)base.minY * scale;
        float height = Math.max(1.0F, (float)base.getYsize() * scale);
        float center = top + height * 0.5F;
        float bottom = top + height;
        float screenHeight = mc.getWindow().getGuiScaledHeight();

        SnapCandidate best = null;
        best = considerSnap(best, top, 0.0F, SNAP_ALIGNMENT_RANGE, 30);
        best = considerSnap(best, bottom, screenHeight, SNAP_ALIGNMENT_RANGE, 30);
        best = considerSnap(best, center, screenHeight * 0.5F, SNAP_CENTER_RANGE, 40);
        best = considerGridSnap(best, top, center, bottom, screenHeight);
        best = considerComponentSnaps(best, component, top, center, bottom, false);
        return best;
    }

    private static SnapCandidate considerGridSnap(SnapCandidate best, float first, float center, float last, float screenSize) {
        for (int index = 1; ; index++) {
            float guide = index * GUIDE_GRID_SPACING;
            if (guide >= screenSize) {
                break;
            }

            best = considerSnap(best, first, guide, SNAP_GRID_RANGE, 10);
            best = considerSnap(best, center, guide, SNAP_GRID_RANGE, 10);
            best = considerSnap(best, last, guide, SNAP_GRID_RANGE, 10);
        }
        return best;
    }

    private static SnapCandidate considerComponentSnaps(SnapCandidate best, UIComponent<?> component, float first, float center, float last, boolean horizontal) {
        for (UIComponent<?> other : getVisibleComponents()) {
            if (other == component) {
                continue;
            }

            AABB bounds = getDisplayBounds(other);
            float otherFirst = horizontal ? (float)bounds.minX : (float)bounds.minY;
            float otherLast = horizontal ? (float)bounds.maxX : (float)bounds.maxY;
            float otherCenter = (otherFirst + otherLast) * 0.5F;

            best = considerAxisSnaps(best, first, center, last, otherFirst, otherCenter, otherLast);
        }
        return best;
    }

    private static SnapCandidate considerAxisSnaps(SnapCandidate best, float first, float center, float last,
                                                   float targetFirst, float targetCenter, float targetLast) {
        best = considerSnap(best, first, targetFirst, SNAP_ALIGNMENT_RANGE, 20);
        best = considerSnap(best, first, targetCenter, SNAP_ALIGNMENT_RANGE, 20);
        best = considerSnap(best, first, targetLast, SNAP_ALIGNMENT_RANGE, 20);
        best = considerSnap(best, center, targetFirst, SNAP_ALIGNMENT_RANGE, 20);
        best = considerSnap(best, center, targetCenter, SNAP_ALIGNMENT_RANGE, 20);
        best = considerSnap(best, center, targetLast, SNAP_ALIGNMENT_RANGE, 20);
        best = considerSnap(best, last, targetFirst, SNAP_ALIGNMENT_RANGE, 20);
        best = considerSnap(best, last, targetCenter, SNAP_ALIGNMENT_RANGE, 20);
        best = considerSnap(best, last, targetLast, SNAP_ALIGNMENT_RANGE, 20);
        return best;
    }

    private static SnapCandidate considerSnap(SnapCandidate best, float anchor, float guide, float range, int priority) {
        float distance = Math.abs(guide - anchor);
        if (distance > range) {
            return best;
        }

        if (best != null) {
            if (priority < best.priority) {
                return best;
            }

            if (priority == best.priority && distance >= best.distance) {
                return best;
            }
        }

        return new SnapCandidate(guide - anchor, guide, distance, priority);
    }

    private static void clearSnapGuides() {
        activeVerticalSnapLine = Float.NaN;
        activeHorizontalSnapLine = Float.NaN;
    }

    private static void renderComponent(GuiGraphics graphics, UIComponent<?> component, float partialTicks) {
        Layout layout = layoutFor(component);
        Render2DUtility.withTranslation(layout.x, layout.y, () ->
            Render2DUtility.withScale(layout.scale, layout.scale, 0.0F, 0.0F, () ->
                component.render(graphics, partialTicks, layout.scale)
            )
        );
    }

    private static UIComponent<?> getComponentAt(double mouseX, double mouseY) {
        List<UIComponent<?>> components = getVisibleComponents();
        for (int index = components.size() - 1; index >= 0; index--) {
            UIComponent<?> component = components.get(index);
            if (isInside(mouseX, mouseY, getDisplayBounds(component))) {
                return component;
            }
        }
        return null;
    }

    private static boolean isInside(double mouseX, double mouseY, AABB bounds) {
        return mouseX >= bounds.minX && mouseX <= bounds.maxX && mouseY >= bounds.minY && mouseY <= bounds.maxY;
    }

    private static Layout layoutFor(UIComponent<?> component) {
        Layout layout = layouts.computeIfAbsent(component.getId(), ignored -> defaultLayout(component));
        if (clampLayoutToScreen(component, layout)) {
            dirty = true;
        }
        return layout;
    }

    private static void ensureDefaultLayouts() {
        for (UIComponent<?> component : HUD.components) {
            layoutFor(component);
        }
    }

    private static Layout defaultLayout(UIComponent<?> component) {
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
        for (UIComponent<?> component : HUD.components) {
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

    private static boolean clampLayoutToScreen(UIComponent<?> component, Layout layout) {
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

    private static final class SnapCandidate {
        private final float delta;
        private final float guide;
        private final float distance;
        private final int priority;

        private SnapCandidate(float delta, float guide, float distance, int priority) {
            this.delta = delta;
            this.guide = guide;
            this.distance = distance;
            this.priority = priority;
        }
    }
}
