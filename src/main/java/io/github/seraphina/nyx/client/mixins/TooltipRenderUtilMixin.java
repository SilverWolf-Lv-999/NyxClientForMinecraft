package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.ModernGui;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilMixin {
    @Inject(method = "renderTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void onRenderModernTooltipBackground(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        @Nullable Identifier tooltipStyle,
        CallbackInfo info
    ) {
        Render2DUtility.withGuiGraphics(graphics, () -> {
            if (ModernGui.INSTANCE.renderTooltipBackground(graphics, x, y, width, height, tooltipStyle)) {
                info.cancel();
            }
        });
    }
}
