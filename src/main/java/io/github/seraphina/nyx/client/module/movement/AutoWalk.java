package io.github.seraphina.nyx.client.module.movement;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.ICustomGoalProcess;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;

@ModuleInfo(name = "nyxclient.module.autowalk.name", description = "nyxclient.module.autowalk.description", category = Category.MOVEMENT)
public final class AutoWalk extends Module {
    public static final AutoWalk INSTANCE = new AutoWalk();

    private static final double SMART_PATH_DISTANCE = 1_000.0D;

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.NORMAL, this::onModeChanged, this);
    public final EnumValue<Direction> direction = ValueBuild.enumSetting(
            "direction",
            Direction.FORWARD,
            () -> mode.is(Mode.NORMAL),
            this
    );

    private GoalXZ smartGoal;

    @Override
    public void onEnable() {
        if (mode.is(Mode.SMART)) {
            startSmartPathing();
        }
    }

    @Override
    public void onDisable() {
        stopSmartPathing();
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!mode.is(Mode.NORMAL)) {
            return;
        }

        switch (direction.getValue()) {
            case FORWARD -> {
                event.setForward(1.0F);
                event.setStrafe(0.0F);
            }
            case BACKWARD -> {
                event.setForward(-1.0F);
                event.setStrafe(0.0F);
            }
            case RIGHT -> {
                event.setForward(0.0F);
                event.setStrafe(-1.0F);
            }
            case LEFT -> {
                event.setForward(0.0F);
                event.setStrafe(1.0F);
            }
        }
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (mode.is(Mode.SMART)) {
            startSmartPathing();
            return;
        }

        if (isNull()
                || !mc.player.onGround()
                || !mc.player.horizontalCollision) {
            return;
        }

        mc.player.jumpFromGround();
    }

    private void onModeChanged(Mode newMode) {
        if (!isEnabled()) {
            return;
        }

        stopSmartPathing();
        if (newMode == Mode.SMART) {
            startSmartPathing();
        }
    }

    private void startSmartPathing() {
        if (isNull() || smartGoal != null) {
            return;
        }

        smartGoal = GoalXZ.fromDirection(mc.player.position(), mc.player.getYRot(), SMART_PATH_DISTANCE);
        getCustomGoalProcess().setGoalAndPath(smartGoal);
    }

    private void stopSmartPathing() {
        GoalXZ currentSmartGoal = smartGoal;
        smartGoal = null;

        if (currentSmartGoal == null) {
            return;
        }

        ICustomGoalProcess customGoalProcess = getCustomGoalProcess();
        if (customGoalProcess.getGoal() == currentSmartGoal) {
            customGoalProcess.onLostControl();
        }
    }

    private ICustomGoalProcess getCustomGoalProcess() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
    }

    public enum Mode {
        NORMAL,
        SMART
    }

    public enum Direction {
        FORWARD,
        BACKWARD,
        RIGHT,
        LEFT
    }
}
