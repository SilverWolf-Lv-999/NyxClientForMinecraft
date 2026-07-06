package io.github.seraphina.nyxclient.utility.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector2f;

public final class RotationUtility {
    private static final Minecraft MC = Minecraft.getInstance();

    private RotationUtility() {
    }

    public static Direction getClickSide(BlockPos pos) {
        Direction bestSide = findBestDirection(pos, true);

        if (bestSide != null) {
            return bestSide;
        }

        bestSide = findBestDirection(pos, false);
        return bestSide != null ? bestSide : Direction.UP;
    }

    private static Direction findBestDirection(BlockPos pos, boolean useLineOfSight) {
        if (MC.player == null || MC.level == null) {
            return null;
        }

        Direction bestSide = null;
        double minDistanceSqr = Double.MAX_VALUE;
        Vec3 eyePos = MC.player.getEyePosition();

        for (Direction side : Direction.values()) {
            if (useLineOfSight) {
                if (!canSee(pos, side)) {
                    continue;
                }
            } else if (!isGrimDirection(pos, side)) {
                continue;
            }

            double distanceSqr = eyePos.distanceToSqr(Vec3.atCenterOf(pos.relative(side)));
            if (distanceSqr < minDistanceSqr) {
                minDistanceSqr = distanceSqr;
                bestSide = side;
            }
        }

        return bestSide;
    }

    public static boolean canSee(BlockPos pos, Direction side) {
        if (MC.player == null || MC.level == null) {
            return false;
        }

        Vec3 testVec = Vec3.atCenterOf(pos).add(
                side.getStepX() * 0.5,
                side.getStepY() * 0.5,
                side.getStepZ() * 0.5
        );
        HitResult result = MC.level.clip(new ClipContext(
                MC.player.getEyePosition(),
                testVec,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                MC.player
        ));

        return result.getType() == HitResult.Type.MISS;
    }

    public static boolean isGrimDirection(BlockPos pos, Direction direction) {
        if (MC.player == null || MC.level == null) {
            return false;
        }

        AABB combined = getCombinedBox(pos, MC.level);
        LocalPlayer player = MC.player;
        AABB eyePositions = new AABB(
                player.getX(),
                player.getY() + 0.4,
                player.getZ(),
                player.getX(),
                player.getY() + 1.62,
                player.getZ()
        ).inflate(0.0002);

        if (isIntersected(eyePositions, combined)) {
            return true;
        }

        return !switch (direction) {
            case NORTH -> eyePositions.minZ > combined.minZ;
            case SOUTH -> eyePositions.maxZ < combined.maxZ;
            case EAST -> eyePositions.maxX < combined.maxX;
            case WEST -> eyePositions.minX > combined.minX;
            case UP -> eyePositions.maxY < combined.maxY;
            case DOWN -> eyePositions.minY > combined.minY;
        };
    }

    private static boolean isIntersected(AABB box, AABB other) {
        return other.maxX - Shapes.EPSILON > box.minX
                && other.minX + Shapes.EPSILON < box.maxX
                && other.maxY - Shapes.EPSILON > box.minY
                && other.minY + Shapes.EPSILON < box.maxY
                && other.maxZ - Shapes.EPSILON > box.minZ
                && other.minZ + Shapes.EPSILON < box.maxZ;
    }

