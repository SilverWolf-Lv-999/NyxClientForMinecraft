package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.world.phys.AABB;

@ModuleInfo(name = "nyxclient.module.autojump.name", description = "nyxclient.module.autojump.description", category = Category.MOVEMENT)
public class AutoJump extends Module {
    public static final AutoJump INSTANCE = new AutoJump();

    private static final double EDGE_LOOK_AHEAD = 0.18;
    private static final double EDGE_PROBE_SIZE = 0.08;
    private static final double EDGE_PROBE_DEPTH = 0.08;
    private static final double STEP_LOOK_AHEAD = 0.24;

    public final BoolValue edge = ValueBuild.boolSetting("nyxclient.setting.autojump.edge.name", true, this);
    public final BoolValue step = ValueBuild.boolSetting("nyxclient.setting.autojump.step.name", true, this);

    public AutoJump() {

    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!canAutoJump()) {
            return;
        }

        double[] direction = movementDirection();
        if (direction == null) {
            return;
        }

        if ((edge.getValue() && isNearEdge(direction[0], direction[1]))
                || (step.getValue() && canJumpOntoBlock(direction[0], direction[1]))) {
            mc.player.jumpFromGround();
        }
    }

    private boolean canAutoJump() {
        return mc.player != null
                && mc.level != null
                && mc.player.onGround()
                && MovingUtility.isMoving()
                && !mc.player.isShiftKeyDown()
                && !mc.player.getAbilities().flying
                && !mc.player.isFallFlying()
                && !mc.player.isPassenger()
                && !mc.player.isInWater()
                && !mc.player.isInLava();
    }

    private double[] movementDirection() {
        double[] movement = MovingUtility.predictMovement();
        double length = Math.hypot(movement[0], movement[1]);
        if (length < 1.0E-5) {
            return null;
        }
        return new double[]{movement[0] / length, movement[1] / length};
    }

    private boolean isNearEdge(double xDirection, double zDirection) {
        AABB box = mc.player.getBoundingBox();
        double halfWidth = (box.maxX - box.minX) * 0.5;
        double halfDepth = (box.maxZ - box.minZ) * 0.5;
        double sampleX = mc.player.getX() + xDirection * (halfWidth + EDGE_LOOK_AHEAD);
        double sampleZ = mc.player.getZ() + zDirection * (halfDepth + EDGE_LOOK_AHEAD);
        AABB groundProbe = new AABB(
                sampleX - EDGE_PROBE_SIZE,
                box.minY - EDGE_PROBE_DEPTH,
                sampleZ - EDGE_PROBE_SIZE,
                sampleX + EDGE_PROBE_SIZE,
                box.minY - 0.02,
                sampleZ + EDGE_PROBE_SIZE
        );

        return mc.level.noBlockCollision(mc.player, groundProbe);
    }

    private boolean canJumpOntoBlock(double xDirection, double zDirection) {
        AABB nextBox = mc.player.getBoundingBox().move(xDirection * STEP_LOOK_AHEAD, 0.0, zDirection * STEP_LOOK_AHEAD);
        return !mc.level.noBlockCollision(mc.player, nextBox)
                && mc.level.noBlockCollision(mc.player, nextBox.move(0.0, 1.0, 0.0));
    }
}
