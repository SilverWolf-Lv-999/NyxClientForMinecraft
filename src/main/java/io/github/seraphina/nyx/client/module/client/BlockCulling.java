package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

@ModuleInfo(name = "nyxclient.module.blockculling.name", description = "nyxclient.module.blockculling.description", category = Category.CLIENT)
public class BlockCulling extends Module {
    public static final BlockCulling INSTANCE = new BlockCulling();

    private static final double MIN_VISIBLE_DISTANCE_SQR = 4.0D;
    private static final double HIT_EPSILON = 1.0E-4D;
    private static final double BOX_EPSILON = 1.0E-3D;
    private static final double MAX_CULLABLE_BOX_SIZE = 64.0D;
    private static final double[] SAMPLE_FACTORS = {0.1D, 0.9D};
    private static final int CACHE_TICKS = 8;
    private static final int CACHE_TICK_JITTER = 5;
    private static final int CACHE_PRUNE_INTERVAL_TICKS = 40;
    private static final int CACHE_KEEPALIVE_TICKS = 120;
    private static final int MAX_OCCLUSION_TESTS_PER_FRAME = 2;

    public final BoolValue rayOcclusion = ValueBuild.boolSetting("ray occlusion", false, this);
    public final BoolValue blockEntityFaces = ValueBuild.boolSetting("block entity faces", true, this);
    public final BoolValue preserveBreaking = ValueBuild.boolSetting("preserve breaking", true, this);

    private final Map<Long, OcclusionCacheEntry> occlusionCache = new HashMap<>();
    private final Map<RenderType, RenderType> culledRenderTypes = new IdentityHashMap<>();

    private Field renderTypeStateField;
    private Field renderSetupTexturesField;
    private Method textureBindingLocationMethod;
    private boolean renderTypeReflectionFailed;
    private Object cachedLevel;
    private long lastPruneTick = Long.MIN_VALUE;
    private int remainingOcclusionTests = MAX_OCCLUSION_TESTS_PER_FRAME;
    private int submittingBlockEntityDepth;

    public void beginBlockEntitySubmit(BlockEntityRenderState state) {
        if (state != null && shouldTrackBlockEntityFaces()) {
            submittingBlockEntityDepth++;
        }
    }

    public void endBlockEntitySubmit() {
        if (submittingBlockEntityDepth > 0) {
            submittingBlockEntityDepth--;
        }
    }

    public void beginFrame() {
        if (!isEnabled()) {
            return;
        }

        remainingOcclusionTests = MAX_OCCLUSION_TESTS_PER_FRAME;

        if (mc.level == null) {
            cachedLevel = null;
            occlusionCache.clear();
            return;
        }

        if (!rayOcclusion.getValue()) {
            if (!occlusionCache.isEmpty()) {
                occlusionCache.clear();
            }
            cachedLevel = mc.level;
            return;
        }

        ensureCurrentLevelCache();
        pruneCache(mc.level.getGameTime());
    }

    @Override
    public void onDisable() {
        submittingBlockEntityDepth = 0;
        occlusionCache.clear();
        culledRenderTypes.clear();
    }

    public RenderType cullBlockEntityRenderType(RenderType original) {
        if (!shouldCullBlockEntityFaces(original)) {
            return original;
        }

        RenderType cached = culledRenderTypes.get(original);
        if (cached != null) {
            return cached;
        }

        RenderType culled = createCulledRenderType(original);
        culledRenderTypes.put(original, culled);
        return culled;
    }

    public boolean shouldCheckBlockEntityOcclusion(ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        return isEnabled()
                && rayOcclusion.getValue()
                && mc.level != null
                && mc.player != null
                && (!preserveBreaking.getValue() || breakProgress == null);
    }

    public boolean shouldCullBlockEntity(
            BlockEntity blockEntity,
            AABB renderBox,
            Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress
    ) {
        if (!isEnabled()
                || !rayOcclusion.getValue()
                || blockEntity == null
                || cameraPos == null
                || mc.level == null
                || mc.player == null) {
            return false;
        }

        if (preserveBreaking.getValue() && breakProgress != null) {
            return false;
        }

        AABB box = usableBox(blockEntity, renderBox);
        if (!isCullableBox(box)) {
            return false;
        }

        if (box.contains(cameraPos) || box.distanceToSqr(cameraPos) <= MIN_VISIBLE_DISTANCE_SQR) {
            return false;
        }

        Entity clipEntity = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        return shouldCullCached(blockEntity, cameraPos, box, clipEntity);
    }

    private boolean shouldCullBlockEntityFaces(RenderType original) {
        return submittingBlockEntityDepth > 0
                && original != null
                && !renderTypeReflectionFailed
                && !original.pipeline().isCull();
    }

    private boolean shouldTrackBlockEntityFaces() {
        return isEnabled() && blockEntityFaces.getValue() && !renderTypeReflectionFailed;
    }

    private RenderType createCulledRenderType(RenderType original) {
        String renderTypeName = original.toString();
        if (!renderTypeName.contains("entity_cutout_no_cull") || renderTypeName.contains("z_offset")) {
            return original;
        }

        Identifier texture = extractTexture(original);
        return texture != null ? RenderTypes.entityCutout(texture) : original;
    }

    private Identifier extractTexture(RenderType renderType) {
        if (renderTypeReflectionFailed) {
            return null;
        }

        try {
            Field stateField = renderTypeStateField();
            Field texturesField = renderSetupTexturesField();
            Object renderSetup = stateField.get(renderType);
            if (!(texturesField.get(renderSetup) instanceof Map<?, ?> textures)) {
                return null;
            }

            Object sampler = textures.get("Sampler0");
            if (sampler == null && !textures.isEmpty()) {
                sampler = textures.values().iterator().next();
            }

            if (sampler == null) {
                return null;
            }

            Method locationMethod = textureBindingLocationMethod(sampler);
            Object location = locationMethod.invoke(sampler);
            return location instanceof Identifier identifier ? identifier : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            renderTypeReflectionFailed = true;
            return null;
        }
    }

