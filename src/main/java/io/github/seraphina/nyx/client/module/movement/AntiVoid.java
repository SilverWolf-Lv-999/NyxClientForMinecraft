package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.antivoid.name", description = "nyxclient.module.antivoid.description", category = Category.MOVEMENT)
public class AntiVoid extends Module {
    public static final AntiVoid INSTANCE = new AntiVoid();

    public final DoubleValue voidHeight = ValueBuild.doubleSetting("void height", -64.0D, -64.0D, 319.0D, 1.0D, this);
    public final DoubleValue height = ValueBuild.doubleSetting("height", 100.0D, -40.0D, 256.0D, 1.0D, this);

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canCheckVoid()) {
            return;
        }

        if (mc.player.getY() < voidHeight.getValue() + height.getValue() && isOverVoid()) {
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x, 0.0D, velocity.z);
        }
    }

    private boolean canCheckVoid() {
        return mc.player != null
                && mc.level != null
                && !mc.player.getAbilities().flying
                && !mc.player.isSpectator()
                && !mc.player.isFallFlying()
                && !mc.player.isPassenger()
                && !mc.player.isInWater()
                && !mc.player.isInLava();
    }

    private boolean isOverVoid() {
        int minY = (int) Math.floor(voidHeight.getValue()) - 1;
        int playerY = (int) Math.floor(mc.player.getY());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = playerY; y > minY; y--) {
            pos.set(mc.player.getX(), y, mc.player.getZ());
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir() && state.getBlock() != Blocks.VOID_AIR) {
                return false;
            }
        }

        return true;
    }
}
