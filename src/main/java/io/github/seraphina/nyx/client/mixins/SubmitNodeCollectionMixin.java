package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.client.BlockCulling;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
    @ModifyArg(
            method = "submitModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Storage;add(Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelSubmit;)V"
            ),
            index = 0
    )
    private RenderType nyx$cullBlockEntityModel(RenderType renderType) {
        return BlockCulling.INSTANCE.cullBlockEntityRenderType(renderType);
    }

    @ModifyArg(
            method = "submitModelPart",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/ModelPartFeatureRenderer$Storage;add(Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelPartSubmit;)V"
            ),
            index = 0
    )
    private RenderType nyx$cullBlockEntityModelPart(RenderType renderType) {
        return BlockCulling.INSTANCE.cullBlockEntityRenderType(renderType);
    }

    @ModifyArg(
            method = "submitBlockModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$BlockModelSubmit;<init>(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/block/model/BlockStateModel;FFFIII)V"
            ),
            index = 1
    )
    private RenderType nyx$cullBlockEntityBlockModel(RenderType renderType) {
        return BlockCulling.INSTANCE.cullBlockEntityRenderType(renderType);
    }

    @ModifyArg(
            method = "submitCustomGeometry",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/CustomFeatureRenderer$Storage;add(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"
            ),
            index = 1
    )
    private RenderType nyx$cullBlockEntityCustomGeometry(RenderType renderType) {
        return BlockCulling.INSTANCE.cullBlockEntityRenderType(renderType);
    }
}
