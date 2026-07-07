package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.NoRenderer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void nyx$createParticle(ParticleOptions particleOptions, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> info) {
        if (nyx$shouldDisableParticles()) info.setReturnValue(null);
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void nyx$add(Particle particle, CallbackInfo info) {
        if (nyx$shouldDisableParticles()) info.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("HEAD"), cancellable = true)
    private void nyx$createTrackingEmitter(Entity entity, ParticleOptions particleOptions, CallbackInfo info) {
        if (nyx$shouldDisableParticles()) info.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"), cancellable = true)
    private void nyx$createTrackingEmitter(Entity entity, ParticleOptions particleOptions, int lifetime, CallbackInfo info) {
        if (nyx$shouldDisableParticles()) info.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void nyx$tick(CallbackInfo info) {
        if (nyx$shouldDisableParticles()) {
            ((ParticleEngine) (Object) this).clearParticles();
            info.cancel();
        }
    }

    @Unique
    private static boolean nyx$shouldDisableParticles() {
        return NoRenderer.INSTANCE.isEnabled() && NoRenderer.INSTANCE.noparticles.getValue();
    }
}
