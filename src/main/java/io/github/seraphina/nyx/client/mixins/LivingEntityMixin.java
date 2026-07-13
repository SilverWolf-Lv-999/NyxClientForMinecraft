package io.github.seraphina.nyx.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.FallFlyingEvent;
import io.github.seraphina.nyx.client.events.impl.JumpEvent;
import io.github.seraphina.nyx.client.events.impl.RotationAnimationEvent;
import io.github.seraphina.nyx.client.events.impl.TravelEvent;
import io.github.seraphina.nyx.client.module.combat.SpearCooldown;
import io.github.seraphina.nyx.client.module.player.AntiEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @WrapOperation(method = "tickHeadTurn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float modifyHeadYaw(LivingEntity entity, Operation<Float> original) {
        if (entity == Minecraft.getInstance().player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(entity.getYRot(), 0.0F, 0.0F, 0.0F));
            return event.getYaw();
        }

        return original.call(entity);
    }

    @WrapOperation(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float redirectJumpYaw(LivingEntity entity, Operation<Float> original) {
        if (entity == Minecraft.getInstance().player) {
            JumpEvent event = EventBus.INSTANCE.post(new JumpEvent(entity.getYRot()));
            return event.getYaw();
        }

        return original.call(entity);
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float modifyFallFlyingPitch(float original) {
        if ((LivingEntity) (Object) this != Minecraft.getInstance().player) {
            return original;
        }

        FallFlyingEvent event = EventBus.INSTANCE.post(new FallFlyingEvent(original));
        return event.getPitch();
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravel(Vec3 travelVector, CallbackInfo info) {
        if ((LivingEntity) (Object) this == Minecraft.getInstance().player) {
            TravelEvent event = EventBus.INSTANCE.post(new TravelEvent());
            if (event.isCancelled()) {
                info.cancel();
            }
        }
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"), require = 0)
    private boolean nyx$antiEffects(LivingEntity entity, Holder<MobEffect> effect, Operation<Boolean> original) {
        if (shouldIgnoreEffect(entity, effect)) {
            return false;
        }

        return original.call(entity, effect);
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getEffect(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;"), require = 0)
    private MobEffectInstance nyx$antiEffectInstance(LivingEntity entity, Holder<MobEffect> effect, Operation<MobEffectInstance> original) {
        if (shouldIgnoreEffect(entity, effect)) {
            return null;
        }

        return original.call(entity, effect);
    }

    @WrapOperation(method = "getEffectiveGravity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"), require = 0)
    private boolean nyx$antiEffectGravity(LivingEntity entity, Holder<MobEffect> effect, Operation<Boolean> original) {
        if (shouldIgnoreEffect(entity, effect)) {
            return false;
        }

        return original.call(entity, effect);
    }

    private boolean shouldIgnoreEffect(LivingEntity entity, Holder<MobEffect> effect) {
        AntiEffects antiEffects = AntiEffects.INSTANCE;
        return entity == Minecraft.getInstance().player
                && antiEffects.isEnabled()
                && ((effect == MobEffects.SLOW_FALLING && antiEffects.slowFalling.getValue())
                || (effect == MobEffects.LEVITATION && antiEffects.levitation.getValue()));
    }

    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int nyx$modifySpearSwingDuration(int original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity != Minecraft.getInstance().player) {
            return original;
        }

        return SpearCooldown.INSTANCE.swingDuration(original, entity.getMainHandItem());
    }
}
