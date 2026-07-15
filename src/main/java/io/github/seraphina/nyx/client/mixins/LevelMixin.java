package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.client.ThreadRipper;
import io.github.seraphina.nyx.client.module.visual.Ambient;
import io.github.seraphina.nyx.client.utility.SynchronizedRandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mixin(Level.class)
public class LevelMixin {
    @Shadow
    @Final
    protected List<TickingBlockEntity> blockEntityTickers;

    @Shadow
    @Final
    private List<TickingBlockEntity> pendingBlockEntityTickers;

    @Shadow
    private boolean tickingBlockEntities;

    @Shadow
    @Final
    private ArrayList<BlockEntity> freshBlockEntities;

    @Shadow
    @Final
    private ArrayList<BlockEntity> pendingFreshBlockEntities;

    @Shadow
    @Final
    @Mutable
    public RandomSource random;

    @Shadow
    public TickRateManager tickRateManager() {
        throw new AssertionError();
    }

    @Shadow
    public boolean shouldTickBlocksAt(BlockPos pos) {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nyx$wrapClientRandom(WritableLevelData data, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates, CallbackInfo info) {
        if (isClientSide) {
            random = new SynchronizedRandomSource(random);
        }
    }

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void nyx$getDayTime(CallbackInfoReturnable<Long> info) {
        if (Ambient.INSTANCE.shouldChangeTime()) {
            info.setReturnValue(Ambient.INSTANCE.getClientTime());
        }
    }

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void nyx$getRainLevel(float delta, CallbackInfoReturnable<Float> info) {
        if (Ambient.INSTANCE.shouldChangeWeather()) {
            info.setReturnValue(Ambient.INSTANCE.getRainLevel());
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void nyx$getThunderLevel(float delta, CallbackInfoReturnable<Float> info) {
        if (Ambient.INSTANCE.shouldChangeWeather()) {
            info.setReturnValue(Ambient.INSTANCE.getThunderLevel());
        }
    }

    @Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
    private void nyx$parallelTickBlockEntities(CallbackInfo info) {
        Level level = (Level)(Object)this;
        ThreadRipper threadRipper = ThreadRipper.INSTANCE;
        if (!level.isClientSide() || !threadRipper.shouldParallelBlockEntityTicks()) {
            return;
        }

        if (!pendingFreshBlockEntities.isEmpty()) {
            freshBlockEntities.addAll(pendingFreshBlockEntities);
            pendingFreshBlockEntities.clear();
        }

        tickingBlockEntities = true;
        try {
            if (!freshBlockEntities.isEmpty()) {
                freshBlockEntities.forEach(blockEntity -> {
                    if (!blockEntity.isRemoved() && blockEntity.hasLevel()) {
                        blockEntity.onLoad();
                    }
                });
                freshBlockEntities.clear();
            }
            if (!pendingBlockEntityTickers.isEmpty()) {
                blockEntityTickers.addAll(pendingBlockEntityTickers);
                pendingBlockEntityTickers.clear();
            }

            boolean shouldTick = tickRateManager().runsNormally();
            List<TickingBlockEntity> tickableBlockEntities = new ArrayList<>();
            Iterator<TickingBlockEntity> iterator = blockEntityTickers.iterator();
            while (iterator.hasNext()) {
                TickingBlockEntity blockEntity = iterator.next();
                if (blockEntity.isRemoved()) {
                    iterator.remove();
                } else if (shouldTick && shouldTickBlocksAt(blockEntity.getPos())) {
                    tickableBlockEntities.add(blockEntity);
                }
            }

            threadRipper.runParallel(tickableBlockEntities, TickingBlockEntity::tick);
        } finally {
            tickingBlockEntities = false;
        }

        info.cancel();
    }
}
