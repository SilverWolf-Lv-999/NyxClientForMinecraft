package io.github.seraphina.nyx.client.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.MousePressEvent;
import io.github.seraphina.nyx.client.events.impl.MouseScrollEvent;
import io.github.seraphina.nyx.client.module.player.FreeLook;
import io.github.seraphina.nyx.client.module.player.Freecam;
import io.github.seraphina.nyx.client.module.player.Yaw;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
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

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long handle, double scrollX, double scrollY, CallbackInfo info) {
        MouseScrollEvent event = EventBus.INSTANCE.post(new MouseScrollEvent(scrollX, scrollY));
        if (event.isCancelled()) {
            info.cancel();
        }
    }

    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void nyx$handleCameraTurn(LocalPlayer player, double yawDelta, double pitchDelta, Operation<Void> original) {
        if (Freecam.INSTANCE.handleMouseTurn(yawDelta, pitchDelta)
                || FreeLook.INSTANCE.handleMouseTurn(yawDelta, pitchDelta)
                || Yaw.INSTANCE.shouldBlockMouseInput()) {
            return;
        }

        original.call(player, yawDelta, pitchDelta);
    }
}
