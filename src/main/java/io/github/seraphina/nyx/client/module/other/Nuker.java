package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.player.PacketMine;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

@ModuleInfo(name = "nyxclient.module.nuker.name", description = "nyxclient.module.nuker.description", category = Category.OTHER)
public class Nuker extends Module {
    public static final Nuker INSTANCE = new Nuker();

    public final DoubleValue range = ValueBuild.doubleSetting("range", 4.0D, 0.0D, 8.0D, 0.1D, this);
    public final BoolValue down = ValueBuild.boolSetting("down", false, this);

    private BlockPos destroyingPosition;
    private Direction destroyingDirection;

    @Override
    public void onDisable() {
        stopDestroying();
    }

    @EventTarget
    public void onTick(TickEvent.Pre event) {
        if (!canRun()) {
            stopDestroying();
            return;
        }

        BlockPos target = findTarget();
        if (PacketMine.INSTANCE.isEnabled()) {
            stopDestroying();
            if (target != null && !hasLivePacketMineTarget()) {
                PacketMine.INSTANCE.mine(target);
            }
            return;
        }

        if (target == null) {
            stopDestroying();
            return;
        }

        if (!target.equals(destroyingPosition)) {
            stopDestroying();
            startDestroying(target);
            return;
        }

        if (!mc.gameMode.continueDestroyBlock(destroyingPosition, destroyingDirection) || !isBreakable(destroyingPosition)) {
            destroyingPosition = null;
            destroyingDirection = null;
        }
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator();
    }

    private BlockPos findTarget() {
        double scanRange = range.getValue();
        double maxDistanceSqr = scanRange * scanRange;
        int blockRange = (int)Math.ceil(scanRange);
        BlockPos playerPosition = mc.player.blockPosition();
        BlockPos closestAbove = null;
        BlockPos closestBelow = null;
        double closestAboveDistanceSqr = Double.MAX_VALUE;
        double closestBelowDistanceSqr = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                playerPosition.offset(-blockRange, -blockRange, -blockRange),
                playerPosition.offset(blockRange, blockRange, blockRange)
        )) {
            if (!isBreakable(pos)) {
                continue;
            }

            double distanceSqr = mc.player.getEyePosition().distanceToSqr(pos.getCenter());
            if (distanceSqr > maxDistanceSqr) {
                continue;
            }

            if (pos.getY() < mc.player.getY()) {
                if (down.getValue() && distanceSqr < closestBelowDistanceSqr) {
                    closestBelow = pos.immutable();
                    closestBelowDistanceSqr = distanceSqr;
                }
            } else if (distanceSqr < closestAboveDistanceSqr) {
                closestAbove = pos.immutable();
                closestAboveDistanceSqr = distanceSqr;
            }
        }

        return closestAbove != null ? closestAbove : closestBelow;
    }

    private boolean isBreakable(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(mc.level, pos) >= 0.0F;
    }

    private boolean hasLivePacketMineTarget() {
        BlockPos target = PacketMine.targetPos;
        return target != null && isBreakable(target);
    }

    private void startDestroying(BlockPos pos) {
        Direction direction = RotationUtility.getClickSide(pos);
        if (mc.gameMode.startDestroyBlock(pos, direction)) {
            destroyingPosition = pos;
            destroyingDirection = direction;
        }
    }

    private void stopDestroying() {
        if (destroyingPosition != null && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }

        destroyingPosition = null;
        destroyingDirection = null;
    }
}
