package io.github.seraphina.nyxclient.mixins;

import io.github.seraphina.nyxclient.events.bus.EventBus;
import io.github.seraphina.nyxclient.events.impl.KeyPressEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void keyPress(long handle, int action, KeyEvent keyEvent, CallbackInfo info) {
        KeyPressEvent event = EventBus.INSTANCE.post(new KeyPressEvent(keyEvent, action));
        if (event.isCancelled()) {
            info.cancel();
        }
    }
}
