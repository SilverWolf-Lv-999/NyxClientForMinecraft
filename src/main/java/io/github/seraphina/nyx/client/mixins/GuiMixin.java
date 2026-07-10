package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.module.visual.ModernGui;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
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

    @Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
    private void onRenderHearts(
        GuiGraphics guiGraphics,
        Player player,
        int x,
        int y,
        int rowSpacing,
        int regenHeart,
        float maxHealthDisplay,
        int health,
        int displayHealth,
        int absorption,
        boolean flashing,
        CallbackInfo info
    ) {
        if (!ModernGui.INSTANCE.shouldReplaceStatusHearts()) {
            return;
        }

        Render2DUtility.withGuiGraphics(guiGraphics, () ->
            ModernGui.INSTANCE.renderStatusBars(guiGraphics, player, x, y, rowSpacing, displayHealth, absorption, flashing)
        );
        info.cancel();
    }
}
