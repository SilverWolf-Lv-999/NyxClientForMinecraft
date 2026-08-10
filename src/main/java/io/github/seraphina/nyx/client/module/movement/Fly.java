package io.github.seraphina.nyx.client.module.movement;

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
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.fly.name", description = "nyxclient.module.fly.description", category = Category.MOVEMENT)
public class Fly extends Module {
    public static final Fly INSTANCE = new Fly();

    private static final long ANTI_KICK_DOWN_INTERVAL_MS = 3_900L;
    private static final long ANTI_KICK_UP_INTERVAL_MS = 4_000L;
    private static final double ANTI_KICK_DOWN_SPEED = -0.04D;
    private static final double ANTI_KICK_UP_SPEED = 0.04D;

    public final DoubleValue speed = ValueBuild.doubleSetting("speed", 2.5D, 0.1D, 10.0D, 0.1D, this);
    public final DoubleValue verticalSpeed = ValueBuild.doubleSetting("vertical speed", 1.0D, 0.1D, 5.0D, 0.1D, this);
    public final BoolValue antiKick = ValueBuild.boolSetting("anti kick", true, this);
    public final BoolValue up = ValueBuild.boolSetting("up", true, antiKick::getValue, this);
    public final BoolValue allowSneak = ValueBuild.boolSetting("allow sneak", false, this);

    private long lastAntiKickDownTime;
    private long lastAntiKickUpTime;

    @Override
    public void onEnable() {
        resetAntiKickTimers();
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (isNull()) {
            return;
        }

        Vec3 horizontalVelocity = MovingUtility.horizontalVelocity(speed.getValue(), mc.player.getYRot());
        mc.player.setDeltaMovement(
                horizontalVelocity.x,
                verticalVelocity(System.currentTimeMillis()),
                horizontalVelocity.z
        );
    }

    @EventTarget(4)
    public void onMoveInput(MoveInputEvent event) {
        if (!allowSneak.getValue()) {
            event.setSneak(false);
        }
    }

    private double verticalVelocity(long currentTime) {
        if (antiKick.getValue() && !mc.player.onGround()) {
            if (currentTime - lastAntiKickDownTime >= ANTI_KICK_DOWN_INTERVAL_MS) {
                lastAntiKickDownTime = currentTime;
                return ANTI_KICK_DOWN_SPEED;
            }

            if (up.getValue() && currentTime - lastAntiKickUpTime >= ANTI_KICK_UP_INTERVAL_MS) {
                lastAntiKickUpTime = currentTime;
                return ANTI_KICK_UP_SPEED;
            }
        }

        if (mc.options.keyJump.isDown()) {
            return verticalSpeed.getValue();
        }

        if (mc.options.keyShift.isDown()) {
            return -verticalSpeed.getValue();
        }

        return 0.0D;
    }

    private void resetAntiKickTimers() {
        long currentTime = System.currentTimeMillis();
        lastAntiKickDownTime = currentTime;
        lastAntiKickUpTime = currentTime;
    }
}
