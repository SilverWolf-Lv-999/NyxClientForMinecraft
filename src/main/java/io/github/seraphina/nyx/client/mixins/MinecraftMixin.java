package io.github.seraphina.nyx.client.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.ClickEvent;
import io.github.seraphina.nyx.client.events.impl.LevelUpdateEvent;
import io.github.seraphina.nyx.client.events.impl.SetScreenEvent;
import io.github.seraphina.nyx.client.events.impl.StartUseItemEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.module.client.NoChattingAllowed;
import io.github.seraphina.nyx.client.module.combat.UseClick;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    public Options options;

    @Shadow
    public Gui gui;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(CallbackInfo info) {
        NyxClient.INSTANCE.init();
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

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0))
    private boolean allowClicksWhileUsingInKeybinds(LocalPlayer player, Operation<Boolean> original) {
        return original.call(player) && !UseClick.INSTANCE.shouldProcessClicksWhileUsing(player, options.keyUse.isDown());
    }

    @WrapOperation(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
    private boolean allowStartAttackWhileUsing(LocalPlayer player, Operation<Boolean> original) {
        return original.call(player) && !UseClick.INSTANCE.canClickWhileUsing(player);
    }

    @WrapOperation(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean allowContinueAttackWhileUsing(LocalPlayer player, Operation<Boolean> original) {
        return original.call(player) && !UseClick.INSTANCE.canClickWhileUsing(player);
    }

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionHand;values()[Lnet/minecraft/world/InteractionHand;"), cancellable = true)
    private void onStartUseItemBeforeHands(CallbackInfo info) {
        if (EventBus.INSTANCE.post(new StartUseItemEvent()).isCancelled()) {
            info.cancel();
        }
    }

    @Inject(method = "openChatScreen", at = @At("HEAD"), cancellable = true)
    private void nyx$openChatScreen(ChatComponent.ChatMethod chatMethod, CallbackInfo info) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!NoChattingAllowed.INSTANCE.shouldBypassChatRestriction(minecraft)) {
            return;
        }

        this.gui.setChatDisabledByPlayerShown(false);
        this.gui.getChat().openScreen(chatMethod, ChatScreen::new);
        info.cancel();
    }

    @Inject(method = "isBlocked", at = @At("HEAD"), cancellable = true)
    private void nyx$isBlocked(UUID playerId, CallbackInfoReturnable<Boolean> info) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (NoChattingAllowed.INSTANCE.shouldBypassMessageBlocking(minecraft)) {
            info.setReturnValue(minecraft.getPlayerSocialManager().shouldHideMessageFrom(playerId));
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
        FontManager.close();
        Render2DUtility.close();
    }
}
