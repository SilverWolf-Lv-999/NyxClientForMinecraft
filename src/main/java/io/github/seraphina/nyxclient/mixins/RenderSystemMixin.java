package io.github.seraphina.nyxclient.mixins;

import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.seraphina.nyxclient.utility.skija.SkiaUtility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class RenderSystemMixin {
    @Inject(method = "initRenderer", at = @At("TAIL"))
    private static void initSkia(long window, int debugVerbosity, boolean sync, ShaderSource shaderSource, boolean renderDebugLabels, CallbackInfo info) {
        SkiaUtility.start();
    }
}
