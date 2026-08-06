package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.XRay;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void nyx$filterXRayFaces(
            BlockState state,
            BlockState adjacentState,
            Direction direction,
            CallbackInfoReturnable<Boolean> callback
    ) {
        XRay xRay = XRay.INSTANCE;
        if (xRay.isEnabled()) {
            callback.setReturnValue(xRay.isVisible(state));
        }
    }
}
