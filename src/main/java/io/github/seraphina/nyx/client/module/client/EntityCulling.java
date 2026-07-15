package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.visual.Chams;
import io.github.seraphina.nyx.client.module.visual.ESP;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
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

import java.util.IdentityHashMap;
import java.util.Map;

@ModuleInfo(name = "nyxclient.module.entityculling.name", description = "nyxclient.module.entityculling.description", category = Category.CLIENT)
public class EntityCulling extends Module {
    public static final EntityCulling INSTANCE = new EntityCulling();

    private static final double MIN_VISIBLE_DISTANCE_SQR = 4.0D;
    private static final double HIT_EPSILON = 1.0E-4D;
    private static final double[] SAMPLE_FACTORS = {0.1D, 0.9D};

    public final IntValue checkRange = ValueBuild.intSetting("check range", 128, 8, 512, 8, this);
    public final BoolValue cullHiddenFaces = ValueBuild.boolSetting("cull hidden faces", true, this);
    public final BoolValue preserveVisualModules = ValueBuild.boolSetting("preserve visual modules", true, this);
    public final BoolValue preserveGlowing = ValueBuild.boolSetting("preserve glowing", true, this);

    private final Map<LivingEntityRenderState, LivingEntity> renderStateEntities = new IdentityHashMap<>();

    public void rememberEntity(LivingEntity entity, LivingEntityRenderState state) {
        if (entity != null && state != null) {
            renderStateEntities.put(state, entity);
        }
    }

    public void clearRenderStateCache() {
        renderStateEntities.clear();
    }

    @Override
    public void onDisable() {
        clearRenderStateCache();
    }

    public boolean shouldCull(Entity entity, Camera camera) {
        if (!isEnabled() || entity == null || camera == null || mc.level == null || mc.player == null) {
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

        int range = checkRange.getValue();
        if (box.getCenter().distanceToSqr(cameraPos) > (double)range * range) {
            return false;
        }

        Entity clipEntity = camera.entity() != null ? camera.entity() : mc.player;
        return !isBoxVisible(cameraPos, box, clipEntity);
    }

    public RenderType cullHiddenFaces(LivingEntityRenderState state, Identifier texture, RenderType original) {
        if (!shouldCullHiddenFaces(state) || texture == null || original == null) {
            return original;
        }

        if (original != RenderTypes.entityCutoutNoCull(texture, !state.isInvisibleToPlayer)) {
            return original;
        }
        return RenderTypes.entityCutout(texture);
    }

    private boolean shouldCullHiddenFaces(LivingEntityRenderState state) {
        if (!isEnabled() || !cullHiddenFaces.getValue() || mc.player == null || mc.level == null || state == null) {
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
}
