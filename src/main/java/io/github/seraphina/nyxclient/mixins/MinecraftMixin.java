package io.github.seraphina.nyxclient.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.seraphina.nyxclient.Client;
import io.github.seraphina.nyxclient.events.bus.EventBus;
import io.github.seraphina.nyxclient.events.impl.ClickEvent;
import io.github.seraphina.nyxclient.events.impl.LevelUpdateEvent;
import io.github.seraphina.nyxclient.events.impl.SetScreenEvent;
import io.github.seraphina.nyxclient.events.impl.StartUseItemEvent;
import io.github.seraphina.nyxclient.events.impl.TickEvent;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(CallbackInfo info) {
        Client.INSTANCE.init();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onPreTick(CallbackInfo info) {
        EventBus.INSTANCE.post(new TickEvent.Pre());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onPostTick(CallbackInfo info) {
        EventBus.INSTANCE.post(new TickEvent.Post());
    }

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0, shift = At.Shift.BEFORE), cancellable = true)
    private void onHandleKeybinds(CallbackInfo info) {
        ClickEvent event = EventBus.INSTANCE.post(new ClickEvent());
        if (event.isCancelled()) {
            info.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionHand;values()[Lnet/minecraft/world/InteractionHand;"), cancellable = true)
    private void onStartUseItemBeforeHands(CallbackInfo info) {
        if (EventBus.INSTANCE.post(new StartUseItemEvent()).isCancelled()) {
            info.cancel();
        }
    }

    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("HEAD"))
    private void onUpdateLevelInEngines(ClientLevel level, boolean stopSound, CallbackInfo info) {
        EventBus.INSTANCE.post(new LevelUpdateEvent());
    }

    @Inject(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;clearGuiLayers(Lnet/minecraft/client/Minecraft;)V", shift = At.Shift.BEFORE), cancellable = true)
    private void onSetScreen(CallbackInfo info, @Local(argsOnly = true) LocalRef<Screen> screenRef) {
        SetScreenEvent event = EventBus.INSTANCE.post(new SetScreenEvent(screenRef.get()));
        if (event.isCancelled()) {
            info.cancel();
            return;
        }

        screenRef.set(event.getScreen());
    }

    @Inject(method = "close", at = @At("HEAD"))
    public void close(CallbackInfo info) {
        Render2DUtility.close();
    }
}
