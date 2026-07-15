package io.github.seraphina.nyx.client.mixins;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.core.particles.ParticleLimit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ParticleGroup.class)
public abstract class ParticleGroupMixin {
    @Redirect(
            method = "tickParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleEngine;updateCount(Lnet/minecraft/core/particles/ParticleLimit;I)V"
            ),
            require = 0
    )
    private void nyx$synchronizedUpdateCount(ParticleEngine engine, ParticleLimit particleLimit, int amount) {
        synchronized (engine) {
            ((ParticleEngineAccessor) engine).nyx$invokeUpdateCount(particleLimit, amount);
        }
    }
}
