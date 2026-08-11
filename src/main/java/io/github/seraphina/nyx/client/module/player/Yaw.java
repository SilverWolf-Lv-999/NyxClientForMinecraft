package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;

@ModuleInfo(name = "nyxclient.module.yaw.name", description = "nyxclient.module.yaw.description", category = Category.PLAYER)
public final class Yaw extends Module {
    public static final Yaw INSTANCE = new Yaw();

    public final BoolValue yawLock = ValueBuild.boolSetting("yaw lock", true, this);
    public final BoolValue smart = ValueBuild.boolSetting("smart", true, this);
    public final DoubleValue yaw = ValueBuild.doubleSetting("yaw", 0.0D, -180.0D, 180.0D, 0.1D, () -> !smart.getValue(), this);
    public final BoolValue pitchLock = ValueBuild.boolSetting("pitch lock", true, this);
    public final DoubleValue pitch = ValueBuild.doubleSetting("pitch", 0.0D, -90.0D, 90.0D, 0.1D, this);
    public final BoolValue lock = ValueBuild.boolSetting("lock", true, this);

    private Yaw() {
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (isNull()) {
            return;
        }

        if (yawLock.getValue()) {
            float lockedYaw = smart.getValue()
                    ? Math.round((mc.player.getYRot() + 1.0F) / 45.0F) * 45.0F
                    : yaw.getValue().floatValue();
            mc.player.setYRot(lockedYaw);
        }

        if (pitchLock.getValue()) {
            mc.player.setXRot(pitch.getValue().floatValue());
        }
    }

    public boolean shouldBlockMouseInput() {
        return isEnabled() && lock.getValue();
    }
}
