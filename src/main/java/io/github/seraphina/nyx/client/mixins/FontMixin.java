package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.utility.MsgUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public abstract class FontMixin {
    @Inject(
        method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void nyx$prepareDebugText(FormattedCharSequence text, float x, float y, int color, boolean drawShadow,
                                      boolean includeEmpty, int backgroundColor, CallbackInfoReturnable<Font.PreparedText> cir) {
        if (nyx$isDebugSequence(text)) {
            cir.setReturnValue(FontRenderer.prepareDebugChatText(text, x, y, color, drawShadow, includeEmpty, backgroundColor));
        }
    }

    @Inject(
        method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void nyx$debugTextWidth(FormattedCharSequence text, CallbackInfoReturnable<Integer> cir) {
        if (nyx$isDebugSequence(text)) {
            cir.setReturnValue(FontRenderer.debugChatWidth(text));
        }
    }

    private static boolean nyx$isDebugSequence(FormattedCharSequence text) {
        StringBuilder prefix = new StringBuilder(MsgUtility.PREFIX.length());
        boolean[] hasDebugFont = {false};
        text.accept((index, style, codePoint) -> {
            if (nyx$isDebugStyle(style)) {
                hasDebugFont[0] = true;
            }
            if (prefix.length() < MsgUtility.PREFIX.length()) {
                prefix.appendCodePoint(codePoint);
            }
            return true;
        });
        return hasDebugFont[0] || prefix.toString().startsWith(MsgUtility.PREFIX);
    }

    private static boolean nyx$isDebugStyle(Style style) {
        Integer shadowColor = style.getShadowColor();
        return shadowColor != null && shadowColor == MsgUtility.DEBUG_SHADOW_COLOR;
    }
}
