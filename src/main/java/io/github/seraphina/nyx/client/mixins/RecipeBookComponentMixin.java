package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.ModernGui;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow
    private int xOffset;

    @Shadow
    private int width;

    @Shadow
    private int height;

    @Shadow
    private boolean visible;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderModernRecipeBook(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        if (!this.visible || !ModernGui.INSTANCE.shouldReplaceContainers()) {
            return;
        }

        ModernGui.INSTANCE.beginRecipeBookTextureReplacement();
        Render2DUtility.withGuiGraphics(graphics, () ->
            ModernGui.INSTANCE.renderRecipeBookBackground(graphics, nyx$getXOrigin(), nyx$getYOrigin())
        );
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderModernRecipeBookReturn(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        ModernGui.INSTANCE.endRecipeBookTextureReplacement();
    }

    @Unique
    private int nyx$getXOrigin() {
        return (this.width - RecipeBookComponent.IMAGE_WIDTH) / 2 - this.xOffset;
    }

    @Unique
    private int nyx$getYOrigin() {
        return (this.height - RecipeBookComponent.IMAGE_HEIGHT) / 2;
    }
}
