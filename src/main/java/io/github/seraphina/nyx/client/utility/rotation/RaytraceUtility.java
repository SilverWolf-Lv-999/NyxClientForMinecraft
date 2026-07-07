package io.github.seraphina.nyx.client.utility.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.List;
import java.util.Optional;

public final class RaytraceUtility {
    private static final Minecraft MC = Minecraft.getInstance();

    private RaytraceUtility() {
    }

    public static boolean canSeePointFrom(Vec3 eyes, Vec3 point) {
        if (MC.level == null || MC.player == null) {
            return false;
        }

        return MC.level.clip(new ClipContext(
                eyes,
                point,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                MC.player
        )).getType() == HitResult.Type.MISS;
    }

    public static HitResult raytrace(Vector2f rotation, double range) {
        return raytrace(rotation, range, 0);
    }

    public static HitResult raytrace(Vector2f rotation, double range, float expand) {
        return raytrace(rotation, range, expand, MC.player);
    }

    public static HitResult raytrace(Vector2f rotation, double range, float expand, Entity entity) {
        if (MC.level == null || rotation == null || entity == null) {
            return null;
        }

        Vec3 eyePos = entity.getEyePosition();
        Vec3 lookVec = Vec3.directionFromRotation(rotation.y, rotation.x);
        Vec3 endVec = eyePos.add(lookVec.scale(range));

        HitResult objectMouseOver = MC.level.clip(new ClipContext(
                eyePos,
                endVec,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                entity
        ));

        double distToBlock = range;
        if (objectMouseOver.getType() != HitResult.Type.MISS) {
            distToBlock = objectMouseOver.getLocation().distanceTo(eyePos);
        }

        Entity pointedEntity = null;
        Vec3 hitVec = null;
        double currentDist = distToBlock;
        AABB searchBox = entity.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D);
        List<Entity> entities = MC.level.getEntities(entity, searchBox, candidate -> !candidate.isSpectator() && candidate.isPickable());

        for (Entity candidate : entities) {
            float collisionSize = candidate.getPickRadius() + expand;
            AABB entityBox = candidate.getBoundingBox().inflate(collisionSize);
            Optional<Vec3> intercept = entityBox.clip(eyePos, endVec);

            if (entityBox.contains(eyePos)) {
                if (currentDist >= 0.0D) {
                    pointedEntity = candidate;
                    hitVec = intercept.orElse(eyePos);
                    currentDist = 0.0D;
                }
            } else if (intercept.isPresent()) {
                Vec3 interceptVec = intercept.get();
                double distance = eyePos.distanceTo(interceptVec);

                if (distance < currentDist || currentDist == 0.0D) {
                    if (candidate.getRootVehicle() == entity.getRootVehicle()) {
                        if (currentDist == 0.0D) {
                            pointedEntity = candidate;
                            hitVec = interceptVec;
                        }
                    } else {
                        pointedEntity = candidate;
                        hitVec = interceptVec;
                        currentDist = distance;
                    }
                }
            }
        }

        if (pointedEntity != null && (currentDist < distToBlock || objectMouseOver.getType() == HitResult.Type.MISS)) {
            return new EntityHitResult(pointedEntity, hitVec);
        }

        return objectMouseOver;
    }

    public static boolean overBlock(Vector2f rotation, Direction direction, BlockPos pos, boolean strict) {
        if (MC.level == null || MC.player == null || rotation == null) {
            return false;
        }

        Vec3 lookVec = Vec3.directionFromRotation(rotation.y, rotation.x);
        Vec3 eyePos = MC.player.getEyePosition(1.0F);
        double reach = 4.5D;
        Vec3 endVec = eyePos.add(lookVec.scale(reach));

        BlockHitResult result = MC.level.clip(new ClipContext(
                eyePos,
                endVec,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                MC.player
        ));

        if (result.getType() == HitResult.Type.MISS) {
            return false;
        }

        return result.getBlockPos().equals(pos) && (!strict || result.getDirection() == direction);
    }

    public static boolean overBlock(Vector2f rotation, BlockPos pos, boolean strict) {
        return overBlock(rotation, Direction.UP, pos, strict);
    }

    public static boolean overBlock(Vector2f rotation, BlockPos pos) {
        return overBlock(rotation, Direction.UP, pos, false);
    }

    public static boolean overBlock(Vector2f rotation, BlockPos pos, Direction direction) {
        return overBlock(rotation, direction, pos, true);
    }
}
