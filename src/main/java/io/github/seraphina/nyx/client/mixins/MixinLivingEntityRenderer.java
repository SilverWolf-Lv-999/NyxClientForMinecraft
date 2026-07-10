package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.RotationAnimationEvent;
import io.github.seraphina.nyx.client.utility.IMinecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<S extends LivingEntityRenderState> implements IMinecraft {

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

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void modifyRotationAnimation(LivingEntity entity, S state, float partialTicks, CallbackInfo info) {
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