    private Field renderTypeStateField() throws NoSuchFieldException {
        if (renderTypeStateField == null) {
            renderTypeStateField = RenderType.class.getDeclaredField("state");
            renderTypeStateField.setAccessible(true);
        }
        return renderTypeStateField;
    }

    private Field renderSetupTexturesField() throws NoSuchFieldException {
        if (renderSetupTexturesField == null) {
            renderSetupTexturesField = RenderSetup.class.getDeclaredField("textures");
            renderSetupTexturesField.setAccessible(true);
        }
        return renderSetupTexturesField;
    }

    private Method textureBindingLocationMethod(Object textureBinding) throws NoSuchMethodException {
        if (textureBindingLocationMethod == null) {
            textureBindingLocationMethod = textureBinding.getClass().getDeclaredMethod("location");
            textureBindingLocationMethod.setAccessible(true);
        }
        return textureBindingLocationMethod;
    }

    private boolean shouldCullCached(BlockEntity blockEntity, Vec3 cameraPos, AABB box, Entity clipEntity) {
        ensureCurrentLevelCache();

        long gameTime = mc.level.getGameTime();
        long blockKey = blockEntity.getBlockPos().asLong();
        long cameraBlockKey = blockKey(cameraPos);
        OcclusionCacheEntry cached = occlusionCache.get(blockKey);
        if (cached != null) {
            cached.lastSeenTick = gameTime;
            if (cached.cameraBlockKey == cameraBlockKey && cached.validUntilTick >= gameTime) {
                return cached.occluded;
            }
        }

        if (remainingOcclusionTests <= 0) {
            return false;
        }

        remainingOcclusionTests--;
        boolean occluded = !isBoxVisible(cameraPos, box, clipEntity);
        long validUntilTick = gameTime + CACHE_TICKS + Math.floorMod(Long.hashCode(blockKey), CACHE_TICK_JITTER + 1);
        occlusionCache.put(blockKey, new OcclusionCacheEntry(occluded, cameraBlockKey, validUntilTick, gameTime));
        return occluded;
    }

    private void ensureCurrentLevelCache() {
        if (cachedLevel != mc.level) {
            cachedLevel = mc.level;
            occlusionCache.clear();
            lastPruneTick = Long.MIN_VALUE;
        }
    }

    private void pruneCache(long gameTime) {
        if (gameTime - lastPruneTick < CACHE_PRUNE_INTERVAL_TICKS) {
            return;
        }

        lastPruneTick = gameTime;
        occlusionCache.values().removeIf(entry -> gameTime - entry.lastSeenTick > CACHE_KEEPALIVE_TICKS);
    }

    private boolean isBoxVisible(Vec3 cameraPos, AABB box, Entity clipEntity) {
        if (isPointVisible(cameraPos, box.getCenter(), clipEntity, box)) {
            return true;
        }

        for (double xFactor : SAMPLE_FACTORS) {
            double x = MathUtility.lerp(box.minX, box.maxX, xFactor);
            for (double yFactor : SAMPLE_FACTORS) {
                double y = MathUtility.lerp(box.minY, box.maxY, yFactor);
                for (double zFactor : SAMPLE_FACTORS) {
                    if (isPointVisible(cameraPos, new Vec3(x, y, MathUtility.lerp(box.minZ, box.maxZ, zFactor)), clipEntity, box)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isPointVisible(Vec3 cameraPos, Vec3 point, Entity clipEntity, AABB targetBox) {
        double targetDistanceSqr = cameraPos.distanceToSqr(point);
        if (targetDistanceSqr <= HIT_EPSILON) {
            return true;
        }

        HitResult result = mc.level.clip(new ClipContext(
                cameraPos,
                point,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                clipEntity
        ));

        if (result.getType() == HitResult.Type.MISS) {
            return true;
        }

        Vec3 hit = result.getLocation();
        return cameraPos.distanceToSqr(hit) >= targetDistanceSqr - HIT_EPSILON
                || targetBox.inflate(BOX_EPSILON).contains(hit);
    }

    private static AABB usableBox(BlockEntity blockEntity, AABB renderBox) {
        if (renderBox != null && !renderBox.hasNaN() && renderBox.getSize() > 0.0D) {
            return renderBox;
        }

        return AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
    }

    private static boolean isCullableBox(AABB box) {
        return Double.isFinite(box.minX)
                && Double.isFinite(box.minY)
                && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX)
                && Double.isFinite(box.maxY)
                && Double.isFinite(box.maxZ)
                && box.getSize() <= MAX_CULLABLE_BOX_SIZE;
    }

    private static long blockKey(Vec3 pos) {
        return packBlockKey((int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z));
    }

    private static long packBlockKey(int x, int y, int z) {
        return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | ((long)y & 0xFFFL);
    }

    private static final class OcclusionCacheEntry {
        private final boolean occluded;
        private final long cameraBlockKey;
        private final long validUntilTick;
        private long lastSeenTick;

        private OcclusionCacheEntry(boolean occluded, long cameraBlockKey, long validUntilTick, long lastSeenTick) {
            this.occluded = occluded;
            this.cameraBlockKey = cameraBlockKey;
            this.validUntilTick = validUntilTick;
            this.lastSeenTick = lastSeenTick;
        }
    }
}
