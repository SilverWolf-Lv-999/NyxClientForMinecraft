package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.ModernGui;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
    @Shadow
    @Final
    private AbstractContainerScreen<?> screen;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderModernInventoryEffects(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo info) {
        if (!ModernGui.INSTANCE.shouldReplaceInventoryPotionEffects()) {
            return;
        }

        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor)screen;
        Render2DUtility.withGuiGraphics(graphics, () ->
            ModernGui.INSTANCE.renderInventoryPotionEffects(
                graphics,
                screen,
                screen.width,
                accessor.nyx$getLeftPos(),
                accessor.nyx$getTopPos(),
                accessor.nyx$getImageWidth(),
                mouseX,
                mouseY
            )
        );
        info.cancel();
    }
}
