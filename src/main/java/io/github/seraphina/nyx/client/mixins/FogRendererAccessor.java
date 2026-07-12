package io.github.seraphina.nyx.client.mixins;

import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FogRenderer.class)
public interface FogRendererAccessor {
    @Accessor("fogEnabled")
    static void nyx$setFogEnabled(boolean fogEnabled) {
        throw new AssertionError();
    }
}
