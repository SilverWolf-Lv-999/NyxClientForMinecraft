package io.github.seraphina.nyxclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.seraphina.nyxclient.events.bus.EventBus;
import io.github.seraphina.nyxclient.events.impl.UseItemRaytraceEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Item.class)
public class ItemMixin {
    @ModifyExpressionValue(method = "getPlayerPOVHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 hookUseItemRayTrace(Vec3 original, Level level, Player player, ClipContext.Fluid fluid) {
        UseItemRaytraceEvent event = EventBus.INSTANCE.post(new UseItemRaytraceEvent(player.getYRot(), player.getXRot()));
        return player.calculateViewVector(event.getPitch(), event.getYaw());
    }
}
