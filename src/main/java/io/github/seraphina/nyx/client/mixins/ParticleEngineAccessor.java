package io.github.seraphina.nyx.client.mixins;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleLimit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Invoker("updateCount")
    void nyx$invokeUpdateCount(ParticleLimit particleLimit, int amount);
}
