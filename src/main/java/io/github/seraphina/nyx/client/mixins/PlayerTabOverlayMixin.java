package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.module.visual.ModernGui;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Shadow
    @Nullable
    private Component header;

    @Shadow
    @Nullable
    private Component footer;

    @Shadow
    private List<PlayerInfo> getPlayerInfos() {
        throw new AssertionError();
    }

    @Shadow
    public abstract Component getNameForDisplay(PlayerInfo playerInfo);

    @Inject(method = "setVisible", at = @At("RETURN"))
    private void onSetVisible(boolean visible, CallbackInfo info) {
        ModernGui.INSTANCE.onPlayerTabOverlayVisibilityChanged(visible);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphics graphics, int screenWidth, Scoreboard scoreboard, @Nullable Objective objective, CallbackInfo info) {
        if (!ModernGui.INSTANCE.shouldRenderPlayerTabOverlay()) {
            return;
        }

        Render2DUtility.withGuiGraphics(graphics, () ->
            ModernGui.INSTANCE.renderPlayerTabOverlay(
                graphics,
                screenWidth,
                scoreboard,
                objective,
                header,
                footer,
                getPlayerInfos(),
                this::getNameForDisplay
            )
        );
        info.cancel();
    }
}
