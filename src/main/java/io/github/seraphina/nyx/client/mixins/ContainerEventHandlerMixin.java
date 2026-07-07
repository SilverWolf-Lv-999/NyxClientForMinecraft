package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.manager.HUDManager;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerMixin {
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void nyx$mouseDragged(MouseButtonEvent event, double dragX, double dragY, CallbackInfoReturnable<Boolean> info) {
        if (this instanceof ChatScreen && HUDManager.drag(event.x(), event.y())) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void nyx$mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> info) {
        if (this instanceof ChatScreen && HUDManager.stopDragging()) {
            info.setReturnValue(true);
        }
    }
}
