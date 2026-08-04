package io.github.seraphina.nyx.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.module.visual.NoRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void nyx$hideInWallOverlay(
        TextureAtlasSprite sprite,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        CallbackInfo info
    ) {
        if (NoRenderer.INSTANCE.shouldDisableInWallOverlay()) {
            info.cancel();
        }
    }

    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void nyx$hideWaterOverlay(
        Minecraft minecraft,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        CallbackInfo info
    ) {
        if (NoRenderer.INSTANCE.shouldDisableLiquidOverlay()) {
            info.cancel();
        }
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private static void nyx$hideFluidOverlay(
        Minecraft minecraft,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        Identifier texture,
        CallbackInfo info
    ) {
        if (NoRenderer.INSTANCE.shouldDisableLiquidOverlay()) {
            info.cancel();
        }
    }

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void nyx$hideFireOverlay(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        TextureAtlasSprite sprite,
        CallbackInfo info
    ) {
        if (NoRenderer.INSTANCE.shouldDisableFireOverlay()) {
            info.cancel();
        }
    }
}
