package io.github.seraphina.nyxclient.mixins;

import io.github.seraphina.nyxclient.events.bus.EventBus;
import io.github.seraphina.nyxclient.events.impl.Render2DEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo info) {
        EventBus.INSTANCE.post(new Render2DEvent.Level(guiGraphics));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo info) {
        EventBus.INSTANCE.post(new Render2DEvent.HUD(guiGraphics));
    }
}
