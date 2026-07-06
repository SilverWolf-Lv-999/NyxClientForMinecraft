package io.github.seraphina.nyxclient.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.seraphina.nyxclient.events.bus.EventBus;
import io.github.seraphina.nyxclient.events.impl.CollisionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisions.class)
public class BlockCollisionsMixin {
    @WrapOperation(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState hookComputeNext(BlockGetter instance, BlockPos blockPos, Operation<BlockState> original) {
        CollisionEvent event = EventBus.INSTANCE.post(new CollisionEvent(original.call(instance, blockPos), blockPos));
        return event.getState();
    }
}
