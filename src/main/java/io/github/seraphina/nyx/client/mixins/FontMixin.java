package io.github.seraphina.nyx.client.mixins;

import io.github.seraphina.nyx.client.events.bus.EventBus;
import io.github.seraphina.nyx.client.events.impl.TextEvent;
import io.github.seraphina.nyx.client.utility.MsgUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Font.class)
public abstract class FontMixin {
    private static final ThreadLocal<Boolean> NYX_PROCESSING_TEXT_EVENT = ThreadLocal.withInitial(() -> false);

    @ModifyVariable(
        method = {
            "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            "width(Ljava/lang/String;)I",
            "plainSubstrByWidth(Ljava/lang/String;I)Ljava/lang/String;",
            "plainSubstrByWidth(Ljava/lang/String;IZ)Ljava/lang/String;"
        },
        at = @At("HEAD"),
        argsOnly = true
    )
    private String nyx$modifyStringText(String text) {
        return nyx$postTextEvent(text);
    }

    @ModifyVariable(
        method = {
            "width(Lnet/minecraft/network/chat/FormattedText;)I",
            "substrByWidth(Lnet/minecraft/network/chat/FormattedText;I)Lnet/minecraft/network/chat/FormattedText;",
            "wordWrapHeight(Lnet/minecraft/network/chat/FormattedText;I)I",
            "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
            "splitIgnoringLanguage(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"
        },
        at = @At("HEAD"),
        argsOnly = true
    )
    private FormattedText nyx$modifyFormattedText(FormattedText text) {
        if (!nyx$shouldPostTextEvent()) {
            return text;
        }

        String original = text.getString();
        String modified = nyx$postTextEvent(original);
        if (original.equals(modified)) {
            return text;
        }

        Style style = nyx$firstStyle(text);
        return FormattedText.of(modified, style);
    }

    @Dynamic("NeoForge patches Font#prepareText(FormattedCharSequence, float, float, int, boolean, int).")
    @SuppressWarnings({"UnresolvedMixinReference", "MixinAnnotationTarget"})
    @ModifyVariable(
        method = {
            "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            "width(Lnet/minecraft/util/FormattedCharSequence;)I",
            "drawInBatch8xOutline(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
        },
        at = @At("HEAD"),
        argsOnly = true
    )
    private FormattedCharSequence nyx$modifyFormattedCharSequence(FormattedCharSequence text) {
        if (!nyx$shouldPostTextEvent() || nyx$isDebugSequence(text)) {
            return text;
        }

        CapturedFormattedCharSequence captured = nyx$capture(text);
        String modified = nyx$postTextEvent(captured.text());
        if (captured.text().equals(modified)) {
            return text;
        }

        return sink -> StringDecomposer.iterateFormatted(modified, captured.style(), sink);
    }

    @Dynamic("NeoForge patches Font#prepareText(FormattedCharSequence, float, float, int, boolean, int).")
    @SuppressWarnings({"UnresolvedMixinReference", "MixinAnnotationTarget"})
    @Inject(
        method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void nyx$prepareDebugText(FormattedCharSequence text, float x, float y, int color, boolean drawShadow,
                                      int backgroundColor, CallbackInfoReturnable<Font.PreparedText> cir) {
        if (nyx$isDebugSequence(text)) {
            cir.setReturnValue(FontRenderer.prepareDebugChatText(text, x, y, color, drawShadow, false, backgroundColor));
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

    private static boolean nyx$shouldPostTextEvent() {
        return !NYX_PROCESSING_TEXT_EVENT.get() && EventBus.INSTANCE.isListening(TextEvent.class);
    }

    private static String nyx$postTextEvent(String text) {
        if (text == null || !nyx$shouldPostTextEvent()) {
            return text;
        }

        NYX_PROCESSING_TEXT_EVENT.set(true);
        try {
            String modified = EventBus.INSTANCE.post(new TextEvent(text)).getText();
            return modified == null ? "" : modified;
        } finally {
            NYX_PROCESSING_TEXT_EVENT.set(false);
        }
    }

    private static Style nyx$firstStyle(FormattedText text) {
        Style[] style = {Style.EMPTY};
        boolean[] found = {false};
        text.visit((currentStyle, value) -> {
            if (!found[0]) {
                style[0] = currentStyle;
                found[0] = true;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return style[0];
    }

    private static CapturedFormattedCharSequence nyx$capture(FormattedCharSequence text) {
        StringBuilder builder = new StringBuilder();
        Style[] style = {Style.EMPTY};
        boolean[] found = {false};
        text.accept((index, currentStyle, codePoint) -> {
            if (!found[0]) {
                style[0] = currentStyle;
                found[0] = true;
            }
            builder.appendCodePoint(codePoint);
            return true;
        });
        return new CapturedFormattedCharSequence(builder.toString(), style[0]);
    }

    private record CapturedFormattedCharSequence(String text, Style style) {
    }
}
