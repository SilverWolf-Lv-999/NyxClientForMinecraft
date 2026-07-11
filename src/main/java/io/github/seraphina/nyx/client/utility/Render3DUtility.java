package io.github.seraphina.nyx.client.utility;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.Objects;

public final class Render3DUtility {
    private static final float DEFAULT_LINE_WIDTH = 1.0F;
    private static final RenderPipeline NO_DEPTH_FILLED_BOX_PIPELINE = RenderPipelines.DEBUG_FILLED_BOX.toBuilder()
        .withLocation(Identifier.fromNamespaceAndPath("nyxclient", "pipeline/no_depth_filled_box"))
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .build();
    private static final RenderPipeline NO_DEPTH_LINES_PIPELINE = RenderPipelines.LINES.toBuilder()
        .withLocation(Identifier.fromNamespaceAndPath("nyxclient", "pipeline/no_depth_lines"))
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .build();
    private static final RenderType NO_DEPTH_FILLED_BOX = RenderType.create(
        "nyx_no_depth_filled_box",
        RenderSetup.builder(NO_DEPTH_FILLED_BOX_PIPELINE)
            .sortOnUpload()
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup()
    );
    private static final RenderType NO_DEPTH_LINES = RenderType.create(
        "nyx_no_depth_lines",
        RenderSetup.builder(NO_DEPTH_LINES_PIPELINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup()
    );

    private Render3DUtility() {
    }

    public static int rgb(int red, int green, int blue) {
        return rgba(red, green, blue, 255);
    }

    public static int rgba(int red, int green, int blue, int alpha) {
        return (clamp255(alpha) << 24)
            | (clamp255(red) << 16)
            | (clamp255(green) << 8)
            | clamp255(blue);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (clamp255(alpha) << 24);
    }

    public static void renderCube(PoseStack poseStack, int x, int y, int z, double length, float r, float g, float b, float a) {
        renderFilledBox(poseStack, x, y, z, x + length, y + length, z + length, color(r, g, b, a));
    }

    public static void renderCube(PoseStack poseStack, int x, int y, int z, double length, Color color) {
        renderCube(poseStack, x, y, z, length, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    public static void renderCube(PoseStack poseStack, BlockPos pos, double length, Color color) {
        Objects.requireNonNull(pos, "pos");
        renderCube(poseStack, pos.getX(), pos.getY(), pos.getZ(), length, color);
    }

    public static void renderCube(PoseStack poseStack, BlockPos pos, double length, int color) {
        Objects.requireNonNull(pos, "pos");
        renderFilledBox(poseStack, pos.getX(), pos.getY(), pos.getZ(), pos.getX() + length, pos.getY() + length, pos.getZ() + length, color);
    }

    public static void renderBlockBox(PoseStack poseStack, BlockPos pos, Color color) {
        renderFilledBox(poseStack, new AABB(pos), color);
    }

    public static void renderBlockBox(PoseStack poseStack, BlockPos pos, int color) {
        renderFilledBox(poseStack, new AABB(pos), color);
    }

    public static void renderBlockOutline(PoseStack poseStack, BlockPos pos, Color color) {
        renderOutlineBox(poseStack, new AABB(pos), color);
    }

    public static void renderBlockOutline(PoseStack poseStack, BlockPos pos, int color) {
        renderOutlineBox(poseStack, new AABB(pos), color);
    }

    public static void renderBox(PoseStack poseStack, AABB box, Color fillColor, Color outlineColor) {
        renderBox(poseStack, box, color(fillColor), color(outlineColor));
    }

    public static void renderBox(PoseStack poseStack, AABB box, int fillColor, int outlineColor) {
        renderFilledBox(poseStack, box, fillColor);
        renderOutlineBox(poseStack, box, outlineColor);
    }

    public static void renderFilledBox(PoseStack poseStack, AABB box, Color color) {
        renderFilledBox(poseStack, box, color(color));
    }

    public static void renderFilledBox(PoseStack poseStack, AABB box, int color) {
        Objects.requireNonNull(box, "box");
        renderFilledBox(poseStack, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
    }

    public static void renderFilledBoxNoDepth(PoseStack poseStack, AABB box, Color color) {
        renderFilledBoxNoDepth(poseStack, box, color(color));
    }

    public static void renderFilledBoxNoDepth(PoseStack poseStack, AABB box, int color) {
        Objects.requireNonNull(box, "box");
        renderFilledBox(poseStack, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, NO_DEPTH_FILLED_BOX);
    }

    public static void renderFilledBox(
        PoseStack poseStack,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        Color color
    ) {
        renderFilledBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ, color(color));
    }

    public static void renderFilledBoxNoDepth(
        PoseStack poseStack,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        Color color
    ) {
        renderFilledBoxNoDepth(poseStack, minX, minY, minZ, maxX, maxY, maxZ, color(color));
    }

    public static void renderFilledBox(
        PoseStack poseStack,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int color
    ) {
        renderFilledBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ, color, RenderTypes.debugFilledBox());
    }

    public static void renderFilledBoxNoDepth(
        PoseStack poseStack,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int color
    ) {
        renderFilledBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ, color, NO_DEPTH_FILLED_BOX);
    }

    private static void renderFilledBox(
        PoseStack poseStack,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int color,
        RenderType renderType
    ) {
        Objects.requireNonNull(poseStack, "poseStack");
        Objects.requireNonNull(renderType, "renderType");
        if (isTransparent(color)) {
            return;
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(renderType.mode(), renderType.format());
        addBoxQuads(buffer, poseStack.last(), minX, minY, minZ, maxX, maxY, maxZ, color);
        draw(renderType, buffer);
    }

    public static void renderOutlineBox(PoseStack poseStack, AABB box, Color color) {
        renderOutlineBox(poseStack, box, color(color));
    }

    public static void renderOutlineBox(PoseStack poseStack, AABB box, int color) {
        Objects.requireNonNull(box, "box");
        renderOutlineBox(poseStack, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
    }

    public static void renderOutlineBoxNoDepth(PoseStack poseStack, AABB box, Color color) {
        renderOutlineBoxNoDepth(poseStack, box, color(color));
    }

    public static void renderOutlineBoxNoDepth(PoseStack poseStack, AABB box, int color) {
        Objects.requireNonNull(box, "box");
        renderOutlineBox(poseStack, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, NO_DEPTH_LINES);
    }

    public static void renderOutlineBox(
        PoseStack poseStack,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        Color color
    ) {
        renderOutlineBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ, color(color));
    }

    public static void renderOutlineBoxNoDepth(
        PoseStack poseStack,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        Color color
    ) {
        renderOutlineBoxNoDepth(poseStack, minX, minY, minZ, maxX, maxY, maxZ, color(color));
    }

    public static void renderOutlineBox(
        PoseStack poseStack,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int color
    ) {
        renderOutlineBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ, color, RenderTypes.lines());
    }

    public static void renderOutlineBoxNoDepth(
        PoseStack poseStack,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int color
    ) {
        renderOutlineBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ, color, NO_DEPTH_LINES);
    }

