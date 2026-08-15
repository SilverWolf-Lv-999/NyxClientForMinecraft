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

package baritone.api.process;

import baritone.api.pathing.goals.GoalXZ;

import java.util.Optional;

/**
 * Controls an active elytra glide towards a horizontal goal without managing speed, fireworks, or landing.
 */
public interface IDirectElytraProcess extends IBaritoneProcess {

    /**
     * Starts steering the current elytra glide towards the given horizontal goal.
     *
     * @param goal The destination to steer towards
     */
    void setGoal(GoalXZ goal);

    /**
     * Stops steering the current elytra glide.
     */
    void stop();

    /**
     * @return The yaw currently required to steer toward the goal, or empty if no steering is needed.
     */
    Optional<Float> getTargetYaw();
}
