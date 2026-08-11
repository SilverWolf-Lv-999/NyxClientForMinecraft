package io.github.seraphina.nyx.client.utility.world;

import io.github.seraphina.nyx.client.utility.IMinecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class HoleUtility implements IMinecraft {
    private HoleUtility() {
    }

    public static Hole findNearestHole(double range, boolean includeDoubleHoles, boolean anyBlock, boolean up) {
        if (mc.player == null || mc.level == null || range <= 0.0D) {
            return null;
        }

        BlockPos playerBlockPos = mc.player.blockPosition();
        int radius = (int) Math.ceil(range);
        Hole nearestHole = null;
        double nearestDistanceSqr = range * range;

        for (int x = playerBlockPos.getX() - radius; x <= playerBlockPos.getX() + radius; x++) {
            for (int y = playerBlockPos.getY() - radius; y <= playerBlockPos.getY() + radius; y++) {
                for (int z = playerBlockPos.getZ() - radius; z <= playerBlockPos.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    double distanceSqr = mc.player.position().distanceToSqr(Vec3.atCenterOf(pos));
                    if (distanceSqr > range * range || !canSelect(pos, playerBlockPos, up)) {
                        continue;
                    }

                    Hole hole = getHole(pos, includeDoubleHoles, anyBlock);
                    if (hole != null && distanceSqr < nearestDistanceSqr) {
                        nearestHole = hole;
                        nearestDistanceSqr = distanceSqr;
                    }
                }
            }
        }

        return nearestHole;
    }

    public static Hole getHole(BlockPos pos, boolean includeDoubleHoles, boolean anyBlock) {
        if (mc.level == null || pos == null) {
            return null;
        }

        if (isSingleHole(pos, anyBlock)) {
            return new Hole(pos.immutable(), null);
        }

        if (!includeDoubleHoles) {
            return null;
        }

        Direction direction = getDoubleHoleDirection(pos, anyBlock);
        return direction != null && getDoubleHoleDirection(pos.relative(direction), anyBlock) != null
                ? new Hole(pos.immutable(), direction)
                : null;
    }

    public static boolean isSingleHole(BlockPos pos, boolean anyBlock) {
        if (!hasAirColumn(pos) || !isHard(pos.below())) {
            return false;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!isWall(pos.relative(direction), anyBlock)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isDoubleHole(BlockPos pos, boolean anyBlock) {
        Direction direction = getDoubleHoleDirection(pos, anyBlock);
        return direction != null && getDoubleHoleDirection(pos.relative(direction), anyBlock) != null;
    }

    public static Direction getDoubleHoleDirection(BlockPos pos, boolean anyBlock) {
        if (!hasAirColumn(pos) || !isHard(pos.below())) {
            return null;
        }

        int wallCount = 0;
        Direction opening = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(direction);
            if (isWall(adjacent, anyBlock)) {
                wallCount++;
                continue;
            }

            if (opening != null || !hasAirColumn(adjacent)) {
                return null;
            }
            opening = direction;
        }

        return wallCount == 3 ? opening : null;
    }

    public static boolean isHard(BlockPos pos) {
        return mc.level != null && pos != null && isHard(mc.level.getBlockState(pos).getBlock());
    }

    public static boolean isHard(Block block) {
        return block == Blocks.BEDROCK
                || block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.ENDER_CHEST
                || block == Blocks.RESPAWN_ANCHOR;
    }

    private static boolean canSelect(BlockPos pos, BlockPos playerBlockPos, boolean up) {
        boolean differentColumn = pos.getX() != playerBlockPos.getX() || pos.getZ() != playerBlockPos.getZ();
        if (differentColumn && !up && pos.getY() + 1 > mc.player.getY()) {
            return false;
        }

        return pos.getY() - playerBlockPos.getY() <= 1;
    }

    private static boolean hasAirColumn(BlockPos pos) {
        return mc.level.getBlockState(pos).isAir()
                && mc.level.getBlockState(pos.above()).isAir()
                && mc.level.getBlockState(pos.above(2)).isAir();
    }

    private static boolean isWall(BlockPos pos, boolean anyBlock) {
        return anyBlock ? !mc.level.getBlockState(pos).isAir() : isHard(pos);
    }

    public record Hole(BlockPos pos, Direction doubleHoleDirection) {
        public Vec3 centerAt(double y) {
            Vec3 center = new Vec3(pos.getX() + 0.5D, y, pos.getZ() + 0.5D);
            if (doubleHoleDirection == null) {
                return center;
            }

            return center.add(
                    doubleHoleDirection.getStepX() * 0.5D,
                    doubleHoleDirection.getStepY() * 0.5D,
                    doubleHoleDirection.getStepZ() * 0.5D
            );
        }
    }
}
