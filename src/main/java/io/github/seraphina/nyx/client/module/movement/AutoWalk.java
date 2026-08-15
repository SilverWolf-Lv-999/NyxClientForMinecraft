package io.github.seraphina.nyx.client.module.movement;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IDirectElytraProcess;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;

@ModuleInfo(name = "nyxclient.module.autowalk.name", description = "nyxclient.module.autowalk.description", category = Category.MOVEMENT)
public final class AutoWalk extends Module {
    public static final AutoWalk INSTANCE = new AutoWalk();

    private static final double SMART_PATH_DISTANCE = 1_000.0D;
    private static final int MIN_HORIZONTAL_COORDINATE = -29_999_984;
    private static final int MAX_HORIZONTAL_COORDINATE = 29_999_984;
    private static final int MIN_Y_COORDINATE = -64;
    private static final int MAX_Y_COORDINATE = 319;

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.NORMAL, this::onModeChanged, this);
    public final EnumValue<Direction> direction = ValueBuild.enumSetting(
            "direction",
            Direction.FORWARD,
            () -> mode.is(Mode.NORMAL),
            this
    );
    public final EnumValue<SmartTargetMode> smartTargetMode = ValueBuild.enumSetting(
            "smart target mode",
            SmartTargetMode.AUTOMATIC,
            () -> mode.is(Mode.SMART),
            this::onSmartTargetModeChanged,
            this
    );
    public final IntValue targetX = ValueBuild.intSetting(
            "target x",
            0,
            MIN_HORIZONTAL_COORDINATE,
            MAX_HORIZONTAL_COORDINATE,
            1,
            this::isSpecifiedSmartTarget,
            this
    );
    public final IntValue targetY = ValueBuild.intSetting(
            "target y",
            64,
            MIN_Y_COORDINATE,
            MAX_Y_COORDINATE,
            1,
            this::isSpecifiedSmartTarget,
            this
    );
    public final IntValue targetZ = ValueBuild.intSetting(
            "target z",
            0,
            MIN_HORIZONTAL_COORDINATE,
            MAX_HORIZONTAL_COORDINATE,
            1,
            this::isSpecifiedSmartTarget,
            this
    );

    private Goal smartGoal;

    @Override
    public void onEnable() {
        if (mode.is(Mode.SMART)) {
            updateSmartPathing();
        }
    }

    @Override
    public void onDisable() {
        stopSmartPathing();
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!mode.is(Mode.NORMAL) || isFallFlying()) {
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
            updateSmartPathing();
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
            updateSmartPathing();
        }
    }

    private void onSmartTargetModeChanged(SmartTargetMode newTargetMode) {
        if (!isEnabled() || !mode.is(Mode.SMART)) {
            return;
        }

        stopSmartPathing();
        updateSmartPathing();
    }

    private void updateSmartPathing() {
        if (isNull()) {
            return;
        }

        Goal nextSmartGoal = getSmartGoal();
        if (!nextSmartGoal.equals(smartGoal)) {
            smartGoal = nextSmartGoal;
        }

        if (isFallFlying()) {
            getDirectElytraProcess().setGoal(getElytraGoal());
            return;
        }

        getDirectElytraProcess().stop();
        ICustomGoalProcess customGoalProcess = getCustomGoalProcess();
        if (customGoalProcess.getGoal() != smartGoal) {
            customGoalProcess.setGoalAndPath(smartGoal);
        }
    }

    private void stopSmartPathing() {
        getDirectElytraProcess().stop();
        Goal currentSmartGoal = smartGoal;
        smartGoal = null;

        if (currentSmartGoal == null) {
            return;
        }

        ICustomGoalProcess customGoalProcess = getCustomGoalProcess();
        if (customGoalProcess.getGoal() == currentSmartGoal) {
            customGoalProcess.onLostControl();
        }
    }

    private Goal getSmartGoal() {
        if (smartTargetMode.is(SmartTargetMode.SPECIFIED)) {
            return new GoalBlock(targetX.getValue(), targetY.getValue(), targetZ.getValue());
        }

        return smartGoal == null
                ? GoalXZ.fromDirection(mc.player.position(), mc.player.getYRot(), SMART_PATH_DISTANCE)
                : smartGoal;
    }

    private GoalXZ getElytraGoal() {
        if (smartGoal instanceof GoalXZ goalXZ) {
            return goalXZ;
        }

        return new GoalXZ(targetX.getValue(), targetZ.getValue());
    }

    private boolean isSpecifiedSmartTarget() {
        return mode.is(Mode.SMART) && smartTargetMode.is(SmartTargetMode.SPECIFIED);
    }

    private ICustomGoalProcess getCustomGoalProcess() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
    }

    private IDirectElytraProcess getDirectElytraProcess() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getDirectElytraProcess();
    }

    private boolean isFallFlying() {
        return mc.player != null && mc.player.isFallFlying();
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

    public enum SmartTargetMode {
        AUTOMATIC,
        SPECIFIED
    }
}
