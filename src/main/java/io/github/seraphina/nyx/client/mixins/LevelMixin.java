package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.Ambient;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
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
}
