package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.movement.NoWeb;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WebBlock.class)
public class WebBlockMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void nyx$disableWebSlowdown(
            BlockState state,
            Level level,
            BlockPos position,
            Entity entity,
            InsideBlockEffectApplier effectApplier,
            boolean shouldApplyEffect,
            CallbackInfo info
    ) {
        if (entity == Minecraft.getInstance().player && NoWeb.INSTANCE.isEnabled()) {
            info.cancel();
        }
    }
}
