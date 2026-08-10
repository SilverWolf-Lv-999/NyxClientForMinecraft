package io.github.seraphina.nyx.client.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.awt.Color;

@ModuleInfo(name = "nyxclient.module.blockhighlight.name", description = "nyxclient.module.blockhighlight.description", category = Category.VISUAL)
public class BlockHighlight extends Module {
    public static final BlockHighlight INSTANCE = new BlockHighlight();
    private static final double SMOOTHING_SPEED = 16.0D;
    private static final double MAX_FRAME_TIME_SECONDS = 0.1D;
    private static final double SNAP_DISTANCE = 1.0E-4D;

    public final BoolValue smooth = ValueBuild.boolSetting(
            "smooth",
            true,
            this
    );

    public final BoolValue clip = ValueBuild.boolSetting(
            "clip",
            true,
            this
    );

    public final EnumValue<Mode> mode = ValueBuild.enumSetting(
            "mode",
            Mode.ALL,
            this
    );

    public final ColorValue colorValue = ValueBuild.colorValue(
            "color",
            Color.DARK_GRAY,
            this
    );

    private AABB renderedBox;
    private long lastRenderNanos;

    @Override
    public void onDisable() {
        resetAnimation();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        BlockPos targetPos = targetBlockPos();
        if (targetPos == null) {
            resetAnimation();
            return;
        }

        AABB targetBox = new AABB(targetPos);
        if (!smooth.getValue() || renderedBox == null) {
            renderedBox = targetBox;
        } else {
            renderedBox = interpolate(renderedBox, targetBox, animationProgress());
        }

        lastRenderNanos = System.nanoTime();
        renderBox(event.getPoseStack(), renderedBox);
    }

    private BlockPos targetBlockPos() {
        if (isNull() || !(mc.hitResult instanceof BlockHitResult hitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos blockPos = hitResult.getBlockPos();
        return mc.level.getBlockState(blockPos).isAir() ? null : blockPos;
    }

    private double animationProgress() {
        long now = System.nanoTime();
        if (lastRenderNanos == 0L) {
            return 1.0D;
        }

        double frameTime = Math.min((now - lastRenderNanos) / 1_000_000_000.0D, MAX_FRAME_TIME_SECONDS);
        return 1.0D - Math.exp(-SMOOTHING_SPEED * Math.max(0.0D, frameTime));
    }

    private void renderBox(PoseStack poseStack, AABB box) {
        Color color = colorValue.getValue();
        switch (mode.getValue()) {
            case ALL -> {
                if (clip.getValue()) {
                    Render3DUtility.renderFilledBoxNoDepth(poseStack, box, color);
                    Render3DUtility.renderOutlineBoxNoDepth(poseStack, box, color);
                } else {
                    Render3DUtility.renderBox(poseStack, box, color, color);
                }
            }
            case BOX -> {
                if (clip.getValue()) {
                    Render3DUtility.renderFilledBoxNoDepth(poseStack, box, color);
                } else {
                    Render3DUtility.renderFilledBox(poseStack, box, color);
                }
            }
            case OUTLINE -> {
                if (clip.getValue()) {
                    Render3DUtility.renderOutlineBoxNoDepth(poseStack, box, color);
                } else {
                    Render3DUtility.renderOutlineBox(poseStack, box, color);
                }
            }
        }
    }

    private static AABB interpolate(AABB from, AABB to, double progress) {
        AABB box = new AABB(
                MathUtility.lerp(from.minX, to.minX, progress),
                MathUtility.lerp(from.minY, to.minY, progress),
                MathUtility.lerp(from.minZ, to.minZ, progress),
                MathUtility.lerp(from.maxX, to.maxX, progress),
                MathUtility.lerp(from.maxY, to.maxY, progress),
                MathUtility.lerp(from.maxZ, to.maxZ, progress)
        );
        return isClose(box, to) ? to : box;
    }

    private static boolean isClose(AABB first, AABB second) {
        return Math.abs(first.minX - second.minX) < SNAP_DISTANCE
                && Math.abs(first.minY - second.minY) < SNAP_DISTANCE
                && Math.abs(first.minZ - second.minZ) < SNAP_DISTANCE
                && Math.abs(first.maxX - second.maxX) < SNAP_DISTANCE
                && Math.abs(first.maxY - second.maxY) < SNAP_DISTANCE
                && Math.abs(first.maxZ - second.maxZ) < SNAP_DISTANCE;
    }

    private void resetAnimation() {
        renderedBox = null;
        lastRenderNanos = 0L;
    }

    public enum Mode {
        ALL,
        BOX,
        OUTLINE
    }
}
