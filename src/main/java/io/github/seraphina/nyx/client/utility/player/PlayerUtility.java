package io.github.seraphina.nyx.client.utility.player;

import io.github.seraphina.nyx.client.utility.IMinecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class PlayerUtility implements IMinecraft {
    public static void sendMsg(String msg) {
        if (mc.player == null || mc.player.connection == null || msg == null || msg.isBlank()) {
            return;
        }

        mc.player.connection.sendChat(msg.trim());
    }

    public static void runCmd(String cmd) {
        if (mc.player == null || mc.player.connection == null || cmd == null || cmd.isBlank()) {
            return;
        }

        String command = cmd.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (!command.isBlank()) {
            mc.player.connection.sendCommand(command);
        }

    }

    public static HitResult raycastForEntity(Level level, Entity originEntity, float distance, boolean checkForBlocks) {
        Vec3 start = originEntity.getEyePosition();
        Vec3 end = originEntity.getLookAngle().normalize().scale(distance).add(start);
        return raycastForEntity(level, originEntity, start, end, checkForBlocks);
    }

    public static HitResult raycastForEntity(Level level, Entity originEntity, float distance, boolean checkForBlocks, Predicate<? super Entity> filter) {
        Vec3 start = originEntity.getEyePosition();
        Vec3 end = originEntity.getLookAngle().normalize().scale(distance).add(start);
        return raycastForEntity(level, originEntity, start, end, checkForBlocks, filter);
    }

    public static HitResult raycastForEntity(Level level, Entity originEntity, Vec3 start, Vec3 end, boolean checkForBlocks) {
        return internalRaycastForEntity(level, originEntity, start, end, checkForBlocks, 0.0f, PlayerUtility::canHitWithRaycast);
    }

    public static HitResult raycastForEntity(Level level, Entity originEntity, Vec3 start, Vec3 end, boolean checkForBlocks, Predicate<? super Entity> filter) {
        Predicate<? super Entity> raycastFilter = filter == null
                ? PlayerUtility::canHitWithRaycast
                : entity -> canHitWithRaycast(entity) && filter.test(entity);
        return internalRaycastForEntity(level, originEntity, start, end, checkForBlocks, 0.0f, raycastFilter);
    }

    private static boolean canHitWithRaycast(Entity entity) {
        return !entity.isInvulnerable();
    }

    private static HitResult internalRaycastForEntity(Level level, Entity originEntity, Vec3 start, Vec3 end, boolean checkForBlocks, float bbInflation, Predicate<? super Entity> filter) {
        BlockHitResult blockHitResult = null;
        if (checkForBlocks) {
            blockHitResult = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, originEntity));
            end = blockHitResult.getLocation();
        }
        AABB range = originEntity.getBoundingBox().expandTowards(end.subtract(start));
        ArrayList<HitResult> hits = new ArrayList<HitResult>();
        List<Entity> entities = level.getEntities(originEntity, range, filter);
        for (Entity target : entities) {
            HitResult hit = checkEntityIntersecting(target, start, end, bbInflation);
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            hits.add(hit);
        }
        if (!hits.isEmpty()) {
            hits.sort(Comparator.comparingDouble(o -> o.getLocation().distanceToSqr(start)));
            return hits.get(0);
        }
        if (checkForBlocks) {
            return blockHitResult;
        }
        return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
    }

    public static HitResult checkEntityIntersecting(Entity entity, Vec3 start, Vec3 end, float bbInflation) {
        Vec3 hitPos = null;
        if (entity.isMultipartEntity()) {
            for (PartEntity<?> p : entity.getParts()) {
                Vec3 hit = null;
                if (p != null) {
                    hit = p.getBoundingBox().inflate(bbInflation).clip(start, end).orElse(null);
                }
                if (hit == null) {
                    continue;
                }
                hitPos = hit;
                break;
            }
        } else {
            hitPos = entity.getBoundingBox().inflate(bbInflation).clip(start, end).orElse(null);
        }
        if (hitPos != null) {
            return new EntityHitResult(entity, hitPos);
        }
        return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
    }
}
