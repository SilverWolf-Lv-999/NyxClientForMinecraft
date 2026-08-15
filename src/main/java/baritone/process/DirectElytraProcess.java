/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IDirectElytraProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/**
 * Steers an already-active elytra glide while leaving flight speed and pitch control to the caller.
 */
public final class DirectElytraProcess extends BaritoneProcessHelper implements IDirectElytraProcess {
    private static final double ARRIVAL_DISTANCE_SQR = 4.0D;
    private static final float PRESERVED_PITCH_OFFSET = 1.0E-4F;

    private GoalXZ goal;

    public DirectElytraProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void setGoal(GoalXZ goal) {
        this.goal = Objects.requireNonNull(goal, "goal");
    }

    @Override
    public void stop() {
        this.goal = null;
    }

    @Override
    public boolean isActive() {
        return this.goal != null && this.ctx.player() != null && this.ctx.player().isFallFlying();
    }

    @Override
    public Optional<Float> getTargetYaw() {
        if (!this.isActive() || this.hasReachedGoal()) {
            return Optional.empty();
        }

        return Optional.of(this.calculateTargetRotation().getYaw());
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (!this.isActive()) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        this.baritone.getInputOverrideHandler().clearAllKeys();
        if (!this.hasReachedGoal()) {
            this.updateTargetRotation();
        }
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private boolean hasReachedGoal() {
        double xDifference = this.goal.getX() + 0.5D - this.ctx.player().getX();
        double zDifference = this.goal.getZ() + 0.5D - this.ctx.player().getZ();
        return xDifference * xDifference + zDifference * zDifference <= ARRIVAL_DISTANCE_SQR;
    }

    private void updateTargetRotation() {
        Rotation rotation = this.calculateTargetRotation();
        this.baritone.getLookBehavior().updateTarget(
                new Rotation(rotation.getYaw(), this.ctx.playerRotations().getPitch() + PRESERVED_PITCH_OFFSET),
                false
        );
    }

    private Rotation calculateTargetRotation() {
        Vec3 playerPosition = this.ctx.player().position();
        Vec3 targetPosition = new Vec3(this.goal.getX() + 0.5D, playerPosition.y, this.goal.getZ() + 0.5D);
        return RotationUtils.calcRotationFromVec3d(playerPosition, targetPosition, this.ctx.playerRotations());
    }

    @Override
    public void onLostControl() {
        this.stop();
    }

    @Override
    public String displayName0() {
        return "Direct Elytra " + this.goal;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public double priority() {
        return 1.0D;
    }
}
