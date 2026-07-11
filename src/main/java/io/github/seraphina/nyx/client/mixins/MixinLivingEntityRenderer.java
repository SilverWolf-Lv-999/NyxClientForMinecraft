package io.github.seraphina.nyx.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.RotationAnimationEvent;
import io.github.seraphina.nyx.client.module.visual.ESP;
import io.github.seraphina.nyx.client.utility.IMinecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> implements IMinecraft {

    @Shadow
    protected M model;

    @Shadow
    public abstract Identifier getTextureLocation(S s);

//    @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"))
//    private RenderType modifyRenderType(RenderType original, S state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
//        Chams chamsModule = Chams.INSTANCE;
//        if (!chamsModule.isEnabled() || state.entityType != EntityType.PLAYER) {
//            return original;
//        }
//        return Chams.INSTANCE.getRenderType(getTextureLocation(state));
//    }

    @Inject(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void captureModelBones(S state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState, CallbackInfo info) {
        ESP.INSTANCE.captureModelBones(state, this.model, poseStack, cameraRenderState);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void modifyRotationAnimation(LivingEntity entity, S state, float partialTicks, CallbackInfo info) {
        ESP.INSTANCE.rememberModelBoneEntity(entity, state);
        ESP.INSTANCE.applyGlowOutline(entity, state);

        if (entity != mc.player) {
            return;
        }

        RotationAnimationEvent headEvent = EventBus.INSTANCE.post(new RotationAnimationEvent(
                entity.yHeadRot,
                entity.yHeadRotO,
                entity.getXRot(),
                entity.getXRot(0.0F)
        ));
        RotationAnimationEvent bodyEvent = EventBus.INSTANCE.post(new RotationAnimationEvent(
                entity.yBodyRot,
                entity.yBodyRotO,
                0.0F,
                0.0F
        ));

        float bodyRot = Mth.rotLerp(partialTicks, bodyEvent.getLastYaw(), bodyEvent.getYaw());
        float headRot = Mth.rotLerp(partialTicks, headEvent.getLastYaw(), headEvent.getYaw());
        float pitch = Mth.rotLerp(partialTicks, headEvent.getLastPitch(), headEvent.getPitch());

        state.bodyRot = bodyRot;
        state.yRot = Mth.wrapDegrees(headRot - bodyRot);
        state.xRot = pitch;

        if (state.isUpsideDown) {
            state.yRot *= -1.0F;
            state.xRot *= -1.0F;
        }
    }

}
