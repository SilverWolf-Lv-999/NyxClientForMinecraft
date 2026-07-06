package io.github.seraphina.nyxclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.seraphina.nyxclient.events.bus.EventBus;
import io.github.seraphina.nyxclient.events.impl.AttackYawEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerMixin {
    @ModifyExpressionValue(method = {"causeExtraKnockback", "doSweepAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float modifyAttackYaw(float original) {
        AttackYawEvent event = EventBus.INSTANCE.post(new AttackYawEvent(original));
        return event.getYaw();
    }
}
