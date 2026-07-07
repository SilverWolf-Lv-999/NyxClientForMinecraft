package io.github.seraphina.nyx.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"))
    private Input redirectKeyPresses(Input original) {
        MoveInputEvent event = new MoveInputEvent(
                original.forward(),
                original.backward(),
                original.left(),
                original.right(),
                original.jump(),
                original.shift(),
                original.sprint()
        );
        return EventBus.INSTANCE.post(event).toNewInput();
    }
}
