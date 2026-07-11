package io.github.seraphina.nyx.client.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.Render2DEvent;
import io.github.seraphina.nyx.client.module.visual.ModernGui;
import io.github.seraphina.nyx.client.module.visual.NoRenderer;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Shadow
    public int leftHeight;

    @Shadow
    public int rightHeight;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo info) {
        EventBus.INSTANCE.post(new Render2DEvent.Level(guiGraphics));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo info) {
        EventBus.INSTANCE.post(new Render2DEvent.HUD(guiGraphics));
    }

    @WrapOperation(
        method = "renderCameraOverlays",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V",
            ordinal = 0
        )
    )
    private void renderEquipmentCameraOverlay(
        Gui instance,
        GuiGraphics guiGraphics,
        Identifier texture,
        float alpha,
        Operation<Void> original
    ) {
        if (!NoRenderer.INSTANCE.shouldDisablePumpkinOverlay(texture)) {
            original.call(instance, guiGraphics, texture, alpha);
        }
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

    @Inject(method = "renderArmorLevel", at = @At("HEAD"), cancellable = true)
    private void onRenderArmorLevel(GuiGraphics guiGraphics, CallbackInfo info) {
        if (!ModernGui.INSTANCE.shouldReplaceStatusBars()) {
            return;
        }

        Player player = nyx$getCameraPlayer();
        if (player == null) {
            info.cancel();
            return;
        }

        if (player.getArmorValue() > 0) {
            int x = guiGraphics.guiWidth() / 2 - 91;
            int y = guiGraphics.guiHeight() - leftHeight;
            Render2DUtility.withGuiGraphics(guiGraphics, () ->
                ModernGui.INSTANCE.renderArmorBar(guiGraphics, player, x, y)
            );
            leftHeight += 10;
        }

        info.cancel();
    }

    @Inject(method = "renderFoodLevel", at = @At("HEAD"), cancellable = true)
    private void onRenderFoodLevel(GuiGraphics guiGraphics, CallbackInfo info) {
        if (!ModernGui.INSTANCE.shouldReplaceStatusBars()) {
            return;
        }

        Player player = nyx$getCameraPlayer();
        if (player == null) {
            info.cancel();
            return;
        }

        if (!nyx$hasVehicleHealth(player)) {
            int rightEdge = guiGraphics.guiWidth() / 2 + 91;
            int y = guiGraphics.guiHeight() - rightHeight;
            Render2DUtility.withGuiGraphics(guiGraphics, () ->
                ModernGui.INSTANCE.renderFoodBar(guiGraphics, player, rightEdge, y)
            );
            rightHeight += 10;
        }

        info.cancel();
    }

    private Player nyx$getCameraPlayer() {
        return Minecraft.getInstance().getCameraEntity() instanceof Player player ? player : null;
    }

    private boolean nyx$hasVehicleHealth(Player player) {
        if (player.getVehicle() instanceof LivingEntity livingEntity && livingEntity.showVehicleHealth()) {
            return (int)(livingEntity.getMaxHealth() + 0.5F) / 2 > 0;
        }

        return false;
    }
}
