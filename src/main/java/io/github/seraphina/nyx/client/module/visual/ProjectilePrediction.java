package io.github.seraphina.nyx.client.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "nyxclient.module.projectileprediction.name", description = "nyxclient.module.projectileprediction.description", category = Category.VISUAL)
public class ProjectilePrediction extends Module {
    public static final ProjectilePrediction INSTANCE = new ProjectilePrediction();

    private static final int MAX_TICKS = 240;
    private static final double MIN_SEGMENT_LENGTH_SQR = 1.0E-7D;
    private static final double FIRST_PERSON_FORWARD_OFFSET = 0.35D;
    private static final double FIRST_PERSON_SIDE_OFFSET = 0.34D;
    private static final double FIRST_PERSON_DOWN_OFFSET = 0.34D;
    private static final double THIRD_PERSON_FORWARD_OFFSET = 0.16D;
    private static final double THIRD_PERSON_SIDE_OFFSET = 0.16D;
    private static final double LANDING_MARKER_SIZE = 0.28D;

    public final BoolValue trajectory = ValueBuild.boolSetting("trajectory", true, this);
    public final BoolValue landing = ValueBuild.boolSetting("landing", true, this);
    public final BoolValue noDepth = ValueBuild.boolSetting("no depth", true, this);
    public final BoolValue bows = ValueBuild.boolSetting("bows", true, this);
    public final BoolValue crossbows = ValueBuild.boolSetting("crossbows", true, this);
    public final IntValue points = ValueBuild.intSetting("points", 120, 20, MAX_TICKS, 5, this);
    public final DoubleValue lineWidth = ValueBuild.doubleSetting("line width", 1.5D, 1.0D, 4.0D, 0.1D, this);
    public final IntValue trajectoryAlpha = ValueBuild.intSetting("trajectory alpha", 220, 0, 255, 5, trajectory::getValue, this);
    public final IntValue landingAlpha = ValueBuild.intSetting("landing alpha", 230, 0, 255, 5, landing::getValue, this);
    public final ColorValue color = ValueBuild.colorSetting("color", new Color(84, 170, 255), false, this);

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null || mc.player.isSpectator()) {
            return;
        }
        if ((!trajectory.getValue() || trajectoryAlpha.getValue() <= 0) && (!landing.getValue() || landingAlpha.getValue() <= 0)) {
            return;
        }

        PredictionInput input = findPredictionInput(mc.player, event.getPartialTick());
        if (input == null) {
            return;
        }

        PredictionResult result = predict(input);
        if (result.lines.isEmpty() && result.impact == null) {
            return;
        }

        renderPrediction(event.getPoseStack(), result);
    }

    private PredictionInput findPredictionInput(LocalPlayer player, float partialTick) {
        InteractionHand hand = activeHand(player);
        PredictionType type = predictionType(player.getItemInHand(hand), player);
        if (type == null && hand == InteractionHand.MAIN_HAND) {
            hand = InteractionHand.OFF_HAND;
            type = predictionType(player.getItemInHand(hand), player);
        }
        if (type == null) {
            return null;
        }

        Vec3 origin = origin(player, hand, partialTick);
        Vec3 velocity = initialVelocity(player, type);
        if (velocity.lengthSqr() <= MIN_SEGMENT_LENGTH_SQR) {
            return null;
        }
        return new PredictionInput(type, origin, velocity);
    }

    private InteractionHand activeHand(LocalPlayer player) {
        if (player.isUsingItem()) {
            return player.getUsedItemHand();
        }
        return InteractionHand.MAIN_HAND;
    }

    private PredictionType predictionType(ItemStack stack, LocalPlayer player) {
        if (stack == null || stack.isEmpty() || !stack.isItemEnabled(mc.level.enabledFeatures())) {
            return null;
        }

        Item item = stack.getItem();
        if (stack.is(Items.ENDER_PEARL) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) {
            return new PredictionType(1.5D, 0.03D, 0.99D, 0.8D, 0.0F, false);
        }
        if (stack.is(Items.WIND_CHARGE)) {
            return new PredictionType(1.5D, 0.0D, 1.0D, 1.0D, 0.0F, false);
        }
        if (item instanceof ThrowablePotionItem) {
            return new PredictionType(0.5D, 0.05D, 0.99D, 0.8D, -20.0F, false);
        }
        if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            return new PredictionType(0.7D, 0.07D, 0.99D, 0.8D, -20.0F, false);
        }
        if (item instanceof TridentItem && player.isUsingItem() && player.getUseItem() == stack && player.getTicksUsingItem() >= TridentItem.THROW_THRESHOLD_TIME) {
            return new PredictionType(2.5D, 0.05D, 0.99D, 0.99D, 0.0F, true);
        }
        if (bows.getValue() && item instanceof BowItem && player.isUsingItem() && player.getUseItem() == stack) {
            float power = BowItem.getPowerForTime(player.getTicksUsingItem());
            if (power >= 0.1F) {
                return new PredictionType(power * 3.0D, 0.05D, 0.99D, 0.6D, 0.0F, true);
            }
        }
        if (crossbows.getValue() && item instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return new PredictionType(3.15D, 0.05D, 0.99D, 0.6D, 0.0F, true);
        }
        return null;
    }

    private Vec3 initialVelocity(LocalPlayer player, PredictionType type) {
        float pitch = player.getXRot();
        float yaw = player.getYRot();
        float pitchOffset = type.pitchOffset;
        double x = -Mth.sin(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
        double y = -Mth.sin((pitch + pitchOffset) * Mth.DEG_TO_RAD);
        double z = Mth.cos(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
        Vec3 velocity = new Vec3(x, y, z).normalize().scale(type.power);
        Vec3 playerVelocity = player.getKnownMovement();
        return velocity.add(playerVelocity.x, player.onGround() ? 0.0D : playerVelocity.y, playerVelocity.z);
    }

    private Vec3 origin(LocalPlayer player, InteractionHand hand, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 view = player.getViewVector(partialTick).normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = view.cross(up).normalize();
        if (right.lengthSqr() <= MIN_SEGMENT_LENGTH_SQR) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }

        double handSign = handSide(player, hand);
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            return eye.add(view.scale(FIRST_PERSON_FORWARD_OFFSET))
                    .add(right.scale(FIRST_PERSON_SIDE_OFFSET * handSign))
                    .add(0.0D, -FIRST_PERSON_DOWN_OFFSET, 0.0D);
        }
        return eye.add(view.scale(THIRD_PERSON_FORWARD_OFFSET))
                .add(right.scale(THIRD_PERSON_SIDE_OFFSET * handSign))
                .add(0.0D, -0.1D, 0.0D);
    }

    private double handSide(LocalPlayer player, InteractionHand hand) {
        HumanoidArm mainArm = player.getMainArm();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
        return arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
    }

    private PredictionResult predict(PredictionInput input) {
        List<Render3DUtility.LineSegment> lines = new ArrayList<>();
        Vec3 position = input.origin;
        Vec3 velocity = input.velocity;
        Vec3 impact = null;

        int maxPoints = points.getValue();
        for (int tick = 0; tick < maxPoints; tick++) {
            Vec3 next = position.add(velocity);
            BlockHitResult hit = mc.level.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 hitLocation = hit.getLocation();
                addLine(lines, position, hitLocation);
                impact = input.type.offsetImpact ? offsetImpact(hitLocation, hit.getDirection()) : hitLocation;
                break;
            }

            addLine(lines, position, next);
            position = next;
            velocity = velocity.scale(input.type.inertia);
            if (!mc.level.getFluidState(BlockPos.containing(position)).isEmpty()) {
                velocity = velocity.scale(input.type.liquidInertia / input.type.inertia);
            }
            if (input.type.gravity > 0.0D) {
                velocity = velocity.add(0.0D, -input.type.gravity, 0.0D);
            }
        }

        return new PredictionResult(lines, impact);
    }

    private void addLine(List<Render3DUtility.LineSegment> lines, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) <= MIN_SEGMENT_LENGTH_SQR) {
            return;
        }
        lines.add(new Render3DUtility.LineSegment(from.x, from.y, from.z, to.x, to.y, to.z));
    }

    private Vec3 offsetImpact(Vec3 impact, Direction direction) {
        Vec3 normal = direction.getUnitVec3();
        return impact.add(normal.scale(0.01D));
    }

    private void renderPrediction(PoseStack poseStack, PredictionResult result) {
        Color base = color.getValue();
        int rgb = Render3DUtility.rgb(base.getRed(), base.getGreen(), base.getBlue());

        if (trajectory.getValue() && trajectoryAlpha.getValue() > 0 && !result.lines.isEmpty()) {
            int lineColor = Render3DUtility.withAlpha(rgb, trajectoryAlpha.getValue());
            if (noDepth.getValue()) {
                Render3DUtility.renderLineSegmentsNoDepth(poseStack, result.lines, lineColor, lineWidth.getValue().floatValue());
            } else {
                for (Render3DUtility.LineSegment line : result.lines) {
                    Render3DUtility.renderLine(poseStack, line.fromX(), line.fromY(), line.fromZ(), line.toX(), line.toY(), line.toZ(), lineColor);
                }
            }
        }

        if (landing.getValue() && landingAlpha.getValue() > 0 && result.impact != null) {
            int markerColor = Render3DUtility.withAlpha(rgb, landingAlpha.getValue());
            Render3DUtility.renderCross(poseStack, result.impact, LANDING_MARKER_SIZE, markerColor);
            AABB marker = new AABB(result.impact, result.impact).inflate(LANDING_MARKER_SIZE * 0.35D);
            if (noDepth.getValue()) {
                Render3DUtility.renderOutlineBoxNoDepth(poseStack, marker, markerColor);
            } else {
                Render3DUtility.renderOutlineBox(poseStack, marker, markerColor);
            }
        }
    }

    private record PredictionType(
            double power,
            double gravity,
            double inertia,
            double liquidInertia,
            float pitchOffset,
            boolean offsetImpact
    ) {
    }

    private record PredictionInput(PredictionType type, Vec3 origin, Vec3 velocity) {
    }

    private record PredictionResult(List<Render3DUtility.LineSegment> lines, Vec3 impact) {
    }
}