    private static void renderOutlineBox(
        PoseStack poseStack,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int color,
        RenderType renderType
    ) {
        Objects.requireNonNull(poseStack, "poseStack");
        Objects.requireNonNull(renderType, "renderType");
        if (isTransparent(color)) {
            return;
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(renderType.mode(), renderType.format());
        addBoxLines(buffer, poseStack.last(), minX, minY, minZ, maxX, maxY, maxZ, color);
        draw(renderType, buffer);
    }

    public static void renderLine(PoseStack poseStack, Vec3 from, Vec3 to, Color color) {
        renderLine(poseStack, from, to, color(color));
    }

    public static void renderLine(PoseStack poseStack, Vec3 from, Vec3 to, int color) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        renderLine(poseStack, from.x, from.y, from.z, to.x, to.y, to.z, color);
    }

    public static void renderLine(
        PoseStack poseStack,
        double fromX,
        double fromY,
        double fromZ,
        double toX,
        double toY,
        double toZ,
        Color color
    ) {
        renderLine(poseStack, fromX, fromY, fromZ, toX, toY, toZ, color(color));
    }

    public static void renderLine(
        PoseStack poseStack,
        double fromX,
        double fromY,
        double fromZ,
        double toX,
        double toY,
        double toZ,
        int color
    ) {
        Objects.requireNonNull(poseStack, "poseStack");
        if (isTransparent(color)) {
            return;
        }

        Vector3f normal = normal(fromX, fromY, fromZ, toX, toY, toZ);
        PoseStack.Pose pose = poseStack.last();
        RenderType renderType = RenderTypes.lines();
        BufferBuilder buffer = Tesselator.getInstance().begin(renderType.mode(), renderType.format());
        lineVertex(buffer, pose, fromX, fromY, fromZ, color, normal);
        lineVertex(buffer, pose, toX, toY, toZ, color, normal);
        draw(renderType, buffer);
    }

    public static void renderCross(PoseStack poseStack, Vec3 center, double size, Color color) {
        renderCross(poseStack, center, size, color(color));
    }

