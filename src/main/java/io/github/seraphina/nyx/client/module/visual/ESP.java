package io.github.seraphina.nyx.client.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.manager.FriendManager;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ModuleInfo(name = "nyxclient.module.esp.name", description = "nyxclient.module.esp.description", category = Category.VISUAL)
public class ESP extends Module {
    public static final ESP INSTANCE = new ESP();

    public final EnumValue<RenderType> type = ValueBuild.enumSetting("type", RenderType.BONE, this);
    public final IntValue renderRange = ValueBuild.intSetting("render range", 128, 8, 512, 8, this);

    public final BoolValue fill = ValueBuild.boolSetting("fill", true, () -> type.is(RenderType.BOX), this);
    public final BoolValue outline = ValueBuild.boolSetting("outline", true, () -> type.is(RenderType.BOX), this);
    public final IntValue fillAlpha = ValueBuild.intSetting("fill alpha", 35, 0, 255, 5, () -> type.is(RenderType.BOX) && fill.getValue(), this);
    public final IntValue outlineAlpha = ValueBuild.intSetting("outline alpha", 220, 0, 255, 5, () -> type.is(RenderType.BOX) && outline.getValue(), this);
    public final IntValue boneAlpha = ValueBuild.intSetting("bone alpha", 230, 0, 255, 5, () -> type.is(RenderType.BONE), this);
    public final IntValue glowAlpha = ValueBuild.intSetting("glow alpha", 230, 0, 255, 5, () -> type.is(RenderType.GLOW), this);
    public final DoubleValue boxInflate = ValueBuild.doubleSetting("box inflate", 0.04D, 0.0D, 0.2D, 0.005D, () -> type.is(RenderType.BOX) || type.is(RenderType.GLOW), this);
    public final DoubleValue glowWidth = ValueBuild.doubleSetting("glow width", 4.0D, 1.0D, 12.0D, 0.5D, () -> type.is(RenderType.GLOW), this);

    public final ColorValue targetColor = ValueBuild.colorSetting("target color", new Color(84, 170, 255), false, this);
    public final ColorValue friendColor = ValueBuild.colorSetting("friend color", new Color(80, 255, 112), false, this);
    public final ColorValue hurtColor = ValueBuild.colorSetting("hurt color", new Color(255, 64, 64), false, this);

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        List<LivingEntity> targets = collectTargets(mc.level, mc.player);
        if (targets.isEmpty()) {
            return;
        }

