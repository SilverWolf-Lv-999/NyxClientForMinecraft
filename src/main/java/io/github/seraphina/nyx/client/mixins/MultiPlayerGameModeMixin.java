package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.AttackBlockEvent;
import io.github.seraphina.nyx.client.events.impl.AttackEntityEvent;
import io.github.seraphina.nyx.client.events.impl.DestroyBlockEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttackEntity(Player player, Entity entity, CallbackInfo info) {
        AttackEntityEvent event = EventBus.INSTANCE.post(new AttackEntityEvent(player, entity));
        if (event.isCancelled()) {
            info.cancel();
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> info) {
        AttackBlockEvent event = EventBus.INSTANCE.post(new AttackBlockEvent(pos, direction));
        if (event.isCancelled()) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"), cancellable = true)
    private void onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> info) {
        DestroyBlockEvent event = EventBus.INSTANCE.post(new DestroyBlockEvent(pos));
        if (event.isCancelled()) {
            info.setReturnValue(false);
        }
    }
}
