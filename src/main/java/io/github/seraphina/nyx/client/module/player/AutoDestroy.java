package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.bus.EventHandler;
import io.github.seraphina.nyx.client.events.bus.EventPriority;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.combat.AnchorAura;
import io.github.seraphina.nyx.client.module.combat.CrystalAura;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

@ModuleInfo(
        name = "nyxclient.module.autodestroy.name",
        description = "nyxclient.module.autodestroy.description",
        category = Category.PLAYER
)
public class AutoDestroy extends Module {
    public static final AutoDestroy INSTANCE = new AutoDestroy();

    private BlockPos destroyingPosition;
    private Direction destroyingDirection;

    @Override
    public void onDisable() {
        stopDestroying();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canRun()) {
            stopDestroying();
            return;
        }

        MiningTarget target = findTarget();
        if (PacketMine.INSTANCE.isEnabled()) {
            stopDestroying();
            if (target != null && !hasLivePacketMineTarget()) {
                PacketMine.INSTANCE.mine(target.pos());
            }
            return;
        }

        if (target == null) {
            stopDestroying();
            return;
        }

        Direction direction = getDestroyDirection(target);
        if (direction == null) {
            stopDestroying();
            return;
        }

        if (!target.pos().equals(destroyingPosition)) {
            stopDestroying();
            startDestroying(target.pos(), direction);
            return;
        }

        if (!mc.gameMode.continueDestroyBlock(destroyingPosition, destroyingDirection)
                || !isBreakable(destroyingPosition)) {
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

    private MiningTarget findTarget() {
        BlockPos crystalTarget = CrystalAura.INSTANCE.findAutoDestroyPosition();
        if (crystalTarget != null) {
            return new MiningTarget(crystalTarget, CrystalAura.INSTANCE.wallSelect.getValue());
        }

        BlockPos anchorTarget = AnchorAura.INSTANCE.findAutoDestroyPosition();
        if (anchorTarget != null) {
            return new MiningTarget(anchorTarget, AnchorAura.INSTANCE.wallSelect.getValue());
        }

        return null;
    }

    private Direction getDestroyDirection(MiningTarget target) {
        Direction direction = RotationUtility.getClickSide(target.pos());
        if (!target.wallBypass()
                && (!RotationUtility.isGrimDirection(target.pos(), direction) || !RotationUtility.canSee(target.pos(), direction))) {
            return null;
        }

        return direction;
    }

    private boolean isBreakable(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir()
                && !state.canBeReplaced()
                && state.getDestroySpeed(mc.level, pos) >= 0.0F;
    }

    private boolean hasLivePacketMineTarget() {
        BlockPos target = PacketMine.targetPos;
        return target != null && isBreakable(target);
    }

    private void startDestroying(BlockPos pos, Direction direction) {
        if (!mc.gameMode.startDestroyBlock(pos, direction)) {
            return;
        }

        destroyingPosition = pos;
        destroyingDirection = direction;
    }

    private void stopDestroying() {
        if (destroyingPosition != null && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }

        destroyingPosition = null;
        destroyingDirection = null;
    }

    private record MiningTarget(BlockPos pos, boolean wallBypass) {
    }
}
