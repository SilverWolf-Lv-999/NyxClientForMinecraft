package io.github.seraphina.nyx.client.module.movement;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.seraphina.nyx.client.NyxClient;
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
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL43C;

import java.nio.FloatBuffer;
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

    private static final double FORWARD_ACCELERATION = 0.04D;
    private static final double REVERSE_ACCELERATION = 0.005D;
    private static final double TURN_ONLY_ACCELERATION = 0.005D;
    private static final double SLIPPERY_FRICTION = 0.9D;
    private static final double MIN_PHYSICS_FRICTION = 0.8D;
    private static final double MAX_PHYSICS_FRICTION = 0.995D;
    private static final double INNER_SAMPLE_SCALE = 0.82D;
    private static final double MARGIN_SAMPLE_DISTANCE = 0.32D;
    private static final double COLLISION_SAFETY_MARGIN = 0.035D;
    private static final double COLLISION_SPEED_MARGIN = 0.035D;
    private static final double MAX_COLLISION_SAFETY_MARGIN = 0.16D;
    private static final double COLLISION_BOTTOM_INSET = 0.06D;
    private static final double COLLISION_TOP_INSET = 0.04D;
    private static final double GROUND_HEIGHT_TOLERANCE = 0.08D;
    private static final double SURFACE_SWEEP_STEP = 0.45D;
    private static final double SURFACE_CACHE_SCALE = 8.0D;
    private static final int MAX_SURFACE_SWEEP_SAMPLES = 12;
    private static final double TURN_SPEED_TARGET = 0.85D;
    private static final int BRAKING_PREVIEW_TICKS = 10;
    private static final int MAX_ADAPTIVE_HORIZON = 88;
    private static final int DIVERSITY_PRESERVATION_TICKS = 2;
    private static final int MAX_EXPANDED_CANDIDATES_PER_PLAN = 9_500;
    private static final int GPU_EXPANDED_CANDIDATES_PER_PLAN = 28_000;
    private static final int NVIDIA_EXPANDED_CANDIDATES_PER_PLAN = 54_000;
    private static final double GPU_GRID_MIN_RADIUS = 18.0D;
    private static final double GPU_GRID_MAX_RADIUS = 48.0D;
    private static final double SURVIVAL_SCORE = 1_000.0D;
    private static final double COLLISION_FAILURE_PENALTY = 450.0D;
    private static final double OFF_ICE_FAILURE_PENALTY = 260.0D;
    private static final double STRAIGHT_BIAS = 1.5D;
    private static final double FORWARD_BIAS = 1.0D;
    private static final double SCORE_EPSILON = 1.0E-6D;
    private static final double MIN_GUIDANCE_SPEED = 0.12D;
    private static final double MAX_GUIDANCE_YAW_STEP = 24.0D;
    private static final double GUIDANCE_LOCK_ANGLE = 115.0D;
    private static final double GUIDED_PROGRESS_WEIGHT = 7.0D;
    private static final double REVERSE_PROGRESS_PENALTY = 18.0D;
    private static final double MISALIGNED_FORWARD_YAW = 100.0D;
    private static final double MISALIGNED_FORWARD_PENALTY = 0.11D;

    private static final PlanningProfile AUTO_PROFILE = new PlanningProfile(
            0.50D,
            76.0D,
            34.0D,
            3.25D,
            1.15D,
            1.6D
    );
    private static final PlanningProfile FROSTHEX_PROFILE = new PlanningProfile(
            0.44D,
            66.0D,
            26.0D,
            4.25D,
            1.0D,
            1.3D
    );

    public final EnumValue<ServerType> serverType = ValueBuild.enumSetting("server type", ServerType.AUTO, this);
    public final IntValue predictionTicks = ValueBuild.intSetting("prediction ticks", 48, 20, 80, 2, this);
    public final IntValue beamWidth = ValueBuild.intSetting("beam width", 30, 9, 60, 3, this);

    private Action lastAction = Action.FORWARD_STRAIGHT;
    private double travelHeadingYaw;
    private boolean hasTravelHeading;
    private final GpuPlanner gpuPlanner = new GpuPlanner();

    @Override
    public void onEnable() {
        resetSteering();
    }

    @Override
    public void onDisable() {
        resetSteering();
        gpuPlanner.close();
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

        Action action = chooseAction(context, currentSurface);
        event.setForward(action.throttle().forwardInput());
        event.setStrafe(action.steer().strafeInput());
        lastAction = action;
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
                baseBox.minX,
                baseBox.minY + COLLISION_BOTTOM_INSET,
                baseBox.minZ,
                baseBox.maxX,
                baseBox.maxY - COLLISION_TOP_INSET,
                baseBox.maxZ
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
                new HashMap<>(),
                new HashMap<>()
        );
    }

    private PlanningProfile profile() {
        return serverType.is(ServerType.FROSTHEX) ? FROSTHEX_PROFILE : AUTO_PROFILE;
    }

    private Action chooseAction(PlanningContext context, SurfaceMetrics initialSurface) {
        AbstractBoat boat = context.boat();
        Vec3 movement = boat.getDeltaMovement();
        double angularVelocity = Mth.wrapDegrees(boat.getYRot() - boat.yRotO);
        angularVelocity = Mth.clamp(angularVelocity, -40.0D, 40.0D);
        double speed = Math.hypot(movement.x, movement.z);
        double initialTravelHeadingYaw = currentTravelHeading(boat, movement, speed);
        int horizon = adaptivePredictionTicks(speed, initialSurface.friction());

        Action gpuAction = gpuPlanner.chooseAction(
                context,
                initialSurface,
                movement,
                angularVelocity,
                initialTravelHeadingYaw,
                horizon
        );
        if (gpuAction != null) {
            return gpuAction;
        }

        SimState initial = new SimState(
                boat.getX(),
                boat.getZ(),
                movement.x,
                movement.z,
                boat.getYRot(),
                angularVelocity,
                initialTravelHeadingYaw,
                initialSurface,
                null,
                lastAction,
                0.0D
        );

        List<SimState> beam = List.of(initial);
        double[] actionScores = new double[Action.values().length];
        Arrays.fill(actionScores, Double.NEGATIVE_INFINITY);

        for (int tick = 0; tick < horizon && !beam.isEmpty(); tick++) {
            List<SimState> candidates = new ArrayList<>(beam.size() * Action.values().length);

            for (SimState state : beam) {
                for (Action action : Action.values()) {
                    Action firstAction = state.firstAction() == null ? action : state.firstAction();
                    StepResult result = simulateStep(context, state, action, firstAction);
                    if (result.state() == null) {
                        actionScores[firstAction.ordinal()] = Math.max(
                                actionScores[firstAction.ordinal()],
                                state.score() - result.failurePenalty()
                        );
                        continue;
                    }

                    candidates.add(result.state());
                    if (tick == horizon - 1) {
                        actionScores[firstAction.ordinal()] = Math.max(
                                actionScores[firstAction.ordinal()],
                                result.state().score()
                        );
                    }
                }
            }

            int effectiveBeamWidth = effectiveBeamWidth(horizon, tick);
            beam = selectBeam(candidates, effectiveBeamWidth);
        }

        for (SimState state : beam) {
            if (state.firstAction() != null) {
                actionScores[state.firstAction().ordinal()] = Math.max(
                        actionScores[state.firstAction().ordinal()],
                        state.score()
                );
            }
        }

        return selectAction(actionScores, context.profile());
    }

    private int effectiveBeamWidth(int horizon, int tick) {
        int requestedWidth = Math.max(beamWidth.getValue(), Action.values().length);
        int budgetedWidth = MAX_EXPANDED_CANDIDATES_PER_PLAN / Math.max(1, horizon * Action.values().length);
        int minimumWidth = tick < DIVERSITY_PRESERVATION_TICKS
                ? Action.values().length * Throttle.values().length
                : Action.values().length;
        return Math.min(requestedWidth, Math.max(minimumWidth, budgetedWidth));
    }

    private int adaptivePredictionTicks(double speed, double surfaceFriction) {
        int configuredTicks = predictionTicks.getValue();
        if (speed <= TURN_SPEED_TARGET) {
            return configuredTicks;
        }

        double friction = Mth.clamp(surfaceFriction, MIN_PHYSICS_FRICTION, MAX_PHYSICS_FRICTION);
        double predictedSpeed = speed;
        int brakingTicks = 0;
        while (predictedSpeed > TURN_SPEED_TARGET
                && brakingTicks < MAX_ADAPTIVE_HORIZON - BRAKING_PREVIEW_TICKS) {
            predictedSpeed = Math.max(0.0D, predictedSpeed * friction - REVERSE_ACCELERATION);
            brakingTicks++;
        }

        int brakingHorizon = brakingTicks + BRAKING_PREVIEW_TICKS;
        return Math.min(MAX_ADAPTIVE_HORIZON, Math.max(configuredTicks, brakingHorizon));
    }

    private StepResult simulateStep(
            PlanningContext context,
            SimState state,
            Action action,
            Action firstAction
    ) {
        double friction = Mth.clamp(
                state.surface().friction(),
                MIN_PHYSICS_FRICTION,
                MAX_PHYSICS_FRICTION
        );
        double velocityX = state.velocityX() * friction;
        double velocityZ = state.velocityZ() * friction;
        double angularVelocity = state.angularVelocity() * friction + action.steer().turnAcceleration();
        double yaw = Mth.wrapDegrees(state.yaw() + angularVelocity);

        double yawRadians = Math.toRadians(yaw);
        double propulsion = action.propulsionAcceleration();
        velocityX += Math.sin(-yawRadians) * propulsion;
        velocityZ += Math.cos(yawRadians) * propulsion;
        double nextTravelHeadingYaw = nextTravelHeading(state.travelHeadingYaw(), velocityX, velocityZ);

        double nextX = state.x() + velocityX;
        double nextZ = state.z() + velocityZ;

        double distance = Math.hypot(nextX - state.x(), nextZ - state.z());
        int surfaceSamples = Mth.clamp(
                Mth.ceil(distance / SURFACE_SWEEP_STEP),
                1,
                MAX_SURFACE_SWEEP_SAMPLES
        );
        SurfaceMetrics surface = state.surface();
        double minimumCoverage = 1.0D;
        double minimumMargin = 1.0D;
        boolean stayedCentered = true;
        for (int sample = 1; sample <= surfaceSamples; sample++) {
            double progress = (double) sample / surfaceSamples;
            double sampleX = Mth.lerp(progress, state.x(), nextX);
            double sampleZ = Mth.lerp(progress, state.z(), nextZ);
            surface = sampleSurfaceCached(context, sampleX, sampleZ);
            minimumCoverage = Math.min(minimumCoverage, surface.coverage());
            minimumMargin = Math.min(minimumMargin, surface.margin());
            stayedCentered &= surface.centerIce();
            if (!surface.isDriveable(context.profile().minimumCoverage())) {
                double uncovered = 1.0D - surface.coverage();
                return new StepResult(null, OFF_ICE_FAILURE_PENALTY + uncovered * 180.0D);
            }
        }

        if (hasSweptCollision(context, state.x(), state.z(), nextX, nextZ)) {
            return new StepResult(null, COLLISION_FAILURE_PENALTY);
        }

        double score = state.score() + scoreStep(
                context.profile(),
                state,
                action,
                minimumCoverage,
                minimumMargin,
                stayedCentered,
                velocityX,
                velocityZ,
                yaw,
                angularVelocity,
                nextTravelHeadingYaw
        );

        return new StepResult(
                new SimState(
                        nextX,
                        nextZ,
                        velocityX,
                        velocityZ,
                        yaw,
                        angularVelocity,
                        nextTravelHeadingYaw,
                        surface,
                        firstAction,
                        action,
                        score
                ),
                0.0D
        );
    }

    private double scoreStep(
            PlanningProfile profile,
            SimState previous,
            Action action,
            double minimumCoverage,
            double minimumMargin,
            boolean stayedCentered,
            double velocityX,
            double velocityZ,
            double yaw,
            double angularVelocity,
            double travelHeadingYaw
    ) {
        double speed = Math.hypot(velocityX, velocityZ);
        double yawRadians = Math.toRadians(yaw);
        double forwardVelocity = velocityX * Math.sin(-yawRadians) + velocityZ * Math.cos(yawRadians);
        double velocityYaw = speed > SCORE_EPSILON
                ? Math.toDegrees(Math.atan2(-velocityX, velocityZ))
                : yaw;
        double driftAngle = Math.abs(Mth.wrapDegrees(yaw - velocityYaw));
        double travelYawRadians = Math.toRadians(travelHeadingYaw);
        double guidedVelocity = velocityX * Math.sin(-travelYawRadians) + velocityZ * Math.cos(travelYawRadians);
        double headingYawDelta = Math.abs(Mth.wrapDegrees(yaw - travelHeadingYaw));
        double headingAlignment = Math.max(0.0D, Math.cos(Math.toRadians(headingYawDelta)));

        double score = SURVIVAL_SCORE;
        score += minimumCoverage * profile.coverageWeight();
        score += minimumMargin * profile.marginWeight();
        score += stayedCentered ? 10.0D : 0.0D;
        score += speed * profile.speedWeight();
        score += Math.max(0.0D, forwardVelocity) * 2.0D * headingAlignment;
        score += Math.max(0.0D, guidedVelocity) * GUIDED_PROGRESS_WEIGHT;
        score -= Math.max(0.0D, -guidedVelocity) * (REVERSE_PROGRESS_PENALTY + speed * 4.0D);
        score -= Math.abs(angularVelocity) * 0.16D;
        score -= driftAngle * (0.03D + speed * 0.025D);
        if (action.throttle() == Throttle.FORWARD && headingYawDelta > MISALIGNED_FORWARD_YAW) {
            score -= (headingYawDelta - MISALIGNED_FORWARD_YAW) * MISALIGNED_FORWARD_PENALTY;
        }
        score -= speed * speed * Math.pow(1.0D - minimumMargin, 2.0D) * 2.5D;
        score -= action.steer() == Steer.STRAIGHT ? 0.0D : 0.35D;
        score -= action.throttle().actionPenalty();

        if (action.steer() != previous.lastAction().steer()) {
            score -= profile.switchPenalty();
        }
        if (action.throttle() != previous.lastAction().throttle()) {
            score -= profile.switchPenalty() * 0.45D;
        }
        if (action.steer().isOpposite(previous.lastAction().steer())) {
            score -= 0.65D;
        }

        return score;
    }

    private double currentTravelHeading(AbstractBoat boat, Vec3 movement, double speed) {
        if (!hasTravelHeading) {
            travelHeadingYaw = speed >= MIN_GUIDANCE_SPEED ? yawFromVelocity(movement.x, movement.z) : boat.getYRot();
            hasTravelHeading = true;
            return travelHeadingYaw;
        }

        if (speed >= MIN_GUIDANCE_SPEED) {
            travelHeadingYaw = updateTravelHeading(travelHeadingYaw, movement.x, movement.z);
        }
        return travelHeadingYaw;
    }

    private static double nextTravelHeading(double currentHeadingYaw, double velocityX, double velocityZ) {
        double speed = Math.hypot(velocityX, velocityZ);
        return speed >= MIN_GUIDANCE_SPEED
                ? updateTravelHeading(currentHeadingYaw, velocityX, velocityZ)
                : currentHeadingYaw;
    }

    private static double updateTravelHeading(double currentHeadingYaw, double velocityX, double velocityZ) {
        double velocityYaw = yawFromVelocity(velocityX, velocityZ);
        double delta = Mth.wrapDegrees(velocityYaw - currentHeadingYaw);
        if (Math.abs(delta) > GUIDANCE_LOCK_ANGLE) {
            return currentHeadingYaw;
        }

        return Mth.wrapDegrees(currentHeadingYaw + Mth.clamp(delta, -MAX_GUIDANCE_YAW_STEP, MAX_GUIDANCE_YAW_STEP));
    }

    private static double yawFromVelocity(double velocityX, double velocityZ) {
        return Math.toDegrees(Math.atan2(-velocityX, velocityZ));
    }

    private List<SimState> selectBeam(List<SimState> candidates, int limit) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        candidates.sort(Comparator.comparingDouble(SimState::score).reversed());
        List<SimState> selected = new ArrayList<>(Math.min(limit, candidates.size()));
        Set<SimState> selectedSet = new HashSet<>();
        boolean[] representedFirstActions = new boolean[Action.values().length];
        boolean[][] representedThrottleBranches = new boolean[Action.values().length][Throttle.values().length];

        for (SimState candidate : candidates) {
            int actionIndex = candidate.firstAction().ordinal();
            if (!representedFirstActions[actionIndex]) {
                selected.add(candidate);
                selectedSet.add(candidate);
                representedFirstActions[actionIndex] = true;
                representedThrottleBranches[actionIndex][candidate.lastAction().throttle().ordinal()] = true;
                if (selected.size() >= limit) {
                    return selected;
                }
            }
        }

        for (SimState candidate : candidates) {
            int firstActionIndex = candidate.firstAction().ordinal();
            int throttleIndex = candidate.lastAction().throttle().ordinal();
            if (!representedThrottleBranches[firstActionIndex][throttleIndex]
                    && selectedSet.add(candidate)) {
                selected.add(candidate);
                representedThrottleBranches[firstActionIndex][throttleIndex] = true;
                if (selected.size() >= limit) {
                    return selected;
                }
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

    private Action selectAction(double[] scores, PlanningProfile profile) {
        double[] adjustedScores = scores.clone();
        for (Action action : Action.values()) {
            if (!Double.isFinite(adjustedScores[action.ordinal()])) {
                continue;
            }
            if (action.steer() == Steer.STRAIGHT) {
                adjustedScores[action.ordinal()] += STRAIGHT_BIAS;
            }
            if (action.throttle() == Throttle.FORWARD) {
                adjustedScores[action.ordinal()] += FORWARD_BIAS;
            }
        }
        if (Double.isFinite(adjustedScores[lastAction.ordinal()])) {
            adjustedScores[lastAction.ordinal()] += profile.holdBias();
        }

        Action best = Action.FORWARD_STRAIGHT;
        double bestScore = adjustedScores[best.ordinal()];
        for (Action action : Action.values()) {
            double score = adjustedScores[action.ordinal()];
            if (score > bestScore + SCORE_EPSILON) {
                best = action;
                bestScore = score;
            }
        }

        return Double.isFinite(bestScore) ? best : Action.BRAKE_STRAIGHT;
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
        if (!mc.level.noBlockCollision(context.boat(), sweptBox)) {
            return true;
        }

        double speed = Math.hypot(toX - fromX, toZ - fromZ);
        double safetyMargin = Math.min(
                MAX_COLLISION_SAFETY_MARGIN,
                COLLISION_SAFETY_MARGIN + speed * COLLISION_SPEED_MARGIN
        );
        AABB safeStartBox = startBox.inflate(safetyMargin, 0.0D, safetyMargin);
        if (!mc.level.noBlockCollision(context.boat(), safeStartBox)) {
            return false;
        }

        AABB safeSweptBox = safeStartBox.expandTowards(toX - fromX, 0.0D, toZ - fromZ);
        return !mc.level.noBlockCollision(context.boat(), safeSweptBox);
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

    private SurfaceMetrics sampleSurfaceCached(PlanningContext context, double centerX, double centerZ) {
        int quantizedX = Mth.floor(centerX * SURFACE_CACHE_SCALE);
        int quantizedZ = Mth.floor(centerZ * SURFACE_CACHE_SCALE);
        long key = packInts(quantizedX, quantizedZ);
        SurfaceMetrics cached = context.surfaceCache().get(key);
        if (cached != null) {
            return cached;
        }

        double cachedX = (quantizedX + 0.5D) / SURFACE_CACHE_SCALE;
        double cachedZ = (quantizedZ + 0.5D) / SURFACE_CACHE_SCALE;
        SurfaceMetrics surface = sampleSurface(context, cachedX, cachedZ);
        context.surfaceCache().put(key, surface);
        return surface;
    }

    private boolean isIceAt(PlanningContext context, double x, double z) {
        return supportAt(context, Mth.floor(x), Mth.floor(z)).ice();
    }

    private SupportCell supportAt(PlanningContext context, int x, int z) {
        long key = packInts(x, z);
        SupportCell cached = context.supportCache().get(key);
        if (cached != null) {
            return cached;
        }

        BlockPos pos = new BlockPos(x, context.groundY(), z);
        SupportCell cell = readSupportCell(context, pos);
        context.supportCache().put(key, cell);
        return cell;
    }

    private static long packInts(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
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
        lastAction = Action.FORWARD_STRAIGHT;
        hasTravelHeading = false;
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

    private enum Throttle {
        FORWARD(1.0F, FORWARD_ACCELERATION, 0.0D),
        COAST(0.0F, 0.0D, 0.12D),
        BRAKE(-1.0F, -REVERSE_ACCELERATION, 0.28D);

        private final float forwardInput;
        private final double acceleration;
        private final double actionPenalty;

        Throttle(float forwardInput, double acceleration, double actionPenalty) {
            this.forwardInput = forwardInput;
            this.acceleration = acceleration;
            this.actionPenalty = actionPenalty;
        }

        private float forwardInput() {
            return forwardInput;
        }

        private double acceleration(Steer steer) {
            if (this == COAST && steer != Steer.STRAIGHT) {
                return TURN_ONLY_ACCELERATION;
            }
            return acceleration;
        }

        private double actionPenalty() {
            return actionPenalty;
        }
    }

    private enum Action {
        FORWARD_STRAIGHT(Steer.STRAIGHT, Throttle.FORWARD),
        FORWARD_LEFT(Steer.LEFT, Throttle.FORWARD),
        FORWARD_RIGHT(Steer.RIGHT, Throttle.FORWARD),
        COAST_STRAIGHT(Steer.STRAIGHT, Throttle.COAST),
        COAST_LEFT(Steer.LEFT, Throttle.COAST),
        COAST_RIGHT(Steer.RIGHT, Throttle.COAST),
        BRAKE_STRAIGHT(Steer.STRAIGHT, Throttle.BRAKE),
        BRAKE_LEFT(Steer.LEFT, Throttle.BRAKE),
        BRAKE_RIGHT(Steer.RIGHT, Throttle.BRAKE);

        private final Steer steer;
        private final Throttle throttle;

        Action(Steer steer, Throttle throttle) {
            this.steer = steer;
            this.throttle = throttle;
        }

        private Steer steer() {
            return steer;
        }

        private Throttle throttle() {
            return throttle;
        }

        private double propulsionAcceleration() {
            return throttle.acceleration(steer);
        }
    }

    private final class GpuPlanner {
        private static final int STATE_STRIDE = 11;
        private static final int OUTPUT_STRIDE = 16;
        private static final int CELL_STRIDE = 4;
        private static final int WORKGROUP_SIZE = 64;
        private static final String COMPUTE_SOURCE = """
                #version 430

                layout(local_size_x = 64) in;

                layout(std430, binding = 0) readonly buffer StateBuffer {
                    float states[];
                };

                layout(std430, binding = 1) readonly buffer CellBuffer {
                    float cells[];
                };

                layout(std430, binding = 2) writeonly buffer OutputBuffer {
                    float outputs[];
                };

                uniform int uStateCount;
                uniform int uGridOriginX;
                uniform int uGridOriginZ;
                uniform int uGridWidth;
                uniform int uGridDepth;
                uniform float uHalfWidth;
                uniform float uHalfDepth;
                uniform float uCollisionMinX;
                uniform float uCollisionMinZ;
                uniform float uCollisionMaxX;
                uniform float uCollisionMaxZ;
                uniform float uMinimumCoverage;
                uniform float uCoverageWeight;
                uniform float uMarginWeight;
                uniform float uSpeedWeight;
                uniform float uSwitchPenalty;

                const float FORWARD_ACCELERATION = 0.04;
                const float REVERSE_ACCELERATION = 0.005;
                const float TURN_ONLY_ACCELERATION = 0.005;
                const float MIN_PHYSICS_FRICTION = 0.8;
                const float MAX_PHYSICS_FRICTION = 0.995;
                const float INNER_SAMPLE_SCALE = 0.82;
                const float MARGIN_SAMPLE_DISTANCE = 0.32;
                const float SURFACE_SWEEP_STEP = 0.45;
                const int MAX_SURFACE_SWEEP_SAMPLES = 12;
                const float SURVIVAL_SCORE = 1000.0;
                const float COLLISION_FAILURE_PENALTY = 450.0;
                const float OFF_ICE_FAILURE_PENALTY = 260.0;
                const float SCORE_EPSILON = 0.000001;
                const float COLLISION_SAFETY_MARGIN = 0.035;
                const float COLLISION_SPEED_MARGIN = 0.035;
                const float MAX_COLLISION_SAFETY_MARGIN = 0.16;
                const float MIN_GUIDANCE_SPEED = 0.12;
                const float MAX_GUIDANCE_YAW_STEP = 24.0;
                const float GUIDANCE_LOCK_ANGLE = 115.0;
                const float GUIDED_PROGRESS_WEIGHT = 7.0;
                const float REVERSE_PROGRESS_PENALTY = 18.0;
                const float MISALIGNED_FORWARD_YAW = 100.0;
                const float MISALIGNED_FORWARD_PENALTY = 0.11;

                struct Surface {
                    float coverage;
                    float margin;
                    float friction;
                    float centerIce;
                };

                float wrapDegrees(float degrees) {
                    return degrees - floor((degrees + 180.0) / 360.0) * 360.0;
                }

                int steerOf(int action) {
                    int column = action - (action / 3) * 3;
                    return column == 1 ? -1 : (column == 2 ? 1 : 0);
                }

                int throttleOf(int action) {
                    return action / 3;
                }

                float propulsionOf(int action) {
                    int throttle = throttleOf(action);
                    if (throttle == 0) {
                        return FORWARD_ACCELERATION;
                    }
                    if (throttle == 2) {
                        return -REVERSE_ACCELERATION;
                    }
                    return steerOf(action) == 0 ? 0.0 : TURN_ONLY_ACCELERATION;
                }

                float actionPenalty(int action) {
                    int throttle = throttleOf(action);
                    return throttle == 1 ? 0.12 : (throttle == 2 ? 0.28 : 0.0);
                }

                vec4 cellAt(int blockX, int blockZ) {
                    int x = blockX - uGridOriginX;
                    int z = blockZ - uGridOriginZ;
                    if (x < 0 || z < 0 || x >= uGridWidth || z >= uGridDepth) {
                        return vec4(0.0, 0.0, MIN_PHYSICS_FRICTION, 1.0);
                    }

                    int index = (z * uGridWidth + x) * 4;
                    return vec4(cells[index], cells[index + 1], cells[index + 2], cells[index + 3]);
                }

                Surface sampleSurface(float centerX, float centerZ) {
                    float innerX = uHalfWidth * INNER_SAMPLE_SCALE;
                    float innerZ = uHalfDepth * INNER_SAMPLE_SCALE;
                    float xs[3] = float[3](-innerX, 0.0, innerX);
                    float zs[3] = float[3](-innerZ, 0.0, innerZ);
                    int iceSamples = 0;
                    int supportSamples = 0;
                    float frictionTotal = 0.0;
                    float centerIce = 0.0;

                    for (int xi = 0; xi < 3; xi++) {
                        for (int zi = 0; zi < 3; zi++) {
                            vec4 cell = cellAt(int(floor(centerX + xs[xi])), int(floor(centerZ + zs[zi])));
                            if (cell.x > 0.5) {
                                supportSamples++;
                                frictionTotal += cell.z;
                            }
                            if (cell.y > 0.5) {
                                iceSamples++;
                            }
                            if (xi == 1 && zi == 1) {
                                centerIce = cell.y;
                            }
                        }
                    }

                    float outerX = uHalfWidth + MARGIN_SAMPLE_DISTANCE;
                    float outerZ = uHalfDepth + MARGIN_SAMPLE_DISTANCE;
                    int marginIceSamples = 0;
                    int marginSamples = 0;
                    for (int i = 0; i < 3; i++) {
                        marginIceSamples += cellAt(int(floor(centerX - outerX)), int(floor(centerZ + zs[i]))).y > 0.5 ? 1 : 0;
                        marginIceSamples += cellAt(int(floor(centerX + outerX)), int(floor(centerZ + zs[i]))).y > 0.5 ? 1 : 0;
                        marginSamples += 2;
                        marginIceSamples += cellAt(int(floor(centerX + xs[i])), int(floor(centerZ - outerZ))).y > 0.5 ? 1 : 0;
                        marginIceSamples += cellAt(int(floor(centerX + xs[i])), int(floor(centerZ + outerZ))).y > 0.5 ? 1 : 0;
                        marginSamples += 2;
                    }

                    Surface surface;
                    surface.coverage = float(iceSamples) / 9.0;
                    surface.margin = float(marginIceSamples) / float(marginSamples);
                    surface.friction = supportSamples == 0 ? MIN_PHYSICS_FRICTION : frictionTotal / float(supportSamples);
                    surface.centerIce = centerIce;
                    return surface;
                }

                bool isDriveable(Surface surface) {
                    return surface.coverage >= uMinimumCoverage && (surface.centerIce > 0.5 || surface.coverage >= 0.78);
                }

                bool blockedAt(float centerX, float centerZ, float margin) {
                    int minX = int(floor(centerX + uCollisionMinX - margin));
                    int maxX = int(floor(centerX + uCollisionMaxX + margin));
                    int minZ = int(floor(centerZ + uCollisionMinZ - margin));
                    int maxZ = int(floor(centerZ + uCollisionMaxZ + margin));
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            if (cellAt(x, z).w > 0.5) {
                                return true;
                            }
                        }
                    }
                    return false;
                }

                bool sweptCollision(float fromX, float fromZ, float toX, float toZ) {
                    float dx = toX - fromX;
                    float dz = toZ - fromZ;
                    float distance = length(vec2(dx, dz));
                    int samples = clamp(int(ceil(distance / 0.25)), 1, 12);
                    for (int i = 0; i <= samples; i++) {
                        float progress = float(i) / float(samples);
                        if (blockedAt(mix(fromX, toX, progress), mix(fromZ, toZ, progress), 0.0)) {
                            return true;
                        }
                    }

                    float safetyMargin = min(MAX_COLLISION_SAFETY_MARGIN, COLLISION_SAFETY_MARGIN + distance * COLLISION_SPEED_MARGIN);
                    if (blockedAt(fromX, fromZ, safetyMargin)) {
                        return false;
                    }
                    for (int i = 0; i <= samples; i++) {
                        float progress = float(i) / float(samples);
                        if (blockedAt(mix(fromX, toX, progress), mix(fromZ, toZ, progress), safetyMargin)) {
                            return true;
                        }
                    }
                    return false;
                }

                bool oppositeSteer(int a, int b) {
                    int sa = steerOf(a);
                    int sb = steerOf(b);
                    return sa != 0 && sb != 0 && sa != sb;
                }

                float nextTravelHeading(float currentHeadingYaw, float vx, float vz) {
                    if (length(vec2(vx, vz)) < MIN_GUIDANCE_SPEED) {
                        return currentHeadingYaw;
                    }

                    float velocityYaw = degrees(atan(-vx, vz));
                    float delta = wrapDegrees(velocityYaw - currentHeadingYaw);
                    if (abs(delta) > GUIDANCE_LOCK_ANGLE) {
                        return currentHeadingYaw;
                    }

                    return wrapDegrees(currentHeadingYaw + clamp(delta, -MAX_GUIDANCE_YAW_STEP, MAX_GUIDANCE_YAW_STEP));
                }

                void writeCandidate(uint gid, float x, float z, float vx, float vz, float yaw, float angularVelocity,
                                    float travelHeadingYaw,
                                    Surface surface, float firstAction, float lastAction, float score, float valid,
                                    float failurePenalty) {
                    uint base = gid * 16u;
                    outputs[base] = x;
                    outputs[base + 1u] = z;
                    outputs[base + 2u] = vx;
                    outputs[base + 3u] = vz;
                    outputs[base + 4u] = yaw;
                    outputs[base + 5u] = angularVelocity;
                    outputs[base + 6u] = travelHeadingYaw;
                    outputs[base + 7u] = surface.friction;
                    outputs[base + 8u] = surface.coverage;
                    outputs[base + 9u] = surface.margin;
                    outputs[base + 10u] = surface.centerIce;
                    outputs[base + 11u] = firstAction;
                    outputs[base + 12u] = lastAction;
                    outputs[base + 13u] = score;
                    outputs[base + 14u] = valid;
                    outputs[base + 15u] = failurePenalty;
                }

                void main() {
                    uint gid = gl_GlobalInvocationID.x;
                    uint candidateCount = uint(uStateCount * 9);
                    if (gid >= candidateCount) {
                        return;
                    }

                    int stateIndex = int(gid / 9u);
                    int action = int(gid - uint(stateIndex * 9));
                    int base = stateIndex * 11;
                    float x = states[base];
                    float z = states[base + 1];
                    float vx = states[base + 2];
                    float vz = states[base + 3];
                    float yaw = states[base + 4];
                    float angularVelocity = states[base + 5];
                    float travelHeadingYaw = states[base + 6];
                    float friction = clamp(states[base + 7], MIN_PHYSICS_FRICTION, MAX_PHYSICS_FRICTION);
                    float firstAction = states[base + 8] < 0.0 ? float(action) : states[base + 8];
                    int lastAction = int(states[base + 9] + 0.5);
                    float previousScore = states[base + 10];

                    float nextVx = vx * friction;
                    float nextVz = vz * friction;
                    float nextAngularVelocity = angularVelocity * friction + float(steerOf(action));
                    float nextYaw = wrapDegrees(yaw + nextAngularVelocity);
                    float yawRadians = radians(nextYaw);
                    float propulsion = propulsionOf(action);
                    nextVx += sin(-yawRadians) * propulsion;
                    nextVz += cos(yawRadians) * propulsion;
                    float nextTravelHeadingYaw = nextTravelHeading(travelHeadingYaw, nextVx, nextVz);

                    float nextX = x + nextVx;
                    float nextZ = z + nextVz;
                    float distance = length(vec2(nextX - x, nextZ - z));
                    int surfaceSamples = clamp(int(ceil(distance / SURFACE_SWEEP_STEP)), 1, MAX_SURFACE_SWEEP_SAMPLES);
                    Surface surface = sampleSurface(x, z);
                    float minimumCoverage = 1.0;
                    float minimumMargin = 1.0;
                    bool stayedCentered = true;

                    for (int sample = 1; sample <= surfaceSamples; sample++) {
                        float progress = float(sample) / float(surfaceSamples);
                        surface = sampleSurface(mix(x, nextX, progress), mix(z, nextZ, progress));
                        minimumCoverage = min(minimumCoverage, surface.coverage);
                        minimumMargin = min(minimumMargin, surface.margin);
                        stayedCentered = stayedCentered && surface.centerIce > 0.5;
                        if (!isDriveable(surface)) {
                            float uncovered = 1.0 - surface.coverage;
                            writeCandidate(gid, nextX, nextZ, nextVx, nextVz, nextYaw, nextAngularVelocity,
                                    nextTravelHeadingYaw,
                                    surface, firstAction, float(action), previousScore - OFF_ICE_FAILURE_PENALTY - uncovered * 180.0,
                                    0.0, OFF_ICE_FAILURE_PENALTY + uncovered * 180.0);
                            return;
                        }
                    }

                    if (sweptCollision(x, z, nextX, nextZ)) {
                        writeCandidate(gid, nextX, nextZ, nextVx, nextVz, nextYaw, nextAngularVelocity,
                                nextTravelHeadingYaw,
                                surface, firstAction, float(action), previousScore - COLLISION_FAILURE_PENALTY,
                                0.0, COLLISION_FAILURE_PENALTY);
                        return;
                    }

                    float speed = length(vec2(nextVx, nextVz));
                    float forwardVelocity = nextVx * sin(-yawRadians) + nextVz * cos(yawRadians);
                    float velocityYaw = speed > SCORE_EPSILON ? degrees(atan(-nextVx, nextVz)) : nextYaw;
                    float driftAngle = abs(wrapDegrees(nextYaw - velocityYaw));
                    float travelYawRadians = radians(nextTravelHeadingYaw);
                    float guidedVelocity = nextVx * sin(-travelYawRadians) + nextVz * cos(travelYawRadians);
                    float headingYawDelta = abs(wrapDegrees(nextYaw - nextTravelHeadingYaw));
                    float headingAlignment = max(0.0, cos(radians(headingYawDelta)));
                    float score = SURVIVAL_SCORE;
                    score += minimumCoverage * uCoverageWeight;
                    score += minimumMargin * uMarginWeight;
                    score += stayedCentered ? 10.0 : 0.0;
                    score += speed * uSpeedWeight;
                    score += max(0.0, forwardVelocity) * 2.0 * headingAlignment;
                    score += max(0.0, guidedVelocity) * GUIDED_PROGRESS_WEIGHT;
                    score -= max(0.0, -guidedVelocity) * (REVERSE_PROGRESS_PENALTY + speed * 4.0);
                    score -= abs(nextAngularVelocity) * 0.16;
                    score -= driftAngle * (0.03 + speed * 0.025);
                    if (throttleOf(action) == 0 && headingYawDelta > MISALIGNED_FORWARD_YAW) {
                        score -= (headingYawDelta - MISALIGNED_FORWARD_YAW) * MISALIGNED_FORWARD_PENALTY;
                    }
                    score -= speed * speed * pow(1.0 - minimumMargin, 2.0) * 2.5;
                    score -= steerOf(action) == 0 ? 0.0 : 0.35;
                    score -= actionPenalty(action);
                    score -= steerOf(action) == steerOf(lastAction) ? 0.0 : uSwitchPenalty;
                    score -= throttleOf(action) == throttleOf(lastAction) ? 0.0 : uSwitchPenalty * 0.45;
                    score -= oppositeSteer(action, lastAction) ? 0.65 : 0.0;

                    writeCandidate(gid, nextX, nextZ, nextVx, nextVz, nextYaw, nextAngularVelocity,
                            nextTravelHeadingYaw,
                            surface, firstAction, float(action), previousScore + score, 1.0, 0.0);
                }
                """;

        private int program;
        private int stateBuffer;
        private int cellBuffer;
        private int outputBuffer;
        private boolean initialized;
        private boolean disabled;
        private boolean nvidiaRenderer;

        private Action chooseAction(
                PlanningContext context,
                SurfaceMetrics initialSurface,
                Vec3 movement,
                double angularVelocity,
                double initialTravelHeadingYaw,
                int horizon
        ) {
            if (disabled || !RenderSystem.isOnRenderThread() || !ensureReady()) {
                return null;
            }

            try {
                double speed = Math.hypot(movement.x, movement.z);
                GpuGrid grid = buildGrid(context, speed, horizon);
                uploadGrid(grid);
                Action action = runPlan(
                        context,
                        initialSurface,
                        movement,
                        angularVelocity,
                        initialTravelHeadingYaw,
                        horizon,
                        grid
                );
                return isFirstStepSafe(
                        context,
                        initialSurface,
                        movement,
                        angularVelocity,
                        initialTravelHeadingYaw,
                        action
                ) ? action : null;
            } catch (RuntimeException ignored) {
                disabled = true;
                close();
                return null;
            }
        }

        private boolean ensureReady() {
            if (initialized) {
                return true;
            }
            try {
                if (GL.getCapabilities() == null || !GL.getCapabilities().OpenGL43) {
                    disabled = true;
                    return false;
                }
                String vendor = String.valueOf(GL11C.glGetString(GL11C.GL_VENDOR));
                String renderer = String.valueOf(GL11C.glGetString(GL11C.GL_RENDERER));
                nvidiaRenderer = vendor.toLowerCase().contains("nvidia")
                        || renderer.toLowerCase().contains("nvidia")
                        || renderer.toLowerCase().contains("rtx");
                NyxClient.LOGGER.info("AutoIceBoot GPU planner using OpenGL renderer: {} ({})", renderer, vendor);
                if (!nvidiaRenderer) {
                    NyxClient.LOGGER.warn("AutoIceBoot GPU planner did not get an NVIDIA OpenGL renderer; "
                            + "load sera_native before the GL context or force javaw.exe to RTX 4050 in Windows graphics settings.");
                }

                int shader = GL43C.glCreateShader(GL43C.GL_COMPUTE_SHADER);
                GL43C.glShaderSource(shader, COMPUTE_SOURCE);
                GL43C.glCompileShader(shader);
                if (GL43C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL15C.GL_FALSE) {
                    System.err.println("AutoIceBoot GPU shader compile failed: "
                            + GL43C.glGetShaderInfoLog(shader, 4096));
                    GL43C.glDeleteShader(shader);
                    disabled = true;
                    return false;
                }

                program = GL43C.glCreateProgram();
                GL43C.glAttachShader(program, shader);
                GL43C.glLinkProgram(program);
                GL43C.glDeleteShader(shader);
                if (GL43C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL15C.GL_FALSE) {
                    System.err.println("AutoIceBoot GPU shader link failed: "
                            + GL43C.glGetProgramInfoLog(program, 4096));
                    close();
                    disabled = true;
                    return false;
                }

                stateBuffer = GL43C.glGenBuffers();
                cellBuffer = GL43C.glGenBuffers();
                outputBuffer = GL43C.glGenBuffers();
                initialized = true;
                return true;
            } catch (RuntimeException ignored) {
                disabled = true;
                close();
                return false;
            }
        }

        private Action runPlan(
                PlanningContext context,
                SurfaceMetrics initialSurface,
                Vec3 movement,
                double angularVelocity,
                double initialTravelHeadingYaw,
                int horizon,
                GpuGrid grid
        ) {
            AbstractBoat boat = context.boat();
            SimState initial = new SimState(
                    boat.getX(),
                    boat.getZ(),
                    movement.x,
                    movement.z,
                    boat.getYRot(),
                    angularVelocity,
                    initialTravelHeadingYaw,
                    initialSurface,
                    null,
                    lastAction,
                    0.0D
            );

            List<SimState> beam = List.of(initial);
            double[] actionScores = new double[Action.values().length];
            Arrays.fill(actionScores, Double.NEGATIVE_INFINITY);

            int previousProgram = GL11GetCurrentProgram();
            try {
                GL43C.glUseProgram(program);
                setUniforms(context, grid);
                for (int tick = 0; tick < horizon && !beam.isEmpty(); tick++) {
                    List<GpuCandidate> expanded = expand(beam);
                    List<SimState> candidates = new ArrayList<>(expanded.size());
                    for (GpuCandidate candidate : expanded) {
                        if (!candidate.valid()) {
                            actionScores[candidate.firstAction().ordinal()] = Math.max(
                                    actionScores[candidate.firstAction().ordinal()],
                                    candidate.score()
                            );
                            continue;
                        }

                        SimState state = candidate.toState();
                        candidates.add(state);
                        if (tick == horizon - 1) {
                            actionScores[state.firstAction().ordinal()] = Math.max(
                                    actionScores[state.firstAction().ordinal()],
                                    state.score()
                            );
                        }
                    }

                    beam = selectBeam(candidates, gpuEffectiveBeamWidth(horizon, tick));
                }
            } finally {
                GL43C.glUseProgram(previousProgram);
                GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
            }

            for (SimState state : beam) {
                if (state.firstAction() != null) {
                    actionScores[state.firstAction().ordinal()] = Math.max(
                            actionScores[state.firstAction().ordinal()],
                            state.score()
                    );
                }
            }

            return hasFiniteScore(actionScores) ? selectAction(actionScores, context.profile()) : null;
        }

        private List<GpuCandidate> expand(List<SimState> beam) {
            int stateCount = beam.size();
            int candidateCount = stateCount * Action.values().length;

            FloatBuffer input = BufferUtils.createFloatBuffer(stateCount * STATE_STRIDE);
            for (SimState state : beam) {
                input.put((float) state.x());
                input.put((float) state.z());
                input.put((float) state.velocityX());
                input.put((float) state.velocityZ());
                input.put((float) state.yaw());
                input.put((float) state.angularVelocity());
                input.put((float) state.travelHeadingYaw());
                input.put((float) state.surface().friction());
                input.put(state.firstAction() == null ? -1.0F : (float) state.firstAction().ordinal());
                input.put((float) state.lastAction().ordinal());
                input.put((float) state.score());
            }
            input.flip();

            GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, stateBuffer);
            GL43C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, input, GL43C.GL_STREAM_DRAW);
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, stateBuffer);

            GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, outputBuffer);
            GL43C.glBufferData(
                    GL43C.GL_SHADER_STORAGE_BUFFER,
                    (long) candidateCount * OUTPUT_STRIDE * Float.BYTES,
                    GL43C.GL_STREAM_READ
            );
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 2, outputBuffer);

            GL43C.glUniform1i(GL43C.glGetUniformLocation(program, "uStateCount"), stateCount);
            GL43C.glDispatchCompute(Math.max(1, Mth.ceil((double) candidateCount / WORKGROUP_SIZE)), 1, 1);
            GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT | GL43C.GL_BUFFER_UPDATE_BARRIER_BIT);

            FloatBuffer output = BufferUtils.createFloatBuffer(candidateCount * OUTPUT_STRIDE);
            GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, outputBuffer);
            GL15C.glGetBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 0L, output);

            List<GpuCandidate> candidates = new ArrayList<>(candidateCount);
            for (int i = 0; i < candidateCount; i++) {
                int base = i * OUTPUT_STRIDE;
                SurfaceMetrics surface = new SurfaceMetrics(
                        output.get(base + 8),
                        output.get(base + 9),
                        output.get(base + 7),
                        output.get(base + 10) > 0.5F
                );
                int firstAction = Mth.clamp(Math.round(output.get(base + 11)), 0, Action.values().length - 1);
                int lastAction = Mth.clamp(Math.round(output.get(base + 12)), 0, Action.values().length - 1);
                candidates.add(new GpuCandidate(
                        output.get(base),
                        output.get(base + 1),
                        output.get(base + 2),
                        output.get(base + 3),
                        output.get(base + 4),
                        output.get(base + 5),
                        output.get(base + 6),
                        surface,
                        Action.values()[firstAction],
                        Action.values()[lastAction],
                        output.get(base + 13),
                        output.get(base + 14) > 0.5F
                ));
            }

            return candidates;
        }

        private int gpuEffectiveBeamWidth(int horizon, int tick) {
            int requestedWidth = Math.max(beamWidth.getValue(), Action.values().length);
            int budget = nvidiaRenderer ? NVIDIA_EXPANDED_CANDIDATES_PER_PLAN : GPU_EXPANDED_CANDIDATES_PER_PLAN;
            int budgetedWidth = budget / Math.max(1, horizon * Action.values().length);
            int minimumWidth = tick < DIVERSITY_PRESERVATION_TICKS
                    ? Action.values().length * Throttle.values().length
                    : Action.values().length;
            return Math.max(requestedWidth, Math.max(minimumWidth, budgetedWidth));
        }

        private void uploadGrid(GpuGrid grid) {
            FloatBuffer cells = BufferUtils.createFloatBuffer(grid.cells().length);
            cells.put(grid.cells());
            cells.flip();
            GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, cellBuffer);
            GL43C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, cells, GL43C.GL_STREAM_DRAW);
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 1, cellBuffer);
        }

        private void setUniforms(PlanningContext context, GpuGrid grid) {
            PlanningProfile profile = context.profile();
            double halfWidth = (context.baseBox().maxX - context.baseBox().minX) * 0.5D;
            double halfDepth = (context.baseBox().maxZ - context.baseBox().minZ) * 0.5D;
            GL43C.glUniform1i(GL43C.glGetUniformLocation(program, "uGridOriginX"), grid.originX());
            GL43C.glUniform1i(GL43C.glGetUniformLocation(program, "uGridOriginZ"), grid.originZ());
            GL43C.glUniform1i(GL43C.glGetUniformLocation(program, "uGridWidth"), grid.width());
            GL43C.glUniform1i(GL43C.glGetUniformLocation(program, "uGridDepth"), grid.depth());
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uHalfWidth"), (float) halfWidth);
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uHalfDepth"), (float) halfDepth);
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uCollisionMinX"), (float) (context.collisionBox().minX - context.originX()));
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uCollisionMinZ"), (float) (context.collisionBox().minZ - context.originZ()));
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uCollisionMaxX"), (float) (context.collisionBox().maxX - context.originX()));
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uCollisionMaxZ"), (float) (context.collisionBox().maxZ - context.originZ()));
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uMinimumCoverage"), (float) profile.minimumCoverage());
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uCoverageWeight"), (float) profile.coverageWeight());
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uMarginWeight"), (float) profile.marginWeight());
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uSpeedWeight"), (float) profile.speedWeight());
            GL43C.glUniform1f(GL43C.glGetUniformLocation(program, "uSwitchPenalty"), (float) profile.switchPenalty());
        }

        private GpuGrid buildGrid(PlanningContext context, double speed, int horizon) {
            int radius = Mth.ceil(Mth.clamp(
                    speed * horizon * 0.35D + GPU_GRID_MIN_RADIUS,
                    GPU_GRID_MIN_RADIUS,
                    GPU_GRID_MAX_RADIUS
            ));
            int originX = Mth.floor(context.originX()) - radius;
            int originZ = Mth.floor(context.originZ()) - radius;
            int width = radius * 2 + 1;
            int depth = width;
            float[] cells = new float[width * depth * CELL_STRIDE];
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    int blockX = originX + x;
                    int blockZ = originZ + z;
                    SupportCell support = supportAt(context, blockX, blockZ);
                    int index = (z * width + x) * CELL_STRIDE;
                    cells[index] = support.support() ? 1.0F : 0.0F;
                    cells[index + 1] = support.ice() ? 1.0F : 0.0F;
                    cells[index + 2] = (float) support.friction();
                    cells[index + 3] = isBlockedAt(context, blockX, blockZ) ? 1.0F : 0.0F;
                }
            }
            return new GpuGrid(originX, originZ, width, depth, cells);
        }

        private boolean isBlockedAt(PlanningContext context, int x, int z) {
            int minY = Mth.floor(context.collisionBox().minY);
            int maxY = Mth.floor(context.collisionBox().maxY);
            for (int y = minY; y <= maxY; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!mc.level.hasChunkAt(pos)) {
                    return true;
                }

                BlockState state = mc.level.getBlockState(pos);
                VoxelShape shape = state.getCollisionShape(mc.level, pos);
                if (shape.isEmpty()) {
                    continue;
                }

                double minShapeY = y + shape.min(Direction.Axis.Y);
                double maxShapeY = y + shape.max(Direction.Axis.Y);
                if (maxShapeY > context.collisionBox().minY && minShapeY < context.collisionBox().maxY) {
                    return true;
                }
            }
            return false;
        }

        private boolean isFirstStepSafe(
                PlanningContext context,
                SurfaceMetrics initialSurface,
                Vec3 movement,
                double angularVelocity,
                double initialTravelHeadingYaw,
                Action action
        ) {
            if (action == null) {
                return false;
            }

            AbstractBoat boat = context.boat();
            SimState initial = new SimState(
                    boat.getX(),
                    boat.getZ(),
                    movement.x,
                    movement.z,
                    boat.getYRot(),
                    angularVelocity,
                    initialTravelHeadingYaw,
                    initialSurface,
                    null,
                    lastAction,
                    0.0D
            );
            return simulateStep(context, initial, action, action).state() != null;
        }

        private boolean hasFiniteScore(double[] scores) {
            for (double score : scores) {
                if (Double.isFinite(score)) {
                    return true;
                }
            }
            return false;
        }

        private int GL11GetCurrentProgram() {
            return GL43C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        }

        private void close() {
            if (!RenderSystem.isOnRenderThread()) {
                return;
            }
            if (stateBuffer != 0) {
                GL15C.glDeleteBuffers(stateBuffer);
                stateBuffer = 0;
            }
            if (cellBuffer != 0) {
                GL15C.glDeleteBuffers(cellBuffer);
                cellBuffer = 0;
            }
            if (outputBuffer != 0) {
                GL15C.glDeleteBuffers(outputBuffer);
                outputBuffer = 0;
            }
            if (program != 0) {
                GL20C.glDeleteProgram(program);
                program = 0;
            }
            initialized = false;
        }
    }

    private record GpuGrid(
            int originX,
            int originZ,
            int width,
            int depth,
            float[] cells
    ) {
    }

    private record GpuCandidate(
            double x,
            double z,
            double velocityX,
            double velocityZ,
            double yaw,
            double angularVelocity,
            double travelHeadingYaw,
            SurfaceMetrics surface,
            Action firstAction,
            Action lastAction,
            double score,
            boolean valid
    ) {
        private SimState toState() {
            return new SimState(
                    x,
                    z,
                    velocityX,
                    velocityZ,
                    yaw,
                    angularVelocity,
                    travelHeadingYaw,
                    surface,
                    firstAction,
                    lastAction,
                    score
            );
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
            Map<Long, SupportCell> supportCache,
            Map<Long, SurfaceMetrics> surfaceCache
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
            double travelHeadingYaw,
            SurfaceMetrics surface,
            Action firstAction,
            Action lastAction,
            double score
    ) {
    }

    private record StepResult(SimState state, double failurePenalty) {
    }
}
