package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

@ModuleInfo(
        name = "nyxclient.module.bedbreaker.name",
        description = "nyxclient.module.bedbreaker.description",
        category = Category.PLAYER
)
public class BedBreaker extends Module {
    public static final BedBreaker INSTANCE = new BedBreaker();

    public final IntValue range = ValueBuild.intSetting("range", 6, 1, 6, 1, this);
    public final BoolValue swing = ValueBuild.boolSetting("swing", true, this);

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

        BlockPos target = findClosestBed();
        if (target == null) {
            stopDestroying();
            return;
        }

        if (!target.equals(destroyingPosition)) {
            stopDestroying();
            startDestroying(target);
            return;
        }

        if (!mc.gameMode.continueDestroyBlock(destroyingPosition, destroyingDirection)
                || !isBed(destroyingPosition)) {
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

    private BlockPos findClosestBed() {
        int scanRange = range.getValue();
        BlockPos playerPosition = mc.player.blockPosition();
        double maxDistanceSqr = scanRange * (double) scanRange;
        BlockPos closestBed = null;
        double closestDistanceSqr = Double.MAX_VALUE;

        for (BlockPos mutablePos : BlockPos.betweenClosed(
                playerPosition.offset(-scanRange, -scanRange, -scanRange),
                playerPosition.offset(scanRange, scanRange, scanRange)
        )) {
            if (!isBed(mutablePos)) {
                continue;
            }

            double distanceSqr = mc.player.getEyePosition().distanceToSqr(mutablePos.getCenter());
            if (distanceSqr <= maxDistanceSqr && distanceSqr < closestDistanceSqr) {
                closestBed = mutablePos.immutable();
                closestDistanceSqr = distanceSqr;
            }
        }

        return closestBed;
    }

    private boolean isBed(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.is(BlockTags.BEDS);
    }

    private void startDestroying(BlockPos pos) {
        destroyingDirection = RotationUtility.getClickSide(pos);
        if (!mc.gameMode.startDestroyBlock(pos, destroyingDirection)) {
            destroyingDirection = null;
            return;
        }

        destroyingPosition = pos;
        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
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
