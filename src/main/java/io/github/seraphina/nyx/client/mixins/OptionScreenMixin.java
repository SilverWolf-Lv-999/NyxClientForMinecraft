package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.ui.mainui.MainUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class OptionScreenMixin extends Screen {
    @Unique
    private static final long NYX_SLIDE_DURATION_NANOS = 260_000_000L;

    @Shadow
    @Final
    private Screen lastScreen;

    @Unique
    private long nyx$slideStartedAtNanos;

    @Unique
    private boolean nyx$barrelOpenSoundPlayed;

    protected OptionScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void nyx$startMainUiTransition(CallbackInfo info) {
        if (!(this.lastScreen instanceof MainUI)) {
            return;
        }

        this.nyx$slideStartedAtNanos = System.nanoTime();
        if (!this.nyx$barrelOpenSoundPlayed) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BARREL_OPEN, 1.0F, 0.7F));
            }
            this.nyx$barrelOpenSoundPlayed = true;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.nyx$shouldSlideIn()) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        float progress = this.nyx$slideProgress();
        if (progress >= 1.0F) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        float yOffset = -this.height * (1.0F - this.nyx$easeOutCubic(progress));
        guiGraphics.pose().pushMatrix();
        try {
            guiGraphics.pose().translate(0.0F, yOffset);
            super.render(guiGraphics, mouseX, Math.round(mouseY - yOffset), partialTick);
        } finally {
            guiGraphics.pose().popMatrix();
        }
    }

    @Unique
    private boolean nyx$shouldSlideIn() {
        return this.lastScreen instanceof MainUI && this.nyx$slideStartedAtNanos > 0L;
    }

    @Unique
    private float nyx$slideProgress() {
        long elapsedNanos = System.nanoTime() - this.nyx$slideStartedAtNanos;
        return Math.max(0.0F, Math.min(1.0F, elapsedNanos / (float)NYX_SLIDE_DURATION_NANOS));
    }

    @Unique
    private float nyx$easeOutCubic(float value) {
        float inverse = 1.0F - Math.max(0.0F, Math.min(1.0F, value));
        return 1.0F - inverse * inverse * inverse;
    }
}
