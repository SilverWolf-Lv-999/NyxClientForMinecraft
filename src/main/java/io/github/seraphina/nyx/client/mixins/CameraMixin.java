package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.ViewClip;
import io.github.seraphina.nyx.client.module.visual.MotionCamera;
import io.github.seraphina.nyx.client.module.player.FreeLook;
import io.github.seraphina.nyx.client.module.player.Freecam;
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
        Freecam freecam = Freecam.INSTANCE;
        if (freecam.shouldApply()) {
            args.set(0, freecam.getFakeX());
            args.set(1, freecam.getFakeY());
            args.set(2, freecam.getFakeZ());
            return;
        }

        MotionCamera motionCamera = MotionCamera.INSTANCE;
        if (motionCamera.shouldApply()) {
            args.set(0, motionCamera.getFakeX());
            args.set(1, motionCamera.getFakeY());
            args.set(2, motionCamera.getFakeZ());
        }
    }

    @ModifyArgs(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FFF)V"
            )
    )
    private void nyx$setFreeCameraRotation(Args args) {
        Freecam freecam = Freecam.INSTANCE;
        if (freecam.shouldApply()) {
            args.set(0, freecam.getFakeYaw());
            args.set(1, freecam.getFakePitch());
            return;
        }

        FreeLook freeLook = FreeLook.INSTANCE;
        if (freeLook.shouldApply()) {
            args.set(0, freeLook.getFakeYaw());
            args.set(1, freeLook.getFakePitch());
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
