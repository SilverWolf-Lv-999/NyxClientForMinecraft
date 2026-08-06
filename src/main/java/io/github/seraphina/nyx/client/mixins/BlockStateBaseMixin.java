package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.XRay;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateBaseMixin {
    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
    private void nyx$brightenXRayBlocks(CallbackInfoReturnable<Integer> callback) {
        XRay xRay = XRay.INSTANCE;
        if (xRay.isEnabled() && xRay.isVisible((BlockState)(Object)this)) {
            callback.setReturnValue(15);
        }
    }
}
