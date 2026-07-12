package io.github.seraphina.nyx.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.module.client.Zoom;
import io.github.seraphina.nyx.client.module.visual.NoRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"), cancellable = true)
    private void bobHurt(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (NoRenderer.INSTANCE.isEnabled() && NoRenderer.INSTANCE.nohurtcamera.getValue()) ci.cancel();
    }

    @Inject(method = "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"), cancellable = true)
    private void bobView(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (NoRenderer.INSTANCE.isEnabled() && NoRenderer.INSTANCE.noview.getValue()) ci.cancel();
    }

    @Inject(method = "displayItemActivation(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void displayItemActivation(ItemStack stack, CallbackInfo ci) {
        if (NoRenderer.INSTANCE.shouldDisableTotemAnimation(stack)) ci.cancel();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void getFov(Camera camera, float partialTick, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (changingFov) {
            cir.setReturnValue(Zoom.INSTANCE.applyZoom(cir.getReturnValue()));
        }
    }
}
