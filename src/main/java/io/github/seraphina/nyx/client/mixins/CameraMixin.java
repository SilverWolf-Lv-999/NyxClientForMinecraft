package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.ViewClip;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void getMaxZoom(float startingDistance, CallbackInfoReturnable<Float> info) {
        ViewClip viewClip = ViewClip.INSTANCE;
        if (viewClip.isEnabled()) {
            info.setReturnValue(viewClip.getCameraDistance(startingDistance));
        }
    }
}
