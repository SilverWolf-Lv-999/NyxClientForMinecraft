package io.github.seraphina.nyx.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ModuleInfo(
        name = "nyxclient.module.autoiceboot.name",
        description = "nyxclient.module.autoiceboot.description",
        category = Category.MOVEMENT
)
public class AutoIceBoot extends Module {
    public static final AutoIceBoot INSTANCE = new AutoIceBoot();

    private static final double BOAT_ACCELERATION = 0.04D;
    private static final double SLIPPERY_FRICTION = 0.9D;
    private static final double MIN_PHYSICS_FRICTION = 0.8D;
    private static final double MAX_PHYSICS_FRICTION = 0.995D;
    private static final double INNER_SAMPLE_SCALE = 0.82D;
    private static final double MARGIN_SAMPLE_DISTANCE = 0.32D;
    private static final double COLLISION_HORIZONTAL_INSET = 0.055D;
    private static final double COLLISION_BOTTOM_INSET = 0.06D;
    private static final double COLLISION_TOP_INSET = 0.04D;
    private static final double GROUND_HEIGHT_TOLERANCE = 0.08D;
    private static final double SURVIVAL_SCORE = 1_000.0D;
    private static final double COLLISION_FAILURE_PENALTY = 450.0D;
    private static final double OFF_ICE_FAILURE_PENALTY = 260.0D;
    private static final double STRAIGHT_BIAS = 1.5D;
    private static final double SCORE_EPSILON = 1.0E-6D;

    private static final PlanningProfile AUTO_PROFILE = new PlanningProfile(
            0.50D,
            70.0D,
            22.0D,
            5.0D,
            3.5D,
            7.0D
    );
    private static final PlanningProfile FROSTHEX_PROFILE = new PlanningProfile(
            0.44D,
            62.0D,
            15.0D,
            6.5D,
            2.75D,
            5.0D
    );

    public final EnumValue<ServerType> serverType = ValueBuild.enumSetting("server type", ServerType.AUTO, this);
    public final IntValue predictionTicks = ValueBuild.intSetting("prediction ticks", 48, 20, 80, 2, this);
    public final IntValue beamWidth = ValueBuild.intSetting("beam width", 30, 9, 60, 3, this);

    private Steer lastSteer = Steer.STRAIGHT;

    @Override
    public void onEnable() {
        resetSteering();
    }

    @Override
    public void onDisable() {
        resetSteering();
    }

    @EventTarget(4)
    public void onMoveInput(MoveInputEvent event) {
        AbstractBoat boat = controlledBoat();
        if (boat == null || !isForwardKeyPhysicallyDown()) {
            resetSteering();
            return;
        }

        PlanningContext context = createContext(boat);
        SurfaceMetrics currentSurface = sampleSurface(context, boat.getX(), boat.getZ());
        if (!currentSurface.isDriveable(context.profile().minimumCoverage())) {
            resetSteering();
            return;
        }

        Steer steer = chooseSteer(context, currentSurface);
        event.setForward(1.0F);
        event.setStrafe(steer.strafeInput());
        lastSteer = steer;
    }

    private AbstractBoat controlledBoat() {
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return null;
        }

        if (!(mc.player.getVehicle() instanceof AbstractBoat boat)
                || boat.isRemoved()
                || boat.getControllingPassenger() != mc.player) {
            return null;
        }

