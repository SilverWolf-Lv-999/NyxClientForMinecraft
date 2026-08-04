package io.github.seraphina.nyx.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.module.visual.NoRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void nyx$hideArmor(
        PoseStack poseStack,
        SubmitNodeCollector nodeCollector,
        int lightCoords,
        HumanoidRenderState state,
        float limbSwing,
        float limbSwingAmount,
        CallbackInfo info
    ) {
        if (NoRenderer.INSTANCE.shouldDisableArmor()) {
            info.cancel();
        }
    }
}
