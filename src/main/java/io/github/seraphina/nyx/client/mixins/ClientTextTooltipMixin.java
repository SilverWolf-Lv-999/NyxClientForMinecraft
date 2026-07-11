package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.ModernGui;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientTextTooltip.class)
public abstract class ClientTextTooltipMixin {
    @Shadow
    @Final
    private FormattedCharSequence text;

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void onGetModernTooltipTextWidth(Font font, CallbackInfoReturnable<Integer> info) {
        if (ModernGui.INSTANCE.shouldReplaceTooltips()) {
            info.setReturnValue(ModernGui.INSTANCE.modernTooltipTextWidth(this.text));
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void onGetModernTooltipTextHeight(Font font, CallbackInfoReturnable<Integer> info) {
        if (ModernGui.INSTANCE.shouldReplaceTooltips()) {
            info.setReturnValue(ModernGui.INSTANCE.modernTooltipTextHeight());
        }
    }

    @Inject(method = "renderText", at = @At("HEAD"), cancellable = true)
    private void onRenderModernTooltipText(GuiGraphics graphics, Font font, int x, int y, CallbackInfo info) {
        if (!ModernGui.INSTANCE.shouldReplaceTooltips()) {
            return;
        }

        Render2DUtility.withGuiGraphics(graphics, () -> ModernGui.INSTANCE.renderTooltipText(this.text, x, y));
        info.cancel();
    }
}
