package io.github.seraphina.nyx.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.AttackYawEvent;
import io.github.seraphina.nyx.client.events.impl.RotationAnimationEvent;
import io.github.seraphina.nyx.client.module.combat.Reach;
import io.github.seraphina.nyx.client.module.combat.SpearCooldown;
import io.github.seraphina.nyx.client.module.movement.SafeWalk;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @ModifyExpressionValue(method = {"causeExtraKnockback", "doSweepAttack*"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float modifyAttackYaw(float original) {
        AttackYawEvent event = EventBus.INSTANCE.post(new AttackYawEvent(original));
        return event.getYaw();
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F", ordinal = 0))
    private float modifyHeadYaw(float original) {
        if ((Object) this instanceof LocalPlayer) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(original, 0.0F, 0.0F, 0.0F));
            return event.getYaw();
        }

        return original;
    }

    @ModifyExpressionValue(method = "maybeBackOffFromEdge", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isStayingOnGroundSurface()Z"))
    private boolean nyx$enableSafeWalk(boolean original) {
        return original || ((Object) this instanceof LocalPlayer player && SafeWalk.INSTANCE.shouldStayOnGroundSurface(player));
    }

    @Inject(method = "entityInteractionRange()D", at = @At("RETURN"), cancellable = true)
    private void modifyEntityInteractionRange(CallbackInfoReturnable<Double> info) {
        if ((Object) this instanceof LocalPlayer) {
            info.setReturnValue(Reach.INSTANCE.getEntityRange(info.getReturnValue()));
        }
    }

    @Inject(method = "cannotAttackWithItem", at = @At("HEAD"), cancellable = true)
    private void nyx$modifySpearAttackCooldown(ItemStack stack, int adjustTicks, CallbackInfoReturnable<Boolean> info) {
        if ((Object) this instanceof LocalPlayer && SpearCooldown.INSTANCE.shouldOverrideSpearAttackCooldown(stack)) {
            int attackStrengthTicker = ((LivingEntityAccessor) this).nyx$getAttackStrengthTicker();
            info.setReturnValue(SpearCooldown.INSTANCE.cannotAttackYet(attackStrengthTicker, adjustTicks));
        }
    }
}
