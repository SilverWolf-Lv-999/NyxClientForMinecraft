package io.github.seraphina.nyx.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.module.visual.NoRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
    private void nyx$hideNameTags(
        EntityRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector nodeCollector,
        CameraRenderState cameraRenderState,
        CallbackInfo info
    ) {
        if (NoRenderer.INSTANCE.shouldDisableNameTags()) {
            info.cancel();
        }
    }
}
