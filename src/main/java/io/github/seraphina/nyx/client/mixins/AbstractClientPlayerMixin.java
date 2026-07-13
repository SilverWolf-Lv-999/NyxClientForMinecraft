package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.Cape;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void nyx$overrideSkin(CallbackInfoReturnable<PlayerSkin> info) {
        info.setReturnValue(Cape.INSTANCE.overrideSkin((AbstractClientPlayer) (Object) this, info.getReturnValue()));
    }
}
