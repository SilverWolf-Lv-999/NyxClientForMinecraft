package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.ModernGui;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> extends Screen {
    @Shadow
    protected int imageWidth;

    @Shadow
    protected int imageHeight;

    @Shadow
    protected int titleLabelX;

    @Shadow
    protected int titleLabelY;

    @Shadow
    protected int inventoryLabelX;

    @Shadow
    protected int inventoryLabelY;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    @Final
    protected Component playerInventoryTitle;

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    public abstract T getMenu();

    @Inject(
        method = "renderBackground",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"
        )
    )
    private void onRenderModernContainerBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        if (!ModernGui.INSTANCE.shouldReplaceContainers()) {
            return;
        }

        ModernGui.INSTANCE.beginContainerTextureReplacement();
        Render2DUtility.withGuiGraphics(graphics, () ->
            ModernGui.INSTANCE.renderContainerBackground(graphics, leftPos, topPos, imageWidth, imageHeight, getMenu().slots)
        );
    }

    @Inject(method = "renderBackground", at = @At("RETURN"))
    private void onRenderModernContainerBackgroundReturn(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        ModernGui.INSTANCE.endContainerTextureReplacement();
    }

    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    private void onRenderModernContainerLabels(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo info) {
        if (!ModernGui.INSTANCE.shouldReplaceContainers()) {
            return;
        }

        Render2DUtility.withGuiGraphics(graphics, () ->
            ModernGui.INSTANCE.renderContainerLabels(
                graphics,
                title,
                playerInventoryTitle,
                titleLabelX,
                titleLabelY,
                inventoryLabelX,
                inventoryLabelY,
                imageWidth
            )
        );
        info.cancel();
    }

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void onRenderModernContainerSlot(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!ModernGui.INSTANCE.shouldReplaceContainers()) {
            return;
        }

        Render2DUtility.withGuiGraphics(graphics, () ->
            ModernGui.INSTANCE.renderContainerSlot(graphics, slot, slot == hoveredSlot)
        );
    }

    @Inject(method = "renderSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void onRenderModernContainerSlotHighlightBack(GuiGraphics graphics, CallbackInfo info) {
        if (!ModernGui.INSTANCE.shouldReplaceContainers()) {
            return;
        }

        info.cancel();
    }

    @Inject(method = "renderSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void onRenderModernContainerSlotHighlightFront(GuiGraphics graphics, CallbackInfo info) {
        if (!ModernGui.INSTANCE.shouldReplaceContainers()) {
            return;
        }

        Render2DUtility.withGuiGraphics(graphics, () ->
            ModernGui.INSTANCE.renderContainerSlotHighlight(graphics, hoveredSlot)
        );
        info.cancel();
    }
}