    public static void renderCross(PoseStack poseStack, Vec3 center, double size, int color) {
        Objects.requireNonNull(center, "center");
        renderCross(poseStack, center.x, center.y, center.z, size, color);
    }

    public static void renderCross(PoseStack poseStack, double centerX, double centerY, double centerZ, double size, Color color) {
        renderCross(poseStack, centerX, centerY, centerZ, size, color(color));
    }

    public static void renderCross(PoseStack poseStack, double centerX, double centerY, double centerZ, double size, int color) {
        Objects.requireNonNull(poseStack, "poseStack");
        if (size <= 0.0 || isTransparent(color)) {
            return;
        }

        double half = size * 0.5;
        PoseStack.Pose pose = poseStack.last();
        RenderType renderType = RenderTypes.lines();
        BufferBuilder buffer = Tesselator.getInstance().begin(renderType.mode(), renderType.format());
        addLine(buffer, pose, centerX - half, centerY, centerZ, centerX + half, centerY, centerZ, color);
        addLine(buffer, pose, centerX, centerY - half, centerZ, centerX, centerY + half, centerZ, color);
        addLine(buffer, pose, centerX, centerY, centerZ - half, centerX, centerY, centerZ + half, color);
        draw(renderType, buffer);
    }

    private static void addBoxQuads(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int color
    ) {
        quad(consumer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, color);
        quad(consumer, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color);
        quad(consumer, pose, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, color);
        quad(consumer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        quad(consumer, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, color);
        quad(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, color);
    }

    private static void quad(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        double x0,
        double y0,
        double z0,
        double x1,
        double y1,
        double z1,
        double x2,
        double y2,
        double z2,
        double x3,
        double y3,
        double z3,
        int color
    ) {
        vertex(consumer, pose, x0, y0, z0, color);
        vertex(consumer, pose, x1, y1, z1, color);
        vertex(consumer, pose, x2, y2, z2, color);
        vertex(consumer, pose, x3, y3, z3, color);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, double x, double y, double z, int color) {
        consumer.addVertex(pose, (float)x, (float)y, (float)z).setColor(color);
    }

    private static void addBoxLines(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int color
    ) {
        addLine(consumer, pose, minX, minY, minZ, maxX, minY, minZ, color);
        addLine(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, color);
        addLine(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, color);
        addLine(consumer, pose, minX, minY, maxZ, minX, minY, minZ, color);
        addLine(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, color);
        addLine(consumer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        addLine(consumer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        addLine(consumer, pose, minX, maxY, maxZ, minX, maxY, minZ, color);
        addLine(consumer, pose, minX, minY, minZ, minX, maxY, minZ, color);
        addLine(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, color);
        addLine(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        addLine(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, color);
    }

    private static void addLine(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        double fromX,
        double fromY,
        double fromZ,
        double toX,
        double toY,
        double toZ,
        int color
    ) {
        Vector3f normal = normal(fromX, fromY, fromZ, toX, toY, toZ);
        lineVertex(consumer, pose, fromX, fromY, fromZ, color, normal);
        lineVertex(consumer, pose, toX, toY, toZ, color, normal);
    }

    private static void lineVertex(VertexConsumer consumer, PoseStack.Pose pose, double x, double y, double z, int color, Vector3f normal) {
        consumer.addVertex(pose, (float)x, (float)y, (float)z)
            .setColor(color)
            .setNormal(pose, normal)
            .setLineWidth(DEFAULT_LINE_WIDTH);
    }

    private static Vector3f normal(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        float x = (float)(toX - fromX);
        float y = (float)(toY - fromY);
        float z = (float)(toZ - fromZ);
        if (x == 0.0F && y == 0.0F && z == 0.0F) {
            return new Vector3f(0.0F, 1.0F, 0.0F);
        }
        return new Vector3f(x, y, z).normalize();
    }

    private static void draw(RenderType renderType, BufferBuilder buffer) {
        MeshData meshData = buffer.buildOrThrow();
        renderType.draw(meshData);
    }

    private static int color(Color color) {
        Objects.requireNonNull(color, "color");
        return rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private static int color(float red, float green, float blue, float alpha) {
        return rgba(component(red), component(green), component(blue), component(alpha));
    }

    private static int component(float value) {
        if (Float.isNaN(value)) {
            return 0;
        }
        if (value <= 1.0F) {
            return clamp255(Math.round(clamp01(value) * 255.0F));
        }
        return clamp255(Math.round(value));
    }

    private static boolean isTransparent(int color) {
        return ((color >>> 24) & 0xFF) == 0;
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