        switch (type.getValue()) {
            case BOX -> renderBoxes(event.getPoseStack(), targets);
            case BONE -> renderBones(event.getPoseStack(), targets);
            case GLOW -> renderGlow(event, targets);
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

    private void renderBoxes(PoseStack poseStack, List<LivingEntity> targets) {
        boolean renderFill = fill.getValue() && fillAlpha.getValue() > 0;
        boolean renderOutline = outline.getValue() && outlineAlpha.getValue() > 0;
        if (!renderFill && !renderOutline) {
            return;
        }

        double inflate = boxInflate.getValue();
        for (LivingEntity target : targets) {
            int color = colorFor(target);
            AABB box = target.getBoundingBox().inflate(inflate);
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
            int color = Render3DUtility.withAlpha(colorFor(target), alpha);
            linesByColor.computeIfAbsent(color, ignored -> new ArrayList<>()).addAll(buildBoneLines(target));
        }

        for (Map.Entry<Integer, List<Render3DUtility.LineSegment>> entry : linesByColor.entrySet()) {
            Render3DUtility.renderLineSegmentsNoDepth(poseStack, entry.getValue(), entry.getKey());
        }
    }

    private void renderGlow(Render3DEvent event, List<LivingEntity> targets) {
        int alpha = glowAlpha.getValue();
        if (alpha <= 0) {
            return;
        }

        double inflate = boxInflate.getValue();
        Map<Integer, List<Render3DUtility.LineSegment>> linesByColor = new HashMap<>();
        for (LivingEntity target : targets) {
            int color = Render3DUtility.withAlpha(colorFor(target), alpha);
            linesByColor.computeIfAbsent(color, ignored -> new ArrayList<>()).addAll(buildBoxLines(target.getBoundingBox().inflate(inflate)));
        }

        float width = glowWidth.getValue().floatValue();
        for (Map.Entry<Integer, List<Render3DUtility.LineSegment>> entry : linesByColor.entrySet()) {
            Render3DUtility.renderGlowLineSegmentsNoDepth(event.getPoseStack(), event.getProjectionMatrix(), entry.getValue(), entry.getKey(), width);
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

    private static List<Render3DUtility.LineSegment> buildBoxLines(AABB box) {
        List<Render3DUtility.LineSegment> lines = new ArrayList<>(12);
        addLine(lines, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ);
        addLine(lines, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
        addLine(lines, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
        addLine(lines, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ);
        addLine(lines, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ);
        addLine(lines, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
        addLine(lines, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);
        addLine(lines, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ);
        addLine(lines, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ);
        addLine(lines, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
        addLine(lines, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ);
        addLine(lines, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ);
        return lines;
    }

    private static List<Render3DUtility.LineSegment> buildBoneLines(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double height = Math.max(0.4D, box.maxY - box.minY);
        double width = Math.max(0.2D, Math.max(box.maxX - box.minX, box.maxZ - box.minZ));

        double footY = box.minY + height * 0.02D;
        double hipY = box.minY + height * 0.38D;
        double chestY = box.minY + height * 0.62D;
        double shoulderY = box.minY + height * 0.78D;
        double neckY = box.minY + height * 0.86D;
        double headY = box.maxY;

        double yaw = Math.toRadians(entity.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 side = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));

        double shoulderHalf = width * 0.45D;
        double hipHalf = width * 0.25D;
        Vec3 hip = new Vec3(centerX, hipY, centerZ);
        Vec3 chest = new Vec3(centerX, chestY, centerZ);
        Vec3 neck = new Vec3(centerX, neckY, centerZ);
        Vec3 head = new Vec3(centerX, headY, centerZ);
        Vec3 leftShoulder = offset(centerX, shoulderY, centerZ, side, shoulderHalf);
        Vec3 rightShoulder = offset(centerX, shoulderY, centerZ, side, -shoulderHalf);
        Vec3 leftHand = leftShoulder.add(side.scale(shoulderHalf * 0.35D)).add(0.0D, -height * 0.28D, 0.0D);
        Vec3 rightHand = rightShoulder.add(side.scale(-shoulderHalf * 0.35D)).add(0.0D, -height * 0.28D, 0.0D);
        Vec3 leftHip = offset(centerX, hipY, centerZ, side, hipHalf);
        Vec3 rightHip = offset(centerX, hipY, centerZ, side, -hipHalf);
        Vec3 leftKnee = leftHip.add(side.scale(hipHalf * 0.15D)).add(forward.scale(height * 0.04D)).add(0.0D, -height * 0.18D, 0.0D);
        Vec3 rightKnee = rightHip.add(side.scale(-hipHalf * 0.15D)).add(forward.scale(height * 0.04D)).add(0.0D, -height * 0.18D, 0.0D);
        Vec3 leftFoot = new Vec3(leftKnee.x + side.x * hipHalf * 0.15D, footY, leftKnee.z + side.z * hipHalf * 0.15D);
        Vec3 rightFoot = new Vec3(rightKnee.x - side.x * hipHalf * 0.15D, footY, rightKnee.z - side.z * hipHalf * 0.15D);

        List<Render3DUtility.LineSegment> lines = new ArrayList<>(11);
        addLine(lines, head, neck);
        addLine(lines, neck, chest);
        addLine(lines, chest, hip);
        addLine(lines, leftShoulder, rightShoulder);
        addLine(lines, leftShoulder, leftHand);
        addLine(lines, rightShoulder, rightHand);
        addLine(lines, leftHip, rightHip);
        addLine(lines, leftHip, leftKnee);
        addLine(lines, leftKnee, leftFoot);
        addLine(lines, rightHip, rightKnee);
        addLine(lines, rightKnee, rightFoot);
        return lines;
    }

    private static Vec3 offset(double x, double y, double z, Vec3 direction, double amount) {
        return new Vec3(x + direction.x * amount, y, z + direction.z * amount);
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
