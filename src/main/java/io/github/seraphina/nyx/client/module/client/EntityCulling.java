package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.visual.Chams;
import io.github.seraphina.nyx.client.module.visual.ESP;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

@ModuleInfo(name = "nyxclient.module.entityculling.name", description = "nyxclient.module.entityculling.description", category = Category.CLIENT)
public class EntityCulling extends Module {
    public static final EntityCulling INSTANCE = new EntityCulling();

    private static final double MIN_VISIBLE_DISTANCE_SQR = 4.0D;
    private static final double HIT_EPSILON = 1.0E-4D;
    private static final double[] SAMPLE_FACTORS = {0.1D, 0.9D};
    private static final int CACHE_TICKS = 4;
    private static final int CACHE_TICK_JITTER = 3;
    private static final int CACHE_PRUNE_INTERVAL_TICKS = 40;
    private static final int CACHE_KEEPALIVE_TICKS = 100;
    private static final int MAX_OCCLUSION_TESTS_PER_FRAME = 2;

    public final BoolValue rayOcclusion = ValueBuild.boolSetting("ray occlusion", false, this);
    public final BoolValue cullHiddenFaces = ValueBuild.boolSetting("cull hidden faces", true, this);
    public final BoolValue preserveVisualModules = ValueBuild.boolSetting("preserve visual modules", true, this);
    public final BoolValue preserveGlowing = ValueBuild.boolSetting("preserve glowing", true, this);

    private final Map<LivingEntityRenderState, LivingEntity> renderStateEntities = new IdentityHashMap<>();
    private final Map<UUID, OcclusionCacheEntry> occlusionCache = new HashMap<>();

    private Object cachedLevel;
    private long lastPruneTick = Long.MIN_VALUE;
    private int remainingOcclusionTests = MAX_OCCLUSION_TESTS_PER_FRAME;

    public void rememberEntity(LivingEntity entity, LivingEntityRenderState state) {
        if (!shouldTrackRenderStates() || entity == null || state == null) {
            return;
        }

        renderStateEntities.put(state, entity);
    }

    public void clearRenderStateCache() {
        renderStateEntities.clear();
    }

    public void beginFrame() {
        if (!isEnabled()) {
            return;
        }

        if (shouldTrackRenderStates()) {
            clearRenderStateCache();
        } else if (!renderStateEntities.isEmpty()) {
            clearRenderStateCache();
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
        clearRenderStateCache();
        occlusionCache.clear();
    }

    public boolean shouldCull(Entity entity, Camera camera) {
        if (!isEnabled() || !rayOcclusion.getValue() || entity == null || camera == null || mc.level == null || mc.player == null) {
            return false;
        }

        if (entity == mc.player || entity.isRemoved()) {
            return false;
        }

        if (shouldPreserveEntity(entity)) {
            return false;
        }

        Vec3 cameraPos = camera.position();
        AABB box = entity.getBoundingBox();
        if (box.hasNaN() || box.getSize() == 0.0D) {
            box = fallbackBox(entity);
        }

        if (box.contains(cameraPos) || box.distanceToSqr(cameraPos) <= MIN_VISIBLE_DISTANCE_SQR) {
            return false;
        }

        Entity clipEntity = camera.entity() != null ? camera.entity() : mc.player;
        return shouldCullCached(entity, cameraPos, box, clipEntity);
    }

    public RenderType cullHiddenFaces(LivingEntityRenderState state, Identifier texture, RenderType original) {
        if (!shouldCullHiddenFaces(state) || texture == null || original == null || original.pipeline().isCull()) {
            return original;
        }

        if (original != RenderTypes.entityCutoutNoCull(texture, !state.isInvisibleToPlayer)) {
            return original;
        }
        return RenderTypes.entityCutout(texture);
    }

    private boolean shouldCullHiddenFaces(LivingEntityRenderState state) {
        if (!shouldTrackRenderStates() || mc.player == null || mc.level == null || state == null) {
            return false;
        }

        LivingEntity entity = renderStateEntities.get(state);
        if (entity == null || entity == mc.player || entity.isRemoved()) {
            return false;
        }

        return !shouldPreserveEntity(entity);
    }

    private boolean shouldPreserveEntity(Entity entity) {
        if (preserveGlowing.getValue()
                && (entity.isCurrentlyGlowing() || entity.hasCustomOutlineRendering(mc.player))) {
            return true;
        }

        return preserveVisualModules.getValue()
                && (Chams.INSTANCE.requiresEntityRender(entity) || ESP.INSTANCE.requiresEntityRender(entity));
    }

    private boolean shouldTrackRenderStates() {
        return isEnabled() && cullHiddenFaces.getValue();
    }

    private boolean shouldCullCached(Entity entity, Vec3 cameraPos, AABB box, Entity clipEntity) {
        ensureCurrentLevelCache();

        long gameTime = mc.level.getGameTime();
        long cameraBlockKey = blockKey(cameraPos);
        long entityBlockKey = blockKey(box.getCenter());
        UUID key = entity.getUUID();
        OcclusionCacheEntry cached = occlusionCache.get(key);
        if (cached != null) {
            cached.lastSeenTick = gameTime;
            if (cached.cameraBlockKey == cameraBlockKey
                    && cached.targetBlockKey == entityBlockKey
                    && cached.validUntilTick >= gameTime) {
                return cached.occluded;
            }
        }

        if (remainingOcclusionTests <= 0) {
            return false;
        }

        remainingOcclusionTests--;
        boolean occluded = !isBoxVisible(cameraPos, box, clipEntity);
        long validUntilTick = gameTime + CACHE_TICKS + Math.floorMod(key.hashCode(), CACHE_TICK_JITTER + 1);
        occlusionCache.put(key, new OcclusionCacheEntry(occluded, cameraBlockKey, entityBlockKey, validUntilTick, gameTime));
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
        if (isPointVisible(cameraPos, box.getCenter(), clipEntity)) {
            return true;
        }

        for (double xFactor : SAMPLE_FACTORS) {
            double x = lerp(box.minX, box.maxX, xFactor);
            for (double yFactor : SAMPLE_FACTORS) {
                double y = lerp(box.minY, box.maxY, yFactor);
                for (double zFactor : SAMPLE_FACTORS) {
                    if (isPointVisible(cameraPos, new Vec3(x, y, lerp(box.minZ, box.maxZ, zFactor)), clipEntity)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isPointVisible(Vec3 cameraPos, Vec3 point, Entity clipEntity) {
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
        return result.getType() == HitResult.Type.MISS
                || cameraPos.distanceToSqr(result.getLocation()) >= targetDistanceSqr - HIT_EPSILON;
    }

    private static AABB fallbackBox(Entity entity) {
        return new AABB(
                entity.getX() - 0.5D,
                entity.getY() - 0.5D,
                entity.getZ() - 0.5D,
                entity.getX() + 0.5D,
                entity.getY() + 0.5D,
                entity.getZ() + 0.5D
        );
    }

    private static double lerp(double min, double max, double factor) {
        return min + (max - min) * factor;
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
        private final long targetBlockKey;
        private final long validUntilTick;
        private long lastSeenTick;

        private OcclusionCacheEntry(boolean occluded, long cameraBlockKey, long targetBlockKey, long validUntilTick, long lastSeenTick) {
            this.occluded = occluded;
            this.cameraBlockKey = cameraBlockKey;
            this.targetBlockKey = targetBlockKey;
            this.validUntilTick = validUntilTick;
            this.lastSeenTick = lastSeenTick;
        }
    }
}
