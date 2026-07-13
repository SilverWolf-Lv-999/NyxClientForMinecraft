package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.ViewClip;
import io.github.seraphina.nyx.client.module.visual.MotionCamera;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyArgs(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"
            )
    )
    private void nyx$setMotionCameraPosition(Args args) {
        MotionCamera motionCamera = MotionCamera.INSTANCE;
        if (motionCamera.shouldApply()) {
            args.set(0, motionCamera.getFakeX());
            args.set(1, motionCamera.getFakeY());
            args.set(2, motionCamera.getFakeZ());
        }
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void getMaxZoom(float startingDistance, CallbackInfoReturnable<Float> info) {
        ViewClip viewClip = ViewClip.INSTANCE;
        if (viewClip.isEnabled()) {
            info.setReturnValue(viewClip.getCameraDistance(startingDistance));
        }
    }
}
