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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HUDManager implements IMinecraft {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CONFIG_VERSION = 1;
    private static final float MIN_SCALE = 0.5F;
    private static final float MAX_SCALE = 3.0F;
    private static final float SCALE_STEP = 0.08F;
    private static final float GUIDE_GRID_SPACING = 16.0F;
    private static final int GUIDE_GRID_MAJOR_INTERVAL = 4;
    private static final float GUIDE_GRID_STROKE = 0.5F;
    private static final float GUIDE_CENTER_DASH = 8.0F;
    private static final float GUIDE_CENTER_GAP = 5.0F;
    private static final float SNAP_GRID_RANGE = 3.0F;
    private static final float SNAP_ALIGNMENT_RANGE = 6.0F;
    private static final float SNAP_CENTER_RANGE = 8.0F;
    private static final double DRAG_MOUSE_EPSILON = 0.01D;
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
    private static AABB draggingBaseBounds;
    private static List<ComponentBounds> dragSnapBounds = Collections.emptyList();
    private static double lastDragMouseX = Double.NaN;
    private static double lastDragMouseY = Double.NaN;
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

        List<UIComponent<?>> components = getVisibleComponents();
        Render2DUtility.withGuiGraphics(graphics, () -> {
            for (UIComponent<?> component : components) {
                renderComponent(graphics, component, partialTicks);
            }
        });
    }

    public static void renderEditor(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!HUD.INSTANCE.isEnabled()) {
            stopDragging();
            return;
        }

        List<UIComponent<?>> components = getVisibleComponents();
        Render2DUtility.withGuiGraphics(graphics, () -> {
            if (draggingComponent != null) {
                renderDragGuides();
            }

            for (UIComponent<?> component : components) {
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
        draggingBaseBounds = component.getBoundingBox();
        dragSnapBounds = createSnapBounds(component);
        lastDragMouseX = mouseX;
        lastDragMouseY = mouseY;
        clearSnapGuides();
        return true;
    }

    public static boolean drag(double mouseX, double mouseY) {
        if (draggingComponent == null) {
            return false;
        }

        if (Math.abs(mouseX - lastDragMouseX) < DRAG_MOUSE_EPSILON && Math.abs(mouseY - lastDragMouseY) < DRAG_MOUSE_EPSILON) {
            return true;
        }

        lastDragMouseX = mouseX;
        lastDragMouseY = mouseY;
        Layout layout = layoutFor(draggingComponent);
        AABB base = baseBoundsFor(draggingComponent);
        float oldX = layout.x;
        float oldY = layout.y;
        layout.x = (float)mouseX - dragOffsetX;
        layout.y = (float)mouseY - dragOffsetY;
        clampLayoutToScreen(layout, base);
        applySnapping(draggingComponent, layout);
        clampLayoutToScreen(layout, base);
        updateAnchorsFromResolved(layout, base);
        if (oldX != layout.x || oldY != layout.y) {
            dirty = true;
        }
        return true;
    }

    public static boolean stopDragging() {
        if (draggingComponent == null) {
            return false;
        }

        draggingComponent = null;
        draggingBaseBounds = null;
        dragSnapBounds = Collections.emptyList();
        lastDragMouseX = Double.NaN;
        lastDragMouseY = Double.NaN;
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
        AABB base = baseBoundsFor(component);
        clampLayoutToScreen(layout, base);
        updateAnchorsFromResolved(layout, base);
        dirty = true;
        save();
        return true;
    }

    public static AABB getDisplayBounds(UIComponent<?> component) {
        Layout layout = layoutFor(component);
        AABB base = baseBoundsFor(component);
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
            drawVerticalGuideLine(x, 0.0F, screenHeight, major ? 0.75F : GUIDE_GRID_STROKE, major ? GUIDE_GRID_MAJOR : GUIDE_GRID);
        }

        for (int index = 1; ; index++) {
            float y = index * GUIDE_GRID_SPACING;
            if (y >= screenHeight) {
                break;
            }

            boolean major = index % GUIDE_GRID_MAJOR_INTERVAL == 0;
            drawHorizontalGuideLine(0.0F, screenWidth, y, major ? 0.75F : GUIDE_GRID_STROKE, major ? GUIDE_GRID_MAJOR : GUIDE_GRID);
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

    private static void drawVerticalGuideLine(float x, float startY, float endY, float strokeWidth, int color) {
        float top = Math.min(startY, endY);
        float height = Math.abs(endY - startY);
        Render2DUtility.drawRect(x - strokeWidth * 0.5F, top, strokeWidth, height, color);
    }

    private static void drawHorizontalGuideLine(float startX, float endX, float y, float strokeWidth, int color) {
        float left = Math.min(startX, endX);
        float width = Math.abs(endX - startX);
        Render2DUtility.drawRect(left, y - strokeWidth * 0.5F, width, strokeWidth, color);
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

        AABB base = baseBoundsFor(component);
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
        best = considerComponentSnaps(best, left, center, right, true);
        return best;
    }

    private static SnapCandidate findVerticalSnap(UIComponent<?> component, Layout layout) {
        if (mc.getWindow() == null) {
            return null;
        }

        AABB base = baseBoundsFor(component);
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
        best = considerComponentSnaps(best, top, center, bottom, false);
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

    private static SnapCandidate considerComponentSnaps(SnapCandidate best, float first, float center, float last, boolean horizontal) {
        for (ComponentBounds bounds : dragSnapBounds) {
            float otherFirst = horizontal ? bounds.minX : bounds.minY;
            float otherLast = horizontal ? bounds.maxX : bounds.maxY;
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

    private static AABB baseBoundsFor(UIComponent<?> component) {
        if (component == draggingComponent && draggingBaseBounds != null) {
            return draggingBaseBounds;
        }
        return component.getBoundingBox();
    }

    private static List<ComponentBounds> createSnapBounds(UIComponent<?> component) {
        List<UIComponent<?>> components = getVisibleComponents();
        List<ComponentBounds> bounds = new ArrayList<>(Math.max(0, components.size() - 1));
        for (UIComponent<?> other : components) {
            if (other == component) {
                continue;
            }

            AABB displayBounds = getDisplayBounds(other);
            bounds.add(new ComponentBounds(
                (float)displayBounds.minX,
                (float)displayBounds.minY,
                (float)displayBounds.maxX,
                (float)displayBounds.maxY
            ));
        }
        return bounds;
    }

    private static Layout layoutFor(UIComponent<?> component) {
        Layout layout = layouts.computeIfAbsent(component.getId(), ignored -> defaultLayout(component));
        AABB base = baseBoundsFor(component);
        initializeAnchors(layout, base);
        resolveLayoutPosition(layout, base);
        if (clampLayoutToScreen(layout, base)) {
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
        HorizontalAnchor horizontalAnchor = readHorizontalAnchor(object);
        VerticalAnchor verticalAnchor = readVerticalAnchor(object);
        Float anchorX = readFloat(object, "anchorX");
        Float anchorY = readFloat(object, "anchorY");

        if (x == null || y == null) {
            return null;
        }

        return new Layout(
            x,
            y,
            MathUtility.clamp(scale == null ? 1.0F : scale, MIN_SCALE, MAX_SCALE),
            horizontalAnchor,
            verticalAnchor,
            anchorX,
            anchorY
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

    private static HorizontalAnchor readHorizontalAnchor(JsonObject object) {
        String value = readString(object, "horizontalAnchor");
        if (value == null) {
            return null;
        }

        try {
            return HorizontalAnchor.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static VerticalAnchor readVerticalAnchor(JsonObject object) {
        String value = readString(object, "verticalAnchor");
        if (value == null) {
            return null;
        }

        try {
            return VerticalAnchor.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return element.getAsString();
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
            object.addProperty("horizontalAnchor", layout.horizontalAnchor.name().toLowerCase(Locale.ROOT));
            object.addProperty("verticalAnchor", layout.verticalAnchor.name().toLowerCase(Locale.ROOT));
            object.addProperty("anchorX", layout.anchorX);
            object.addProperty("anchorY", layout.anchorY);
            components.add(component.getId(), object);
        }

        root.addProperty("version", CONFIG_VERSION);
        root.add("components", components);
        return root;
    }

    private static boolean clampLayoutToScreen(Layout layout, AABB base) {
        if (mc.getWindow() == null) {
            return false;
        }

        float oldX = layout.x;
        float oldY = layout.y;
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

        if (oldX != layout.x || oldY != layout.y) {
            updateAnchorCoordinates(layout, base);
        }
        return oldX != layout.x || oldY != layout.y;
    }

    private static void initializeAnchors(Layout layout, AABB base) {
        if (layout.horizontalAnchor == null) {
            layout.horizontalAnchor = inferHorizontalAnchor(layout.x, base, layout.scale);
        }

        if (layout.verticalAnchor == null) {
            layout.verticalAnchor = inferVerticalAnchor(layout.y, base, layout.scale);
        }

        if (!layout.hasAnchorX) {
            layout.anchorX = horizontalAnchorScreen(layout.x, base, layout.scale, layout.horizontalAnchor);
            layout.hasAnchorX = true;
        }

        if (!layout.hasAnchorY) {
            layout.anchorY = verticalAnchorScreen(layout.y, base, layout.scale, layout.verticalAnchor);
            layout.hasAnchorY = true;
        }
    }

    private static void resolveLayoutPosition(Layout layout, AABB base) {
        layout.x = layout.anchorX - horizontalAnchorLocal(base, layout.horizontalAnchor) * layout.scale;
        layout.y = layout.anchorY - verticalAnchorLocal(base, layout.verticalAnchor) * layout.scale;
    }

    private static void updateAnchorsFromResolved(Layout layout, AABB base) {
        layout.horizontalAnchor = inferHorizontalAnchor(layout.x, base, layout.scale);
        layout.verticalAnchor = inferVerticalAnchor(layout.y, base, layout.scale);
        updateAnchorCoordinates(layout, base);
    }

    private static void updateAnchorCoordinates(Layout layout, AABB base) {
        layout.anchorX = horizontalAnchorScreen(layout.x, base, layout.scale, layout.horizontalAnchor);
        layout.anchorY = verticalAnchorScreen(layout.y, base, layout.scale, layout.verticalAnchor);
        layout.hasAnchorX = true;
        layout.hasAnchorY = true;
    }

    private static HorizontalAnchor inferHorizontalAnchor(float x, AABB base, float scale) {
        if (mc.getWindow() == null) {
            return HorizontalAnchor.LEFT;
        }

        float left = x + (float)base.minX * scale;
        float right = x + (float)base.maxX * scale;
        float center = (left + right) * 0.5F;
        float middle = mc.getWindow().getGuiScaledWidth() * 0.5F;
        if (Math.abs(center - middle) <= SNAP_CENTER_RANGE) {
            return HorizontalAnchor.CENTER;
        }
        return center >= middle ? HorizontalAnchor.RIGHT : HorizontalAnchor.LEFT;
    }

    private static VerticalAnchor inferVerticalAnchor(float y, AABB base, float scale) {
        if (mc.getWindow() == null) {
            return VerticalAnchor.TOP;
        }

        float top = y + (float)base.minY * scale;
        float bottom = y + (float)base.maxY * scale;
        float center = (top + bottom) * 0.5F;
        float middle = mc.getWindow().getGuiScaledHeight() * 0.5F;
        if (Math.abs(center - middle) <= SNAP_CENTER_RANGE) {
            return VerticalAnchor.CENTER;
        }
        return center >= middle ? VerticalAnchor.BOTTOM : VerticalAnchor.TOP;
    }

    private static float horizontalAnchorScreen(float x, AABB base, float scale, HorizontalAnchor anchor) {
        return x + horizontalAnchorLocal(base, anchor) * scale;
    }

    private static float verticalAnchorScreen(float y, AABB base, float scale, VerticalAnchor anchor) {
        return y + verticalAnchorLocal(base, anchor) * scale;
    }

    private static float horizontalAnchorLocal(AABB base, HorizontalAnchor anchor) {
        return switch (anchor) {
            case LEFT -> (float)base.minX;
            case CENTER -> (float)((base.minX + base.maxX) * 0.5D);
            case RIGHT -> (float)base.maxX;
        };
    }

    private static float verticalAnchorLocal(AABB base, VerticalAnchor anchor) {
        return switch (anchor) {
            case TOP -> (float)base.minY;
            case CENTER -> (float)((base.minY + base.maxY) * 0.5D);
            case BOTTOM -> (float)base.maxY;
        };
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
        private HorizontalAnchor horizontalAnchor;
        private VerticalAnchor verticalAnchor;
        private float anchorX;
        private float anchorY;
        private boolean hasAnchorX;
        private boolean hasAnchorY;

        private Layout(float x, float y, float scale) {
            this(x, y, scale, null, null, null, null);
        }

        private Layout(float x, float y, float scale, HorizontalAnchor horizontalAnchor, VerticalAnchor verticalAnchor,
                       Float anchorX, Float anchorY) {
            this.x = x;
            this.y = y;
            this.scale = MathUtility.clamp(scale, MIN_SCALE, MAX_SCALE);
            this.horizontalAnchor = horizontalAnchor;
            this.verticalAnchor = verticalAnchor;
            if (anchorX != null) {
                this.anchorX = anchorX;
                this.hasAnchorX = true;
            }
            if (anchorY != null) {
                this.anchorY = anchorY;
                this.hasAnchorY = true;
            }
        }
    }

    private enum HorizontalAnchor {
        LEFT,
        CENTER,
        RIGHT
    }

    private enum VerticalAnchor {
        TOP,
        CENTER,
        BOTTOM
    }

    private static final class ComponentBounds {
        private final float minX;
        private final float minY;
        private final float maxX;
        private final float maxY;

        private ComponentBounds(float minX, float minY, float maxX, float maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
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
