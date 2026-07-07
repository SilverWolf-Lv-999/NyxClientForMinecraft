package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.manager.CommandManager;
import io.github.seraphina.nyx.client.manager.HUDManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void nyx$handleClientCommand(String message, boolean addRecentChat, CallbackInfo info) {
        String normalized = ((ChatScreen) (Object) this).normalizeChatMessage(message);
        if (CommandManager.handleChatInput(normalized, addRecentChat)) {
            info.cancel();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void nyx$renderHudEditor(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        HUDManager.render(guiGraphics, partialTick);
        HUDManager.renderEditor(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void nyx$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> info) {
        if (HUDManager.startDragging(event.x(), event.y(), event.button())) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void nyx$mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> info) {
        if (HUDManager.scaleHovered(mouseX, mouseY, scrollY)) {
            info.setReturnValue(true);
        }
    }
}
