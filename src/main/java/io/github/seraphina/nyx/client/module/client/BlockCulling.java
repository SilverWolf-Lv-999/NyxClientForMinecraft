package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
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
import java.util.Collections;
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

    public final IntValue checkRange = ValueBuild.intSetting("check range", 128, 8, 512, 8, this);
    public final BoolValue blockEntityOcclusion = ValueBuild.boolSetting("block entity occlusion", true, this);
    public final BoolValue blockEntityFaces = ValueBuild.boolSetting("block entity faces", true, this);
    public final BoolValue preserveBreaking = ValueBuild.boolSetting("preserve breaking", true, this);

    private final ThreadLocal<BlockEntityRenderState> submittingBlockEntity = new ThreadLocal<>();
    private final Map<RenderType, RenderType> culledRenderTypes = Collections.synchronizedMap(new IdentityHashMap<>());

    private Field renderTypeStateField;
    private Field renderSetupTexturesField;
    private Method textureBindingLocationMethod;
    private boolean renderTypeReflectionFailed;

    public void beginBlockEntitySubmit(BlockEntityRenderState state) {
        submittingBlockEntity.set(state);
    }

    public void endBlockEntitySubmit() {
        submittingBlockEntity.remove();
    }

    @Override
    public void onDisable() {
        submittingBlockEntity.remove();
        culledRenderTypes.clear();
    }

    public RenderType cullBlockEntityRenderType(RenderType original) {
        if (!shouldCullBlockEntityFaces(original)) {
            return original;
        }

        return culledRenderTypes.computeIfAbsent(original, this::createCulledRenderType);
    }

    public boolean shouldCullBlockEntity(
            BlockEntity blockEntity,
            AABB renderBox,
            Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress
    ) {
        if (!isEnabled()
                || !blockEntityOcclusion.getValue()
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

        int range = checkRange.getValue();
        if (box.getCenter().distanceToSqr(cameraPos) > (double)range * range) {
            return false;
        }

        Entity clipEntity = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        return !isBoxVisible(cameraPos, box, clipEntity);
    }

    private boolean shouldCullBlockEntityFaces(RenderType original) {
        return isEnabled()
                && blockEntityFaces.getValue()
                && submittingBlockEntity.get() != null
                && original != null
                && !renderTypeReflectionFailed
                && !original.pipeline().isCull();
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

    private boolean isBoxVisible(Vec3 cameraPos, AABB box, Entity clipEntity) {
        if (isPointVisible(cameraPos, box.getCenter(), clipEntity, box)) {
            return true;
        }

        for (double xFactor : SAMPLE_FACTORS) {
            double x = lerp(box.minX, box.maxX, xFactor);
            for (double yFactor : SAMPLE_FACTORS) {
                double y = lerp(box.minY, box.maxY, yFactor);
                for (double zFactor : SAMPLE_FACTORS) {
                    if (isPointVisible(cameraPos, new Vec3(x, y, lerp(box.minZ, box.maxZ, zFactor)), clipEntity, box)) {
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

    private static double lerp(double min, double max, double factor) {
        return min + (max - min) * factor;
    }
}
