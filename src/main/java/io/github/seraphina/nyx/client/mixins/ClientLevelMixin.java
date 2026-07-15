package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.client.ThreadRipper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Shadow
    @Final
    private EntityTickList tickingEntities;

    @Shadow
    @Final
    private TickRateManager tickRateManager;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "tickEntities", at = @At("HEAD"), cancellable = true)
    private void nyx$parallelTickEntities(CallbackInfo info) {
        ThreadRipper threadRipper = ThreadRipper.INSTANCE;
        if (!threadRipper.shouldParallelEntityTicks()) {
            return;
        }

        List<Entity> tickableEntities = new ArrayList<>();
        tickingEntities.forEach(entity -> {
            if (!entity.isRemoved() && !entity.isPassenger() && !tickRateManager.isEntityFrozen(entity)) {
                tickableEntities.add(entity);
            }
        });
        if (tickableEntities.size() < 2) {
            return;
        }

        ClientLevel level = (ClientLevel)(Object)this;
        threadRipper.runParallel(tickableEntities, entity -> level.guardEntityTick(level::tickNonPassenger, entity));
        info.cancel();
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void nyx$addParticleFromWorker(ParticleOptions particleOptions, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfo info) {
        if (nyx$queueOnRenderThread(() -> ((ClientLevel) (Object) this).addParticle(particleOptions, x, y, z, xSpeed, ySpeed, zSpeed))) {
            info.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void nyx$addParticleFromWorker(ParticleOptions particleOptions, boolean force, boolean alwaysVisible, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfo info) {
        if (nyx$queueOnRenderThread(() -> ((ClientLevel) (Object) this).addParticle(particleOptions, force, alwaysVisible, x, y, z, xSpeed, ySpeed, zSpeed))) {
            info.cancel();
        }
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void nyx$addAlwaysVisibleParticleFromWorker(ParticleOptions particleOptions, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfo info) {
        if (nyx$queueOnRenderThread(() -> ((ClientLevel) (Object) this).addAlwaysVisibleParticle(particleOptions, x, y, z, xSpeed, ySpeed, zSpeed))) {
            info.cancel();
        }
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void nyx$addAlwaysVisibleParticleFromWorker(ParticleOptions particleOptions, boolean ignoreRange, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfo info) {
        if (nyx$queueOnRenderThread(() -> ((ClientLevel) (Object) this).addAlwaysVisibleParticle(particleOptions, ignoreRange, x, y, z, xSpeed, ySpeed, zSpeed))) {
            info.cancel();
        }
    }

    @Inject(method = "playLocalSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V", at = @At("HEAD"), cancellable = true)
    private void nyx$playEntitySoundFromWorker(Entity entity, SoundEvent soundEvent, SoundSource soundSource, float volume, float pitch, CallbackInfo info) {
        if (nyx$queueOnRenderThread(() -> ((ClientLevel) (Object) this).playLocalSound(entity, soundEvent, soundSource, volume, pitch))) {
            info.cancel();
        }
    }

    @Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", at = @At("HEAD"), cancellable = true)
    private void nyx$playSoundFromWorker(double x, double y, double z, SoundEvent soundEvent, SoundSource soundSource, float volume, float pitch, boolean distanceDelay, CallbackInfo info) {
        if (nyx$queueOnRenderThread(() -> ((ClientLevel) (Object) this).playLocalSound(x, y, z, soundEvent, soundSource, volume, pitch, distanceDelay))) {
            info.cancel();
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
    private void nyx$playSeededSoundFromWorker(Entity source, double x, double y, double z, Holder<SoundEvent> soundEvent, SoundSource soundSource, float volume, float pitch, long seed, CallbackInfo info) {
        if (nyx$queueOnRenderThread(() -> ((ClientLevel) (Object) this).playSeededSound(source, x, y, z, soundEvent, soundSource, volume, pitch, seed))) {
            info.cancel();
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
    private void nyx$playSeededEntitySoundFromWorker(Entity source, Entity entity, Holder<SoundEvent> soundEvent, SoundSource soundSource, float volume, float pitch, long seed, CallbackInfo info) {
        if (nyx$queueOnRenderThread(() -> ((ClientLevel) (Object) this).playSeededSound(source, entity, soundEvent, soundSource, volume, pitch, seed))) {
            info.cancel();
        }
    }

    @Inject(method = "playPlayerSound", at = @At("HEAD"), cancellable = true)
    private void nyx$playPlayerSoundFromWorker(SoundEvent soundEvent, SoundSource soundSource, float volume, float pitch, CallbackInfo info) {
        if (nyx$queueOnRenderThread(() -> ((ClientLevel) (Object) this).playPlayerSound(soundEvent, soundSource, volume, pitch))) {
            info.cancel();
        }
    }

    @Unique
    private boolean nyx$queueOnRenderThread(Runnable action) {
        if (!ThreadRipper.INSTANCE.isWorkerThread()) {
            return false;
        }

        minecraft.execute(action);
        return true;
    }
}
