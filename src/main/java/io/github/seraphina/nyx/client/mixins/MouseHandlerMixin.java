package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.MousePressEvent;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo info) {
        MousePressEvent event = EventBus.INSTANCE.post(new MousePressEvent(rawButtonInfo.button(), action, rawButtonInfo.modifiers()));
        if (event.isCancelled()) {
            info.cancel();
        }
    }
}
