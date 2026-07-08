package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.FullBright;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public class LightTextureMixin {
    @Redirect(
            method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F")
    )
    public float nyx$updateLightTexture(Double instance) {
        final float gamma = FullBright.INSTANCE.isEnabled() ? 15F : 1.0F;
        return instance.floatValue() * gamma;
    }
}
