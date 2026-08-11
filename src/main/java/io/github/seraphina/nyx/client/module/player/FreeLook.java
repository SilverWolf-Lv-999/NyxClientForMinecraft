package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.util.Mth;

@ModuleInfo(name = "nyxclient.module.freelook.name", description = "nyxclient.module.freelook.description", category = Category.PLAYER)
public final class FreeLook extends Module {
    public static final FreeLook INSTANCE = new FreeLook();

    private float fakeYaw;
    private float fakePitch;
    private float previousFakeYaw;
    private float previousFakePitch;
    private boolean initialized;

    private FreeLook() {
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
    }

    public boolean handleMouseTurn(double yawDelta, double pitchDelta) {
        if (!shouldApply()) {
            return false;
        }

        fakeYaw += (float) yawDelta * 0.15F;
        fakePitch = Mth.clamp(fakePitch + (float) pitchDelta * 0.15F, -90.0F, 90.0F);
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

    private float partialTick() {
        return mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }
}
