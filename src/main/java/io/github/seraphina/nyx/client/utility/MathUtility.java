package io.github.seraphina.nyx.client.utility;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class MathUtility {
    private static final float SNAP_EPSILON = 0.001F;

    private MathUtility() {
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float lerp(float from, float to, float progress) {
        return from + (to - from) * clamp(progress, 0.0F, 1.0F);
    }

    public static float phase(float start, float end, float value) {
        if (end <= start) {
            return value >= end ? 1.0F : 0.0F;
        }
        return clamp((value - start) / (end - start), 0.0F, 1.0F);
    }

    public static float easeInCubic(float value) {
        float safe = clamp(value, 0.0F, 1.0F);
        return safe * safe * safe;
    }

    public static float easeOutCubic(float value) {
        float inverse = 1.0F - clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    public static float easeInOutCubic(float value) {
        float safe = clamp(value, 0.0F, 1.0F);
        return safe < 0.5F ? 4.0F * safe * safe * safe : 1.0F - (float)Math.pow(-2.0F * safe + 2.0F, 3.0F) * 0.5F;
    }

    public static float easeOutBack(float value) {
        float safe = clamp(value, 0.0F, 1.0F);
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float)Math.pow(safe - 1.0F, 3.0F) + c1 * (float)Math.pow(safe - 1.0F, 2.0F);
    }

    public static float animateLinear(float current, float target, float speed, float frameSeconds) {
        if (current == target) {
            return current;
        }

        float step = clamp(Math.max(0.0F, frameSeconds) * speed, 0.0F, 1.0F);
        return snapToTarget(current + (target - current) * step, target);
    }

    public static float animateExp(float current, float target, float speed, float frameSeconds) {
        float progress = 1.0F - (float)Math.exp(-Math.max(0.0F, speed) * Math.max(0.0F, frameSeconds));
        return snapToTarget(lerp(current, target, progress), target);
    }

    public static float stackedItemY(float listY, int index, float itemHeight, float gap, float scroll) {
        return listY + index * (itemHeight + gap) - scroll;
    }

    public static float stackedContentHeight(int itemCount, float itemHeight, float gap) {
        if (itemCount <= 0) {
            return 0.0F;
        }
        return itemCount * (itemHeight + gap) - gap;
    }

    public static boolean isInside(double pointX, double pointY, float x, float y, float width, float height) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }

    public static boolean isInsideExclusive(double pointX, double pointY, float x, float y, float width, float height) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }

    public static ScreenPosition worldToScreen(
            Vec3 position,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            float screenWidth,
            float screenHeight
    ) {
        if (position == null || modelViewMatrix == null || projectionMatrix == null || screenWidth <= 0.0F || screenHeight <= 0.0F) {
            return null;
        }

        Vector4f clip = new Vector4f((float)position.x, (float)position.y, (float)position.z, 1.0F);
        clip.mul(modelViewMatrix);
        clip.mul(projectionMatrix);

        float w = clip.w();
        if (w <= 0.0F) {
            return null;
        }

        float ndcX = clip.x() / w;
        float ndcY = clip.y() / w;
        float ndcZ = clip.z() / w;
        if (ndcZ < -1.0F || ndcZ > 1.0F) {
            return null;
        }

        return new ScreenPosition(
                (ndcX + 1.0F) * 0.5F * screenWidth,
                (1.0F - ndcY) * 0.5F * screenHeight,
                ndcZ
        );
    }

    private static float snapToTarget(float value, float target) {
        return Math.abs(target - value) < SNAP_EPSILON ? target : value;
    }

    public record ScreenPosition(float x, float y, float depth) {
    }
}
