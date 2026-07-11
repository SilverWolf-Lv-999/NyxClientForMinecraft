package io.github.seraphina.nyx.client.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.manager.FriendManager;
import io.github.seraphina.nyx.client.mixins.ModelPartAccessor;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.client.Friend;
import io.github.seraphina.nyx.client.module.other.Target;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@ModuleInfo(name = "nyxclient.module.esp.name", description = "nyxclient.module.esp.description", category = Category.VISUAL)
public class ESP extends Module {
    public static final ESP INSTANCE = new ESP();
    private static final double MIN_BONE_LENGTH_SQR = 1.0E-6D;

    public final EnumValue<RenderType> type = ValueBuild.enumSetting("type", RenderType.BONE, this);
    public final IntValue renderRange = ValueBuild.intSetting("render range", 128, 8, 512, 8, this);

    public final BoolValue fill = ValueBuild.boolSetting("fill", true, () -> type.is(RenderType.BOX), this);
    public final BoolValue outline = ValueBuild.boolSetting("outline", true, () -> type.is(RenderType.BOX), this);
    public final IntValue fillAlpha = ValueBuild.intSetting("fill alpha", 35, 0, 255, 5, () -> type.is(RenderType.BOX) && fill.getValue(), this);
    public final IntValue outlineAlpha = ValueBuild.intSetting("outline alpha", 220, 0, 255, 5, () -> type.is(RenderType.BOX) && outline.getValue(), this);
    public final IntValue boneAlpha = ValueBuild.intSetting("bone alpha", 230, 0, 255, 5, () -> type.is(RenderType.BONE), this);
    public final IntValue glowAlpha = ValueBuild.intSetting("glow alpha", 230, 0, 255, 5, () -> type.is(RenderType.GLOW), this);
    public final DoubleValue boxInflate = ValueBuild.doubleSetting("box inflate", 0.04D, 0.0D, 0.2D, 0.005D, () -> type.is(RenderType.BOX), this);

    public final ColorValue targetColor = ValueBuild.colorSetting("target color", new Color(84, 170, 255), false, this);
    public final ColorValue friendColor = ValueBuild.colorSetting("friend color", new Color(80, 255, 112), false, this);
    public final ColorValue hurtColor = ValueBuild.colorSetting("hurt color", new Color(255, 64, 64), false, this);

    private final Map<LivingEntityRenderState, LivingEntity> modelBoneEntities = new IdentityHashMap<>();
    private final Map<Integer, List<Render3DUtility.LineSegment>> modelBoneLines = new HashMap<>();

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (type.is(RenderType.GLOW)) {
            return;
        }

        List<LivingEntity> targets = collectTargets(mc.level, mc.player);
        if (targets.isEmpty()) {
            return;
        }