        return boat;
    }

    private boolean isForwardKeyPhysicallyDown() {
        if (mc.getWindow() == null) {
            return false;
        }

        InputConstants.Key key = mc.options.keyUp.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
        }

        return mc.options.keyUp.isDown();
    }

    private PlanningContext createContext(AbstractBoat boat) {
        AABB baseBox = boat.getBoundingBox();
        AABB collisionBox = new AABB(
                baseBox.minX + COLLISION_HORIZONTAL_INSET,
                baseBox.minY + COLLISION_BOTTOM_INSET,
                baseBox.minZ + COLLISION_HORIZONTAL_INSET,
                baseBox.maxX - COLLISION_HORIZONTAL_INSET,
                baseBox.maxY - COLLISION_TOP_INSET,
                baseBox.maxZ - COLLISION_HORIZONTAL_INSET
        );
        int groundY = Mth.floor(baseBox.minY - 0.001D);

        return new PlanningContext(
                boat,
                baseBox,
                collisionBox,
                boat.getX(),
                boat.getZ(),
                baseBox.minY,
                groundY,
                profile(),
                new HashMap<>()
        );
    }

    private PlanningProfile profile() {
        return serverType.is(ServerType.FROSTHEX) ? FROSTHEX_PROFILE : AUTO_PROFILE;
    }

    private Steer chooseSteer(PlanningContext context, SurfaceMetrics initialSurface) {
        AbstractBoat boat = context.boat();
        Vec3 movement = boat.getDeltaMovement();
        double angularVelocity = Mth.wrapDegrees(boat.getYRot() - boat.yRotO);
        angularVelocity = Mth.clamp(angularVelocity, -40.0D, 40.0D);

        SimState initial = new SimState(
                boat.getX(),
                boat.getZ(),
                movement.x,
                movement.z,
                boat.getYRot(),
                angularVelocity,
                initialSurface,
                null,
                lastSteer,
                0.0D
        );

        List<SimState> beam = List.of(initial);
        double[] actionScores = new double[Steer.values().length];
        Arrays.fill(actionScores, Double.NEGATIVE_INFINITY);

        int horizon = predictionTicks.getValue();
        for (int tick = 0; tick < horizon && !beam.isEmpty(); tick++) {
            List<SimState> candidates = new ArrayList<>(beam.size() * Steer.values().length);

            for (SimState state : beam) {
                for (Steer action : Steer.values()) {
                    if (state.lastSteer().isOpposite(action)) {
                        continue;
                    }

                    Steer firstSteer = state.firstSteer() == null ? action : state.firstSteer();
                    StepResult result = simulateStep(context, state, action, firstSteer);
                    if (result.state() == null) {
                        actionScores[firstSteer.ordinal()] = Math.max(
                                actionScores[firstSteer.ordinal()],
                                state.score() - result.failurePenalty()
                        );
                        continue;
                    }

                    candidates.add(result.state());
                    if (tick == horizon - 1) {
                        actionScores[firstSteer.ordinal()] = Math.max(
                                actionScores[firstSteer.ordinal()],
                                result.state().score()
                        );
                    }
                }
            }

            beam = selectBeam(candidates, beamWidth.getValue());
        }

        for (SimState state : beam) {
            if (state.firstSteer() != null) {
                actionScores[state.firstSteer().ordinal()] = Math.max(
                        actionScores[state.firstSteer().ordinal()],
                        state.score()
                );
            }
        }

        return selectAction(actionScores, context.profile());
    }

    private StepResult simulateStep(
            PlanningContext context,
            SimState state,
            Steer action,
            Steer firstSteer
    ) {
        double friction = Mth.clamp(
                state.surface().friction(),
                MIN_PHYSICS_FRICTION,
                MAX_PHYSICS_FRICTION
        );
        double velocityX = state.velocityX() * friction;
        double velocityZ = state.velocityZ() * friction;
        double angularVelocity = state.angularVelocity() * friction + action.turnAcceleration();
        double yaw = Mth.wrapDegrees(state.yaw() + angularVelocity);

        double yawRadians = Math.toRadians(yaw);
        velocityX += Math.sin(-yawRadians) * BOAT_ACCELERATION;
        velocityZ += Math.cos(yawRadians) * BOAT_ACCELERATION;

        double nextX = state.x() + velocityX;
        double nextZ = state.z() + velocityZ;
        if (hasSweptCollision(context, state.x(), state.z(), nextX, nextZ)) {
            return new StepResult(null, COLLISION_FAILURE_PENALTY);
        }

        SurfaceMetrics surface = sampleSurface(context, nextX, nextZ);
        if (!surface.isDriveable(context.profile().minimumCoverage())) {
            double uncovered = 1.0D - surface.coverage();
            return new StepResult(null, OFF_ICE_FAILURE_PENALTY + uncovered * 180.0D);
        }

        double score = state.score() + scoreStep(
                context.profile(),
                state,
                action,
                surface,
                velocityX,
                velocityZ,
                yaw,
                angularVelocity
        );

        return new StepResult(
                new SimState(
                        nextX,
                        nextZ,
                        velocityX,
                        velocityZ,
                        yaw,
                        angularVelocity,
                        surface,
                        firstSteer,
                        action,
                        score
                ),
                0.0D
        );
    }

    private double scoreStep(
            PlanningProfile profile,
            SimState previous,
            Steer action,
            SurfaceMetrics surface,
            double velocityX,
            double velocityZ,
            double yaw,
            double angularVelocity
    ) {
        double speed = Math.hypot(velocityX, velocityZ);
        double yawRadians = Math.toRadians(yaw);
        double forwardVelocity = velocityX * Math.sin(-yawRadians) + velocityZ * Math.cos(yawRadians);
        double velocityYaw = speed > SCORE_EPSILON
                ? Math.toDegrees(Math.atan2(-velocityX, velocityZ))
                : yaw;
        double driftAngle = Math.abs(Mth.wrapDegrees(yaw - velocityYaw));

        double score = SURVIVAL_SCORE;
        score += surface.coverage() * profile.coverageWeight();
        score += surface.margin() * profile.marginWeight();
        score += surface.centerIce() ? 10.0D : 0.0D;
        score += speed * profile.speedWeight();
        score += Math.max(0.0D, forwardVelocity) * 2.0D;
        score -= Math.abs(angularVelocity) * 0.12D;
        score -= driftAngle * 0.025D;
        score -= action == Steer.STRAIGHT ? 0.0D : 0.55D;

        if (action != previous.lastSteer()) {
            score -= profile.switchPenalty();
        }

        return score;
    }

    private List<SimState> selectBeam(List<SimState> candidates, int limit) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        candidates.sort(Comparator.comparingDouble(SimState::score).reversed());
        List<SimState> selected = new ArrayList<>(Math.min(limit, candidates.size()));
        Set<SimState> selectedSet = new HashSet<>();
        boolean[] representedFirstActions = new boolean[Steer.values().length];

        for (SimState candidate : candidates) {
            int actionIndex = candidate.firstSteer().ordinal();
            if (!representedFirstActions[actionIndex]) {
                selected.add(candidate);
                selectedSet.add(candidate);
                representedFirstActions[actionIndex] = true;
            }
        }

        for (SimState candidate : candidates) {
            if (selected.size() >= limit) {
                break;
            }
            if (selectedSet.add(candidate)) {
                selected.add(candidate);
            }
        }

        return selected;
    }

    private Steer selectAction(double[] scores, PlanningProfile profile) {
        double[] adjustedScores = scores.clone();
        if (Double.isFinite(adjustedScores[Steer.STRAIGHT.ordinal()])) {
            adjustedScores[Steer.STRAIGHT.ordinal()] += STRAIGHT_BIAS;
        }
        if (Double.isFinite(adjustedScores[lastSteer.ordinal()])) {
            adjustedScores[lastSteer.ordinal()] += profile.holdBias();
        }

        Steer best = Steer.STRAIGHT;
        double bestScore = adjustedScores[best.ordinal()];
        for (Steer action : Steer.values()) {
            double score = adjustedScores[action.ordinal()];
            if (score > bestScore + SCORE_EPSILON) {
                best = action;
                bestScore = score;
            }
        }

        return Double.isFinite(bestScore) ? best : Steer.STRAIGHT;
    }

    private boolean hasSweptCollision(
            PlanningContext context,
            double fromX,
            double fromZ,
            double toX,
            double toZ
    ) {
        AABB startBox = context.collisionBox().move(
                fromX - context.originX(),
                0.0D,
                fromZ - context.originZ()
        );
        AABB sweptBox = startBox.expandTowards(toX - fromX, 0.0D, toZ - fromZ);
        return !mc.level.noBlockCollision(context.boat(), sweptBox);
    }

    private SurfaceMetrics sampleSurface(PlanningContext context, double centerX, double centerZ) {
        double halfWidth = (context.baseBox().maxX - context.baseBox().minX) * 0.5D;
        double halfDepth = (context.baseBox().maxZ - context.baseBox().minZ) * 0.5D;
        double innerX = halfWidth * INNER_SAMPLE_SCALE;
        double innerZ = halfDepth * INNER_SAMPLE_SCALE;
        double[] xOffsets = {-innerX, 0.0D, innerX};
        double[] zOffsets = {-innerZ, 0.0D, innerZ};

        int iceSamples = 0;
        int supportSamples = 0;
        double frictionTotal = 0.0D;
        boolean centerIce = false;

        for (int xIndex = 0; xIndex < xOffsets.length; xIndex++) {
            for (int zIndex = 0; zIndex < zOffsets.length; zIndex++) {
                SupportCell cell = supportAt(
                        context,
                        Mth.floor(centerX + xOffsets[xIndex]),
                        Mth.floor(centerZ + zOffsets[zIndex])
                );
                if (cell.support()) {
                    supportSamples++;
                    frictionTotal += cell.friction();
                }
                if (cell.ice()) {
                    iceSamples++;
                }
                if (xIndex == 1 && zIndex == 1) {
                    centerIce = cell.ice();
                }
            }
        }

        double outerX = halfWidth + MARGIN_SAMPLE_DISTANCE;
        double outerZ = halfDepth + MARGIN_SAMPLE_DISTANCE;
        int marginIceSamples = 0;
        int marginSamples = 0;

        for (double zOffset : zOffsets) {
            marginIceSamples += isIceAt(context, centerX - outerX, centerZ + zOffset) ? 1 : 0;
            marginIceSamples += isIceAt(context, centerX + outerX, centerZ + zOffset) ? 1 : 0;
            marginSamples += 2;
        }
        for (double xOffset : xOffsets) {
            marginIceSamples += isIceAt(context, centerX + xOffset, centerZ - outerZ) ? 1 : 0;
            marginIceSamples += isIceAt(context, centerX + xOffset, centerZ + outerZ) ? 1 : 0;
            marginSamples += 2;
        }

        double coverage = iceSamples / 9.0D;
        double margin = marginSamples == 0 ? 0.0D : (double) marginIceSamples / marginSamples;
        double friction = supportSamples == 0
                ? MIN_PHYSICS_FRICTION
                : frictionTotal / supportSamples;
        return new SurfaceMetrics(coverage, margin, friction, centerIce);
    }

    private boolean isIceAt(PlanningContext context, double x, double z) {
        return supportAt(context, Mth.floor(x), Mth.floor(z)).ice();
    }

    private SupportCell supportAt(PlanningContext context, int x, int z) {
        BlockPos pos = new BlockPos(x, context.groundY(), z);
        long key = pos.asLong();
        SupportCell cached = context.supportCache().get(key);
        if (cached != null) {
            return cached;
        }

        SupportCell cell = readSupportCell(context, pos);
        context.supportCache().put(key, cell);
        return cell;
    }

    @SuppressWarnings("deprecation")
    private SupportCell readSupportCell(PlanningContext context, BlockPos pos) {
        if (!mc.level.hasChunkAt(pos)) {
            return SupportCell.EMPTY;
        }

        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(mc.level, pos);
        if (shape.isEmpty()) {
            return SupportCell.EMPTY;
        }

        double supportTop = pos.getY() + shape.max(Direction.Axis.Y);
        if (Math.abs(supportTop - context.groundHeight()) > GROUND_HEIGHT_TOLERANCE) {
            return SupportCell.EMPTY;
        }

        float friction = state.getFriction(mc.level, pos, context.boat());
        boolean ice = state.is(BlockTags.ICE) || friction >= SLIPPERY_FRICTION;
        return new SupportCell(true, ice, friction);
    }

    private void resetSteering() {
        lastSteer = Steer.STRAIGHT;
    }

    public enum ServerType {
        AUTO("Auto"),
        FROSTHEX("FrostHex");

        private final String name;

        ServerType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private enum Steer {
        STRAIGHT(0.0F, 0.0D),
        LEFT(1.0F, -1.0D),
        RIGHT(-1.0F, 1.0D);

        private final float strafeInput;
        private final double turnAcceleration;

        Steer(float strafeInput, double turnAcceleration) {
            this.strafeInput = strafeInput;
            this.turnAcceleration = turnAcceleration;
        }

        private float strafeInput() {
            return strafeInput;
        }

        private double turnAcceleration() {
            return turnAcceleration;
        }

        private boolean isOpposite(Steer other) {
            return this != STRAIGHT && other != STRAIGHT && this != other;
        }
    }

    private record PlanningProfile(
            double minimumCoverage,
            double coverageWeight,
            double marginWeight,
            double speedWeight,
            double switchPenalty,
            double holdBias
    ) {
    }

    private record PlanningContext(
            AbstractBoat boat,
            AABB baseBox,
            AABB collisionBox,
            double originX,
            double originZ,
            double groundHeight,
            int groundY,
            PlanningProfile profile,
            Map<Long, SupportCell> supportCache
    ) {
    }

    private record SurfaceMetrics(
            double coverage,
            double margin,
            double friction,
            boolean centerIce
    ) {
        private boolean isDriveable(double minimumCoverage) {
            return coverage >= minimumCoverage && (centerIce || coverage >= 0.78D);
        }
    }

    private record SupportCell(boolean support, boolean ice, double friction) {
        private static final SupportCell EMPTY = new SupportCell(false, false, MIN_PHYSICS_FRICTION);
    }

    private record SimState(
            double x,
            double z,
            double velocityX,
            double velocityZ,
            double yaw,
            double angularVelocity,
            SurfaceMetrics surface,
            Steer firstSteer,
            Steer lastSteer,
            double score
    ) {
    }

    private record StepResult(SimState state, double failurePenalty) {
    }
}
