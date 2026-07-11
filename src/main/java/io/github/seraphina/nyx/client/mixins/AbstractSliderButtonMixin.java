package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.ModernGui;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin extends AbstractWidget {
    @Shadow
    protected double value;

    @Shadow
    protected boolean dragging;

    protected AbstractSliderButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void onRenderModernSlider(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        Render2DUtility.withGuiGraphics(graphics, () -> {
            if (ModernGui.INSTANCE.renderSlider(graphics, (AbstractSliderButton)(Object)this, this.value, this.dragging, this.alpha)) {
                info.cancel();
            }
        });
    }
}