    private static AABB getCombinedBox(BlockPos pos, Level level) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos).move(pos);
        AABB combined = new AABB(pos);

        for (AABB box : shape.toAabbs()) {
            double minX = Math.max(box.minX, combined.minX);
            double minY = Math.max(box.minY, combined.minY);
            double minZ = Math.max(box.minZ, combined.minZ);
            double maxX = Math.min(box.maxX, combined.maxX);
            double maxY = Math.min(box.maxY, combined.maxY);
            double maxZ = Math.min(box.maxZ, combined.maxZ);
            combined = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return combined;
    }

    public static Direction getDirection(BlockPos blockPos) {
        if (MC.player == null || MC.level == null) {
            return Direction.UP;
        }

        double eyePos = MC.player.getY() + MC.player.getEyeHeight(MC.player.getPose());
        VoxelShape outline = MC.level.getBlockState(blockPos).getCollisionShape(MC.level, blockPos);

        if (eyePos > blockPos.getY() + outline.max(Direction.Axis.Y) && MC.level.getBlockState(blockPos.above()).canBeReplaced()) {
            return Direction.UP;
        }

        if (eyePos < blockPos.getY() + outline.min(Direction.Axis.Y) && MC.level.getBlockState(blockPos.below()).canBeReplaced()) {
            return Direction.DOWN;
        }

        BlockPos difference = blockPos.subtract(MC.player.blockPosition());

        if (Math.abs(difference.getX()) > Math.abs(difference.getZ())) {
            return difference.getX() > 0 ? Direction.WEST : Direction.EAST;
        }

        return difference.getZ() > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    public static boolean isInFov(LivingEntity entity, float fov) {
        if (MC.player == null || entity == null) {
            return false;
        }

        if (fov >= 360.0F) {
            return true;
        }

        float yawDiff = Math.abs(Mth.wrapDegrees(getRotationsToEntity(entity).x - MC.player.getYRot()));
        return yawDiff <= fov / 2.0F;
    }

    public static Vector2f getRotationsToEntity(LivingEntity entity) {
        if (MC.player == null || entity == null) {
            return new Vector2f();
        }

        Vec3 eyePos = MC.player.getEyePosition();
        Vec3 targetPos = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, distance));

        return new Vector2f(yaw, Mth.clamp(pitch, -90, 90));
    }

    public static double getEyeDistanceToEntity(LivingEntity entity) {
        if (MC.player == null || entity == null) {
            return Double.MAX_VALUE;
        }

        Vec3 eyePos = MC.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        double dx = Math.max(box.minX - eyePos.x, Math.max(0, eyePos.x - box.maxX));
        double dy = Math.max(box.minY - eyePos.y, Math.max(0, eyePos.y - box.maxY));
        double dz = Math.max(box.minZ - eyePos.z, Math.max(0, eyePos.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static Vector2f calculate(Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        double distance = Math.hypot(diff.x, diff.z);
        float yaw = (float) Math.toDegrees(Mth.atan2(diff.z, diff.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Mth.atan2(diff.y, distance));
        return new Vector2f(yaw, pitch);
    }

    public static Vector2f calculate(Entity entity) {
        if (MC.player == null || entity == null) {
            return new Vector2f();
        }

        return calculate(entity.position().add(0, Mth.clamp(
                MC.player.getY() - entity.getY() + MC.player.getEyeHeight(),
                0.0,
                (entity.getBoundingBox().maxY - entity.getBoundingBox().minY) * 0.9
        ), 0));
    }

    public static Vector2f calculate(Entity entity, boolean adaptive, double range) {
        Vector2f normalRotations = calculate(entity);
        HitResult result = RaytraceUtility.raytrace(normalRotations, range, 0.0F);

        if (!adaptive || (result != null && result.getType() == HitResult.Type.ENTITY)) {
            return normalRotations;
        }

        AABB box = entity.getBoundingBox();
        Vec3 basePos = entity.position();

        for (double yPercent = 1; yPercent >= 0; yPercent -= 0.25 + Math.random() * 0.1) {
            for (double xPercent = 1; xPercent >= -0.5; xPercent -= 0.5) {
                for (double zPercent = 1; zPercent >= -0.5; zPercent -= 0.5) {
                    double offsetX = (box.maxX - box.minX) * xPercent;
                    double offsetY = (box.maxY - box.minY) * yPercent;
                    double offsetZ = (box.maxZ - box.minZ) * zPercent;
                    Vec3 targetPoint = basePos.add(offsetX, offsetY, offsetZ);
                    Vector2f adaptiveRotations = calculate(targetPoint);
                    HitResult rayCastResult = RaytraceUtility.raytrace(adaptiveRotations, range, 0.0F);

                    if (rayCastResult != null && rayCastResult.getType() == HitResult.Type.ENTITY) {
                        return adaptiveRotations;
                    }
                }
            }
        }

        return normalRotations;
    }

    public static Vector2f calculate(BlockPos to) {
        if (MC.player == null) {
            return new Vector2f();
        }

        return calculate(MC.player.getEyePosition(), Vec3.atCenterOf(to));
    }

    public static Vector2f calculate(Vec3 to) {
        if (MC.player == null) {
            return new Vector2f();
        }

        return calculate(MC.player.getEyePosition(), to);
    }

    public static Vector2f calculate(Vec3 position, Direction direction) {
        double x = position.x + 0.5D + direction.getStepX() * 0.5D;
        double y = position.y + 0.5D + direction.getStepY() * 0.5D;
        double z = position.z + 0.5D + direction.getStepZ() * 0.5D;
        return calculate(new Vec3(x, y, z));
    }

    public static Vector2f calculate(BlockPos position, Direction direction) {
        double x = position.getX() + 0.5D + direction.getStepX() * 0.5D;
        double y = position.getY() + 0.5D + direction.getStepY() * 0.5D;
        double z = position.getZ() + 0.5D + direction.getStepZ() * 0.5D;
        return calculate(new Vec3(x, y, z));
    }

    public static Vector2f applySensitivityPatch(Vector2f rotation) {
        return applySensitivityPatch(rotation, getPlayerRotation());
    }

    public static Vector2f applySensitivityPatch(Vector2f rotation, Vector2f previousRotation) {
        if (MC.options == null) {
            return new Vector2f(rotation);
        }

        float mouseSensitivity = (float) (MC.options.sensitivity().get() * (1 + Math.random() / 10000000) * 0.6F + 0.2F);
        double multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F * 0.15D;
        float yaw = previousRotation.x + (float) (Math.round((rotation.x - previousRotation.x) / multiplier) * multiplier);
        float pitch = previousRotation.y + (float) (Math.round((rotation.y - previousRotation.y) / multiplier) * multiplier);
        return new Vector2f(yaw, Mth.clamp(pitch, -90, 90));
    }

    public static Vector2f relateToPlayerRotation(Vector2f rotation) {
        Vector2f previousRotation = getPlayerRotation();
        float yaw = previousRotation.x + Mth.wrapDegrees(rotation.x - previousRotation.x);
        float pitch = Mth.clamp(rotation.y, -90, 90);
        return new Vector2f(yaw, pitch);
    }

    public static Vector2f resetRotation(Vector2f rotation) {
        if (rotation == null || MC.player == null) {
            return null;
        }

        float yaw = rotation.x + Mth.wrapDegrees(MC.player.getYRot() - rotation.x);
        float pitch = MC.player.getXRot();
        return new Vector2f(yaw, pitch);
    }

    public static Vector2f move(Vector2f targetRotation, double speed) {
        return move(getPlayerRotation(), targetRotation, speed);
    }

    public static Vector2f move(Vector2f lastRotation, Vector2f targetRotation, double speed) {
        if (speed == 0 || lastRotation == null || targetRotation == null) {
            return new Vector2f();
        }

        double deltaYaw = Mth.wrapDegrees(targetRotation.x - lastRotation.x);
        double deltaPitch = targetRotation.y - lastRotation.y;
        double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);

        if (distance == 0) {
            return new Vector2f();
        }

        double distributionYaw = Math.abs(deltaYaw / distance);
        double distributionPitch = Math.abs(deltaPitch / distance);
        double maxYaw = speed * distributionYaw;
        double maxPitch = speed * distributionPitch;
        float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        return new Vector2f(moveYaw, movePitch);
    }

    public static Vector2f smooth(Vector2f targetRotation, double speed) {
        return smooth(getPlayerRotation(), targetRotation, speed);
    }

    public static Vector2f smooth(Vector2f lastRotation, Vector2f targetRotation, double speed) {
        if (targetRotation == null) {
            return new Vector2f();
        }

        float yaw = targetRotation.x;
        float pitch = targetRotation.y;

        if (speed != 0) {
            Vector2f move = move(lastRotation, targetRotation, speed);
            yaw = lastRotation.x + move.x;
            pitch = lastRotation.y + move.y;

            int iterations = MC.getFps() <= 0 ? 1 : Math.max(1, (int) (MC.getFps() / 20.0F + Math.random() * 10));
            for (int i = 1; i <= iterations; ++i) {
                if (Math.abs(move.x) + Math.abs(move.y) > 0.0001) {
                    yaw += (float) ((Math.random() - 0.5) / 1000);
                    pitch -= (float) (Math.random() / 200);
                }

                Vector2f fixedRotations = applySensitivityPatch(new Vector2f(yaw, pitch), lastRotation);
                yaw = fixedRotations.x;
                pitch = fixedRotations.y;
            }
        }

        return new Vector2f(yaw, pitch);
    }

    private static Vector2f getPlayerRotation() {
        if (MC.player == null) {
            return new Vector2f();
        }

        return new Vector2f(MC.player.getYRot(), MC.player.getXRot());
    }
}
