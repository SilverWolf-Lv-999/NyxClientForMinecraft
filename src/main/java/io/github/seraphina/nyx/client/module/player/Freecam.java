package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.freecam.name", description = "nyxclient.module.freecam.description", category = Category.PLAYER)
public final class Freecam extends Module {
    public static final Freecam INSTANCE = new Freecam();

    public final DoubleValue horizontalSpeed = ValueBuild.doubleSetting("horizontal speed", 1.0D, 0.0D, 3.0D, 0.1D, this);
    public final DoubleValue verticalSpeed = ValueBuild.doubleSetting("vertical speed", 0.42D, 0.0D, 3.0D, 0.1D, this);
    public final BoolValue rotate = ValueBuild.boolSetting("rotate", true, this);

    private float fakeYaw;
    private float fakePitch;
    private float previousFakeYaw;
    private float previousFakePitch;
    private double fakeX;
    private double fakeY;
    private double fakeZ;
    private double previousFakeX;
    private double previousFakeY;
    private double previousFakeZ;
    private boolean initialized;

    private Freecam() {
    }

    @Override
    public void onEnable() {
        if (isNull()) {
            setEnabled(false);
            return;
        }

        fakeYaw = mc.player.getYRot();
        fakePitch = mc.player.getXRot();
        previousFakeYaw = fakeYaw;
        previousFakePitch = fakePitch;

        fakeX = mc.player.getX();
        fakeY = eyeY();
        fakeZ = mc.player.getZ();
        previousFakeX = fakeX;
        previousFakeY = fakeY;
        previousFakeZ = fakeZ;
        initialized = true;
    }

    @Override
    public void onDisable() {
        initialized = false;
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (isNull()) {
            setEnabled(false);
            return;
        }

        previousFakeYaw = fakeYaw;
        previousFakePitch = fakePitch;
        previousFakeX = fakeX;
        previousFakeY = fakeY;
        previousFakeZ = fakeZ;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!shouldApply()) {
            return;
        }

        Vec3 horizontalVelocity = MovingUtility.horizontalVelocity(horizontalSpeed.getValue(), fakeYaw);
        fakeX += horizontalVelocity.x;
        fakeZ += horizontalVelocity.z;

        if (event.isJump() != event.isSneak()) {
            fakeY += event.isJump() ? verticalSpeed.getValue() : -verticalSpeed.getValue();
        }

        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
        event.setSneak(false);
        event.setSprint(false);
    }

    public boolean handleMouseTurn(double yawDelta, double pitchDelta) {
        if (!shouldApply()) {
            return false;
        }

        if (rotate.getValue()) {
            fakeYaw += (float) yawDelta * 0.15F;
            fakePitch = Mth.clamp(fakePitch + (float) pitchDelta * 0.15F, -90.0F, 90.0F);
        }
        return true;
    }

    public boolean shouldApply() {
        return isEnabled() && initialized;
    }

    public float getFakeYaw() {
        return Mth.lerp(partialTick(), previousFakeYaw, fakeYaw);
    }

    public float getFakePitch() {
        return Mth.lerp(partialTick(), previousFakePitch, fakePitch);
    }

    public double getFakeX() {
        return Mth.lerp(partialTick(), previousFakeX, fakeX);
    }

    public double getFakeY() {
        return Mth.lerp(partialTick(), previousFakeY, fakeY);
    }

    public double getFakeZ() {
        return Mth.lerp(partialTick(), previousFakeZ, fakeZ);
    }

    private double eyeY() {
        return mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
    }

    private float partialTick() {
        return mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }
}
