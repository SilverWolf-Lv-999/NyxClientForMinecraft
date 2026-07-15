package io.github.seraphina.nyx.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.module.client.BlockCulling;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Shadow
    private Vec3 cameraPos;

    @Shadow
    @Nullable
    public abstract <E extends BlockEntity, S extends BlockEntityRenderState> BlockEntityRenderer<E, S> getRenderer(E blockEntity);

    @Inject(
            method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("RETURN"),
            cancellable = true
    )
    private <E extends BlockEntity, S extends BlockEntityRenderState> void nyx$cullHiddenBlockEntity(
            E blockEntity,
            float partialTick,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress,
            @Nullable Frustum frustum,
            CallbackInfoReturnable<S> info
    ) {
        S state = info.getReturnValue();
        if (state == null) {
            return;
        }

        if (!BlockCulling.INSTANCE.shouldCheckBlockEntityOcclusion(breakProgress)) {
            return;
        }

        BlockEntityRenderer<E, S> renderer = getRenderer(blockEntity);
        if (renderer != null && BlockCulling.INSTANCE.shouldCullBlockEntity(blockEntity, renderer.getRenderBoundingBox(blockEntity), cameraPos, breakProgress)) {
            info.setReturnValue(null);
        }
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private <S extends BlockEntityRenderState> void nyx$beginBlockEntitySubmit(
            S state,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            CameraRenderState cameraRenderState,
            CallbackInfo info
    ) {
        BlockCulling.INSTANCE.beginBlockEntitySubmit(state);
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private <S extends BlockEntityRenderState> void nyx$endBlockEntitySubmit(
            S state,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            CameraRenderState cameraRenderState,
            CallbackInfo info
    ) {
        BlockCulling.INSTANCE.endBlockEntitySubmit();
    }
}