        float partialTick = event.getPartialTick();
        switch (type.getValue()) {
            case BOX -> renderBoxes(event.getPoseStack(), targets, partialTick);
            case BONE -> renderBones(event.getPoseStack(), targets);
        }
    }

    @Override
    public void onDisable() {
        clearModelBoneFrame();
    }

    public void clearModelBoneFrame() {
        modelBoneEntities.clear();
        modelBoneLines.clear();
    }

    public boolean shouldCaptureModelBones() {
        return isEnabled()
                && type.is(RenderType.BONE)
                && boneAlpha.getValue() > 0
                && mc.player != null
                && mc.level != null;
    }

    public void rememberModelBoneEntity(LivingEntity entity, LivingEntityRenderState state) {
        if (!shouldCaptureModelBones()) {
            return;
        }

        int range = renderRange.getValue();
        double maxDistanceSqr = (double)range * range;
        if (isValidTarget(mc.player, entity, maxDistanceSqr)) {
            modelBoneEntities.put(state, entity);
        }
    }

    public void applyGlowOutline(LivingEntity entity, LivingEntityRenderState state) {
        if (state == null
                || !isEnabled()
                || !type.is(RenderType.GLOW)
                || glowAlpha.getValue() <= 0
                || mc.player == null
                || mc.level == null) {
            return;
        }

        int range = renderRange.getValue();
        double maxDistanceSqr = (double)range * range;
        if (isValidTarget(mc.player, entity, maxDistanceSqr)) {
            state.outlineColor = Render3DUtility.withAlpha(colorFor(entity), glowAlpha.getValue());
        }
    }

    public <S extends LivingEntityRenderState> void captureModelBones(
            S state,
            EntityModel<? super S> model,
            PoseStack entityPoseStack,
            CameraRenderState cameraState
    ) {
        if (!shouldCaptureModelBones() || model == null || cameraState == null || cameraState.pos == null) {
            return;
        }

        LivingEntity entity = modelBoneEntities.get(state);
        if (entity == null) {
            return;
        }

        int range = renderRange.getValue();
        double maxDistanceSqr = (double)range * range;
        if (!isValidTarget(mc.player, entity, maxDistanceSqr)) {
            return;
        }

        model.setupAnim(state);
        List<Render3DUtility.LineSegment> lines = buildModelBoneLines(model.root(), entityPoseStack, cameraState.pos);
        if (!lines.isEmpty()) {
            modelBoneLines.put(entity.getId(), lines);
        }
    }

    private List<LivingEntity> collectTargets(ClientLevel level, LocalPlayer player) {
        int range = renderRange.getValue();
        double maxDistanceSqr = (double)range * range;
        AABB searchBox = player.getBoundingBox().inflate(range);
        List<Entity> entities = level.getEntities(
                player,
                searchBox,
                entity -> entity instanceof LivingEntity livingEntity && isValidTarget(player, livingEntity, maxDistanceSqr)
        );

        List<LivingEntity> targets = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            targets.add((LivingEntity)entity);
        }
        return targets;
    }

    private boolean isValidTarget(LocalPlayer player, LivingEntity entity, double maxDistanceSqr) {
        return entity != null
                && entity != player
                && !entity.isRemoved()
                && !entity.isSpectator()
                && entity.isPickable()
                && Target.isTarget(entity, true)
                && distanceSqr(player, entity) <= maxDistanceSqr;
    }

    private void renderBoxes(PoseStack poseStack, List<LivingEntity> targets, float partialTick) {
        boolean renderFill = fill.getValue() && fillAlpha.getValue() > 0;
        boolean renderOutline = outline.getValue() && outlineAlpha.getValue() > 0;
        if (!renderFill && !renderOutline) {
            return;
        }

        double inflate = boxInflate.getValue();
        for (LivingEntity target : targets) {
            int color = colorFor(target);
            AABB box = interpolatedBox(target, partialTick).inflate(inflate);
            if (renderFill) {
                Render3DUtility.renderFilledBoxNoDepth(poseStack, box, Render3DUtility.withAlpha(color, fillAlpha.getValue()));
            }
            if (renderOutline) {
                Render3DUtility.renderOutlineBoxNoDepth(poseStack, box, Render3DUtility.withAlpha(color, outlineAlpha.getValue()));
            }
        }
    }

    private void renderBones(PoseStack poseStack, List<LivingEntity> targets) {
        int alpha = boneAlpha.getValue();
        if (alpha <= 0) {
            return;
        }

        Map<Integer, List<Render3DUtility.LineSegment>> linesByColor = new HashMap<>();
        for (LivingEntity target : targets) {
            List<Render3DUtility.LineSegment> boneLines = modelBoneLines.get(target.getId());
            if (boneLines == null || boneLines.isEmpty()) {
                continue;
            }

            int color = Render3DUtility.withAlpha(colorFor(target), alpha);
            linesByColor.computeIfAbsent(color, ignored -> new ArrayList<>()).addAll(boneLines);
        }

        for (Map.Entry<Integer, List<Render3DUtility.LineSegment>> entry : linesByColor.entrySet()) {
            Render3DUtility.renderLineSegmentsNoDepth(poseStack, entry.getValue(), entry.getKey());
        }
    }

    private int colorFor(LivingEntity entity) {
        Color color;
        if (isFriend(entity)) {
            color = friendColor.getValue();
        } else if (entity.hurtTime > 0) {
            color = hurtColor.getValue();
        } else {
            color = targetColor.getValue();
        }
        return Render3DUtility.rgb(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static boolean isFriend(LivingEntity entity) {
        return Friend.INSTANCE.isEnabled() && FriendManager.isFriend(entity);
    }

    private static double distanceSqr(LocalPlayer player, LivingEntity entity) {
        double x = entity.getX() - player.getX();
        double y = entity.getY() - player.getY();
        double z = entity.getZ() - player.getZ();
        return x * x + y * y + z * z;
    }

    private static AABB interpolatedBox(LivingEntity entity, float partialTick) {
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTick;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTick;
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTick;
        return entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
    }

    private static List<Render3DUtility.LineSegment> buildModelBoneLines(ModelPart root, PoseStack entityPoseStack, Vec3 cameraPos) {
        List<Render3DUtility.LineSegment> lines = new ArrayList<>();
        PoseStack modelPoseStack = new PoseStack();
        modelPoseStack.last().set(entityPoseStack.last());
        collectModelBoneLines(root, modelPoseStack, cameraPos, null, lines);
        return lines;
    }

    private static void collectModelBoneLines(
            ModelPart part,
            PoseStack poseStack,
            Vec3 cameraPos,
            Vec3 parentPivot,
            List<Render3DUtility.LineSegment> lines
    ) {
        if (!part.visible) {
            return;
        }

        poseStack.pushPose();
        part.translateAndRotate(poseStack);

        Matrix4f pose = poseStack.last().pose();
        Vec3 pivot = transformPosition(pose, 0.0F, 0.0F, 0.0F, cameraPos);
        addLineIfLongEnough(lines, parentPivot, pivot);

        if (!part.skipDraw) {
            addLineIfLongEnough(lines, pivot, modelPartEndpoint(part, pose, cameraPos));
        }

        for (ModelPart child : ((ModelPartAccessor)(Object)part).nyx$getChildren().values()) {
            collectModelBoneLines(child, poseStack, cameraPos, pivot, lines);
        }

        poseStack.popPose();
    }

    private static Vec3 modelPartEndpoint(ModelPart part, Matrix4f pose, Vec3 cameraPos) {
        List<ModelPart.Cube> cubes = ((ModelPartAccessor)(Object)part).nyx$getCubes();
        if (cubes.isEmpty()) {
            return null;
        }

        Vector3f endpoint = null;
        float bestLengthSqr = 0.0F;
        for (ModelPart.Cube cube : cubes) {
            Vector3f cubeEndpoint = cubeEndpoint(cube);
            float lengthSqr = cubeEndpoint.lengthSquared();
            if (lengthSqr > bestLengthSqr) {
                bestLengthSqr = lengthSqr;
                endpoint = cubeEndpoint;
            }
        }

        if (endpoint == null || bestLengthSqr <= MIN_BONE_LENGTH_SQR) {
            return null;
        }
        return transformPosition(pose, endpoint.x(), endpoint.y(), endpoint.z(), cameraPos);
    }

    private static Vector3f cubeEndpoint(ModelPart.Cube cube) {
        float minX = cube.minX / 16.0F;
        float minY = cube.minY / 16.0F;
        float minZ = cube.minZ / 16.0F;
        float maxX = cube.maxX / 16.0F;
        float maxY = cube.maxY / 16.0F;
        float maxZ = cube.maxZ / 16.0F;

        float centerX = (minX + maxX) * 0.5F;
        float centerY = (minY + maxY) * 0.5F;
        float centerZ = (minZ + maxZ) * 0.5F;
        float centerLengthSqr = centerX * centerX + centerY * centerY + centerZ * centerZ;
        if (centerLengthSqr <= MIN_BONE_LENGTH_SQR) {
            return new Vector3f(centerX, centerY, centerZ);
        }

        float centerLength = (float)Math.sqrt(centerLengthSqr);
        float dirX = centerX / centerLength;
        float dirY = centerY / centerLength;
        float dirZ = centerZ / centerLength;
        float distance = Float.POSITIVE_INFINITY;
        distance = limitRayDistance(distance, dirX, minX, maxX);
        distance = limitRayDistance(distance, dirY, minY, maxY);
        distance = limitRayDistance(distance, dirZ, minZ, maxZ);

        if (!Float.isFinite(distance) || distance <= 0.0F) {
            return new Vector3f(centerX, centerY, centerZ);
        }
        return new Vector3f(dirX * distance, dirY * distance, dirZ * distance);
    }

    private static float limitRayDistance(float current, float direction, float min, float max) {
        if (Math.abs(direction) <= 1.0E-5F) {
            return current;
        }

        float limit = (direction > 0.0F ? max : min) / direction;
        if (limit > 0.0F && limit < current) {
            return limit;
        }
        return current;
    }

    private static Vec3 transformPosition(Matrix4f pose, float x, float y, float z, Vec3 cameraPos) {
        Vector3f transformed = pose.transformPosition(x, y, z, new Vector3f());
        return new Vec3(transformed.x() + cameraPos.x, transformed.y() + cameraPos.y, transformed.z() + cameraPos.z);
    }

    private static void addLineIfLongEnough(List<Render3DUtility.LineSegment> lines, Vec3 from, Vec3 to) {
        if (from == null || to == null || from.distanceToSqr(to) <= MIN_BONE_LENGTH_SQR) {
            return;
        }
        addLine(lines, from, to);
    }

    private static void addLine(List<Render3DUtility.LineSegment> lines, Vec3 from, Vec3 to) {
        addLine(lines, from.x, from.y, from.z, to.x, to.y, to.z);
    }

    private static void addLine(
            List<Render3DUtility.LineSegment> lines,
            double fromX,
            double fromY,
            double fromZ,
            double toX,
            double toY,
            double toZ
    ) {
        lines.add(new Render3DUtility.LineSegment(fromX, fromY, fromZ, toX, toY, toZ));
    }

    public enum RenderType {
        BOX,// 一个方形
        BONE, // 骨架
        GLOW, // 着色器描边发光
    }
}
