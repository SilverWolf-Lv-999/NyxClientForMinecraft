package io.github.seraphina.nyxclient.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.seraphina.nyxclient.events.bus.EventBus;
import io.github.seraphina.nyxclient.events.impl.RaytraceEvent;
import io.github.seraphina.nyxclient.events.impl.StrafeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityMixin {
    @WrapOperation(method = "getViewVector", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectViewRotation(Entity instance, float xRot, float yRot, Operation<Vec3> original) {
        if (instance == Minecraft.getInstance().player) {
            RaytraceEvent event = EventBus.INSTANCE.post(new RaytraceEvent(instance, yRot, xRot));
            return original.call(instance, event.getPitch(), event.getYaw());
        }

        return original.call(instance, xRot, yRot);
    }

    @WrapOperation(method = "moveRelative", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    private float redirectMoveRelativeYaw(Entity instance, Operation<Float> original) {
        if (instance == Minecraft.getInstance().player) {
            StrafeEvent event = EventBus.INSTANCE.post(new StrafeEvent(instance.getYRot()));
            return event.getYaw();
        }

        return original.call(instance);
    }
}
