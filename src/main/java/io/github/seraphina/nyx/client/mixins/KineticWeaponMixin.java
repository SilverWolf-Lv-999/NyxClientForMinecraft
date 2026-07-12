package io.github.seraphina.nyx.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.seraphina.nyx.client.module.combat.SpearCooldown;
import net.minecraft.world.item.component.KineticWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KineticWeapon.class)
public class KineticWeaponMixin {
    @ModifyExpressionValue(method = {"damageEntities", "computeDamageUseDuration"}, at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/component/KineticWeapon;delayTicks:I"))
    private int nyx$modifySpearUseDelayField(int original) {
        return SpearCooldown.INSTANCE.useDelay(original);
    }

    @ModifyExpressionValue(method = "damageEntities", at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/component/KineticWeapon;contactCooldownTicks:I"))
    private int nyx$modifySpearHitCooldownField(int original) {
        return SpearCooldown.INSTANCE.hitCooldown(original);
    }

    @ModifyReturnValue(method = "delayTicks", at = @At("RETURN"))
    private int nyx$modifySpearUseDelay(int original) {
        return SpearCooldown.INSTANCE.useDelay(original);
    }

    @ModifyReturnValue(method = "contactCooldownTicks", at = @At("RETURN"))
    private int nyx$modifySpearHitCooldown(int original) {
        return SpearCooldown.INSTANCE.hitCooldown(original);
    }
}
