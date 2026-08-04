package io.github.seraphina.nyx.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.module.visual.NoRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconRenderer.class)
public class BeaconRendererMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void nyx$hideBeaconBeams(
        BeaconRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector nodeCollector,
        CameraRenderState cameraRenderState,
        CallbackInfo info
    ) {
        if (NoRenderer.INSTANCE.shouldDisableBeaconBeams()) {
            info.cancel();
        }
    }
}
