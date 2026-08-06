package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.movement.NoWeb;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WebBlock.class)
public class WebBlockMixin {
    @Redirect(
            method = "entityInside",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V"
            )
    )
    private void nyx$adjustWebSlowdown(Entity entity, BlockState state, Vec3 vanillaMultiplier) {
        NoWeb noWeb = NoWeb.INSTANCE;
        if (entity != Minecraft.getInstance().player || !noWeb.isEnabled()) {
            entity.makeStuckInBlock(state, vanillaMultiplier);
            return;
        }

        if (noWeb.isGrim()) {
            double speedMultiplier = NoWeb.GRIM_WEB_SPEED_MULTIPLIER;
            entity.makeStuckInBlock(state, new Vec3(speedMultiplier, speedMultiplier, speedMultiplier));
        }

        if (!noWeb.isVanilla() && !noWeb.isGrim()) {
            entity.makeStuckInBlock(state, vanillaMultiplier);
        }
    }
}
