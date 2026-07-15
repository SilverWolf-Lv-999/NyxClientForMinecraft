package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.client.EntityCulling;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow
    public Camera camera;

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void nyx$cullHiddenEntity(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValueZ() && EntityCulling.INSTANCE.shouldCull(entity, camera)) {
            info.setReturnValue(false);
        }
    }
}
