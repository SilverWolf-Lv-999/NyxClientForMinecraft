package io.github.seraphina.nyx.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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

    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;solveBodyRot(Lnet/minecraft/world/entity/LivingEntity;FF)F"))
    private float modifyBodyYaw(float original, LivingEntity entity, S state, float partialTicks) {
        if (entity == mc.player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(entity.yBodyRot, entity.yBodyRotO, 0.0f, 0.0f));
            return Mth.rotLerp(partialTicks, event.getLastYaw(), event.getYaw());
        }
        return original;
    }

    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F"))
    private float modifyHeadYaw(float original, LivingEntity entity, S state, float partialTicks) {
        if (entity == mc.player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(entity.yHeadRot, entity.yHeadRotO, 0.0f, 0.0f));
            return Mth.rotLerp(partialTicks, event.getLastYaw(), event.getYaw());
        }
        return original;
    }

    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F"))
    private float modifyPitch(float original, LivingEntity entity, S state, float partialTicks) {
        if (entity == mc.player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(0.0f, 0.0f, entity.getXRot(), entity.getXRot(0.0f)));
            return Mth.rotLerp(partialTicks, event.getLastPitch(), event.getPitch());
        }
        return original;
    }

}
