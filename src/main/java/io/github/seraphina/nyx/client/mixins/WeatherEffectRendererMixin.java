package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.NoRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {
    @Inject(
        method = "render(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/WeatherRenderState;Lnet/minecraft/client/renderer/state/LevelRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void nyx$hideWeather(
        MultiBufferSource bufferSource,
        Vec3 cameraPosition,
        WeatherRenderState weatherRenderState,
        LevelRenderState levelRenderState,
        CallbackInfo info
    ) {
        if (NoRenderer.INSTANCE.shouldDisableWeather()) {
            info.cancel();
        }
    }

    @Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true)
    private void nyx$hideRainParticles(
        ClientLevel level,
        Camera camera,
        int ticks,
        ParticleStatus particleStatus,
        int particleCount,
        CallbackInfo info
    ) {
        if (NoRenderer.INSTANCE.shouldDisableWeather()) {
            info.cancel();
        }
    }
}
