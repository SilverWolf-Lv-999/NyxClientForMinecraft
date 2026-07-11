package io.github.seraphina.nyx.client.mixins;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.seraphina.nyx.client.module.visual.ModernGui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Inject(
        method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onModernGuiSkipBaseTexture(
        RenderPipeline pipeline,
        Identifier texture,
        int x,
        int y,
        float u,
        float v,
        int width,
        int height,
        int regionWidth,
        int regionHeight,
        int textureWidth,
        int textureHeight,
        int color,
        CallbackInfo info
    ) {
        if (ModernGui.INSTANCE.shouldSkipModernizedTexture(texture)) {
            info.cancel();
        }
    }
}
