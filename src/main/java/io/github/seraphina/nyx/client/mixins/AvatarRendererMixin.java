package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.Cape;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void nyx$forceCapeVisible(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo info) {
        if (avatar instanceof ClientAvatarEntity && Cape.INSTANCE.shouldForceCape(avatar)) {
            state.showCape = true;
        }
    }
}
