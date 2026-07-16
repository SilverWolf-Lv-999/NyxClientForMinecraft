package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.loading.NyxLoadingOverlayRenderer;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Unique
    private static final int NYX$BACKGROUND = 0xFF070A12;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private Consumer<Optional<Throwable>> onFinish;

    @Shadow
    @Final
    private boolean fadeIn;

    @Shadow
    private float currentProgress;

    @Shadow
    private long fadeOutStart;

    @Shadow
    private long fadeInStart;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void nyx$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        long millis = Util.getMillis();
        if (this.fadeIn && this.fadeInStart == -1L) {
            this.fadeInStart = millis;
        }

        float fadeOut = this.fadeOutStart > -1L ? (millis - this.fadeOutStart) / 1000.0F : -1.0F;
        float fadeInProgress = this.fadeInStart > -1L ? (millis - this.fadeInStart) / 500.0F : -1.0F;
        float contentOpacity;
        int backgroundAlpha;
        if (fadeOut >= 1.0F) {
            if (this.minecraft.screen != null) {
                this.minecraft.screen.render(graphics, 0, 0, partialTick);
            }
            contentOpacity = 1.0F - Mth.clamp(fadeOut - 1.0F, 0.0F, 1.0F);
            backgroundAlpha = Mth.ceil(contentOpacity * 255.0F);
        } else if (this.fadeIn) {
            if (this.minecraft.screen != null && fadeInProgress < 1.0F) {
                this.minecraft.screen.render(graphics, mouseX, mouseY, partialTick);
            }
            contentOpacity = Mth.clamp(fadeInProgress, 0.0F, 1.0F);
            backgroundAlpha = Mth.ceil(Mth.clamp((double)fadeInProgress, 0.15D, 1.0D) * 255.0D);
        } else {
            contentOpacity = 1.0F;
            backgroundAlpha = 255;
        }

        graphics.fill(RenderPipelines.GUI, 0, 0, width, height, nyx$replaceAlpha(NYX$BACKGROUND, backgroundAlpha));
        float actualProgress = this.reload.getActualProgress();
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + actualProgress * 0.050000012F, 0.0F, 1.0F);
        if (fadeOut < 2.0F) {
            Render2DUtility.withGuiGraphics(graphics, () ->
                NyxLoadingOverlayRenderer.render(width, height, this.currentProgress, contentOpacity, millis)
            );
        }

        if (fadeOut >= 2.0F) {
            this.minecraft.setOverlay(null);
        }

        if (this.fadeOutStart == -1L && this.reload.isDone() && (!this.fadeIn || fadeInProgress >= 2.0F)) {
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable throwable) {
                this.onFinish.accept(Optional.of(throwable));
            }

            this.fadeOutStart = Util.getMillis();
            if (this.minecraft.screen != null) {
                this.minecraft.screen.init(graphics.guiWidth(), graphics.guiHeight());
            }
        }

        info.cancel();
    }

    @Unique
    private static int nyx$replaceAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }
}
