package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

@ModuleInfo(name = "nyxclient.module.motioncamera.name", description = "nyxclient.module.motioncamera.description", category = Category.VISUAL)
public class MotionCamera extends Module {
    public static final MotionCamera INSTANCE = new MotionCamera();

    public final BoolValue noFirstPerson = ValueBuild.boolValue("no first person", true, this);
    public final DoubleValue firstPersonSpeed = ValueBuild.doubleValue("first person speed", 0.6, 0.0, 1.0, 0.01, this);
    public final DoubleValue speed = ValueBuild.doubleValue("speed", 0.3, 0.0, 1.0, 0.01, this);

    private double fakeX;
    private double fakeY;
    private double fakeZ;
    private double prevFakeX;
    private double prevFakeY;
    private double prevFakeZ;
    private boolean initialized;

    public boolean shouldApply() {
        return initialized && isEnabled() && (!noFirstPerson.getValue() || !isFirstPerson());
    }

    @Override
    public void onEnable() {
        resetPosition();
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            initialized = false;
            return;
        }

        if (!initialized) {
            resetPosition();
            return;
        }

        prevFakeX = fakeX;
        prevFakeY = fakeY;
        prevFakeZ = fakeZ;

        double currentSpeed = isFirstPerson() ? firstPersonSpeed.getValue() : speed.getValue();
        fakeX = animate(fakeX, mc.player.getX(), currentSpeed);
        fakeY = animate(fakeY, eyeY(), currentSpeed);
        fakeZ = animate(fakeZ, mc.player.getZ(), currentSpeed);
    }

    public double getFakeX() {
        return interpolate(prevFakeX, fakeX);
    }

    public double getFakeY() {
        return interpolate(prevFakeY, fakeY);
    }

    public double getFakeZ() {
        return interpolate(prevFakeZ, fakeZ);
    }

    private void resetPosition() {
        if (mc.player == null || mc.level == null) {
            fakeX = fakeY = fakeZ = 0.0;
            prevFakeX = prevFakeY = prevFakeZ = 0.0;
            initialized = false;
            return;
        }

        fakeX = mc.player.getX();
        fakeY = eyeY();
        fakeZ = mc.player.getZ();
        prevFakeX = fakeX;
        prevFakeY = fakeY;
        prevFakeZ = fakeZ;
        initialized = true;
    }

    private boolean isFirstPerson() {
        return mc.options != null && mc.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    private double eyeY() {
        return mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
    }

    private double interpolate(double previous, double current) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        return Mth.lerp(partialTick, previous, current);
    }

    private static double animate(double current, double target, double speed) {
        return Mth.lerp(Mth.clamp(speed, 0.0, 1.0), current, target);
    }
}
