package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import net.minecraft.client.CameraType;

@ModuleInfo(name = "nyxclient.module.viewclip.name", description = "nyxclient.module.viewclip.description", category = Category.VISUAL)
public class ViewClip extends Module {
    public static final ViewClip INSTANCE = new ViewClip();
    public static final float FULL_ANIMATION_VALUE = 100.0F;
    public static final float MIN_ANIMATION_VALUE = 50.0F;

    private static final float MAX_FRAME_SECONDS = 0.1F;

    public final DoubleValue scale = ValueBuild.doubleValue("scale", 1.0, 0.5, 2.0, 0.01, this);
    public final BoolValue animation = ValueBuild.boolSetting("animation", true, this);
    public final DoubleValue animationSpeed = ValueBuild.doubleValue("animation speed", 0.3, 0.01, 0.5, 0.01, () -> animation.getValue(), this);

    private float personViewAnimation = FULL_ANIMATION_VALUE;
    private CameraType lastCameraType;
    private long lastAnimationFrameNanos;

    @Override
    public void onEnable() {
        resetAnimationClock();
    }

    @Override
    public void onDisable() {
        personViewAnimation = FULL_ANIMATION_VALUE;
        lastCameraType = null;
        resetAnimationClock();
    }

    @EventTarget
    public void onRender2D(Render2DEvent.HUD event) {
        updateCameraType();

        if (!animation.getValue()) {
            personViewAnimation = FULL_ANIMATION_VALUE;
            resetAnimationClock();
            return;
        }

        long now = System.nanoTime();
        if (lastAnimationFrameNanos == 0L) {
            lastAnimationFrameNanos = now;
            return;
        }

        float frameSeconds = Math.min((now - lastAnimationFrameNanos) / 1_000_000_000.0F, MAX_FRAME_SECONDS);
        lastAnimationFrameNanos = now;
        personViewAnimation = animate(personViewAnimation, FULL_ANIMATION_VALUE, animationSpeed.getValue().floatValue(), frameSeconds);
    }

    public float getCameraDistance(float startingDistance) {
        updateCameraType();

        float animationValue = animation.getValue()
                ? Math.max(personViewAnimation, MIN_ANIMATION_VALUE)
                : FULL_ANIMATION_VALUE;

        return startingDistance * scale.getValue().floatValue() * animationValue / FULL_ANIMATION_VALUE;
    }

    private void updateCameraType() {
        if (mc.options == null) {
            return;
        }

        CameraType cameraType = mc.options.getCameraType();
        if (lastCameraType == cameraType) {
            return;
        }

        lastCameraType = cameraType;
        if (cameraType == CameraType.FIRST_PERSON || cameraType == CameraType.THIRD_PERSON_BACK) {
            personViewAnimation = MIN_ANIMATION_VALUE;
            resetAnimationClock();
        }
    }

    private void resetAnimationClock() {
        lastAnimationFrameNanos = 0L;
    }

    private static float animate(float current, float target, float speed, float frameSeconds) {
        float step = Math.max(10.0F, Math.abs(current - target) * 40.0F) * speed * frameSeconds;
        if (current < target) {
            return Math.min(current + step, target);
        }

        if (current > target) {
            return Math.max(current - step, target);
        }

        return target;
    }
}
