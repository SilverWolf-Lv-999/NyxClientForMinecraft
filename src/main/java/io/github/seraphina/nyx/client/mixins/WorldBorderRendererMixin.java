package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.NoRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.state.WorldBorderRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRenderer.class)
public class WorldBorderRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void nyx$hideWorldBorder(
        WorldBorderRenderState worldBorderRenderState,
        Vec3 cameraPosition,
        double renderDistance,
        double worldHeight,
        CallbackInfo info
    ) {
        if (NoRenderer.INSTANCE.shouldDisableWorldBorder()) {
            info.cancel();
        }
    }
}
