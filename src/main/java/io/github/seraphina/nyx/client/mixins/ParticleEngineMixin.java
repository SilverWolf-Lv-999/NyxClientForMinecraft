package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.client.ThreadRipper;
import io.github.seraphina.nyx.client.module.visual.NoRenderer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Shadow
    private Map<ParticleRenderType, ParticleGroup<?>> particles;

    @Shadow
    private Queue<TrackingEmitter> trackingEmitters;

    @Shadow
    private Queue<Particle> particlesToAdd;

    @Shadow
    private <T extends ParticleOptions> Particle makeParticle(T particleOptions, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract void updateCount(ParticleLimit particleLimit, int amount);

    @Shadow
    private boolean hasSpaceInParticleLimit(ParticleLimit particleLimit) {
        throw new AssertionError();
    }

    @Shadow
    private ParticleGroup<?> createParticleGroup(ParticleRenderType particleRenderType) {
        throw new AssertionError();
    }

    @Unique
    private final ThreadLocal<Boolean> nyx$createdFromAllowedOptions = ThreadLocal.withInitial(() -> false);

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void nyx$createParticle(ParticleOptions particleOptions, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> info) {
        NoRenderer noRenderer = NoRenderer.INSTANCE;
        nyx$createdFromAllowedOptions.set(false);
        if (noRenderer.shouldDisableParticle(particleOptions)) {
            nyx$createdFromAllowedOptions.set(false);
            info.setReturnValue(null);
            return;
        }

        if (noRenderer.shouldTrackAllowedParticleAdds()) {
            nyx$createdFromAllowedOptions.set(true);
        }

        if (ThreadRipper.INSTANCE.isWorkerThread()) {
            Particle particle;
            try {
                synchronized (this) {
                    particle = makeParticle(particleOptions, x, y, z, xSpeed, ySpeed, zSpeed);
                    if (particle != null) {
                        nyx$addParticleDirect(particle);
                    }
                }
            } finally {
                nyx$createdFromAllowedOptions.set(false);
            }
            info.setReturnValue(particle);
        }
    }

    @Inject(method = "createParticle", at = @At("RETURN"))
    private void nyx$createParticleReturn(ParticleOptions particleOptions, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> info) {
        nyx$createdFromAllowedOptions.set(false);
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void nyx$add(Particle particle, CallbackInfo info) {
        if (NoRenderer.INSTANCE.shouldDisableParticleAdd(nyx$createdFromAllowedOptions.get())) {
            info.cancel();
            return;
        }

        if (ThreadRipper.INSTANCE.isWorkerThread()) {
            synchronized (this) {
                nyx$addParticleDirect(particle);
            }
            info.cancel();
        }
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("HEAD"), cancellable = true)
    private void nyx$createTrackingEmitter(Entity entity, ParticleOptions particleOptions, CallbackInfo info) {
        if (NoRenderer.INSTANCE.shouldDisableParticle(particleOptions)) info.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"), cancellable = true)
    private void nyx$createTrackingEmitter(Entity entity, ParticleOptions particleOptions, int lifetime, CallbackInfo info) {
        if (NoRenderer.INSTANCE.shouldDisableParticle(particleOptions)) info.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void nyx$tick(CallbackInfo info) {
        if (NoRenderer.INSTANCE.shouldClearParticleEngine()) {
            ((ParticleEngine) (Object) this).clearParticles();
            info.cancel();
            return;
        }

        ThreadRipper threadRipper = ThreadRipper.INSTANCE;
        if (threadRipper.shouldParallelParticleTicks() && nyx$parallelTickParticles(threadRipper)) {
            info.cancel();
        }
    }

    @Unique
    private boolean nyx$parallelTickParticles(ThreadRipper threadRipper) {
        List<ParticleGroup<?>> particleGroups;
        synchronized (this) {
            if (particles.size() < 2) {
                return false;
            }
            particleGroups = new ArrayList<>(particles.values());
        }

        if (particleGroups.size() < 2) {
            return false;
        }

        threadRipper.runParallel(particleGroups, ParticleGroup::tickParticles);
        nyx$tickTrackingEmitters();
        nyx$drainParticlesToAdd();
        return true;
    }

    @Unique
    private void nyx$tickTrackingEmitters() {
        if (trackingEmitters.isEmpty()) {
            return;
        }

        List<TrackingEmitter> removed = new ArrayList<>();
        for (TrackingEmitter trackingEmitter : trackingEmitters) {
            trackingEmitter.tick();
            if (!trackingEmitter.isAlive()) {
                removed.add(trackingEmitter);
            }
        }
        trackingEmitters.removeAll(removed);
    }

    @Unique
    private void nyx$drainParticlesToAdd() {
        Particle particle;
        while ((particle = particlesToAdd.poll()) != null) {
            particles.computeIfAbsent(particle.getGroup(), this::createParticleGroup).add(particle);
        }
    }

    @Unique
    private void nyx$addParticleDirect(Particle particle) {
        Optional<ParticleLimit> limit = particle.getParticleLimit();
        if (limit.isPresent()) {
            ParticleLimit particleLimit = limit.get();
            if (hasSpaceInParticleLimit(particleLimit)) {
                particlesToAdd.add(particle);
                updateCount(particleLimit, 1);
            }
        } else {
            particlesToAdd.add(particle);
        }
    }
}
