package io.github.seraphina.nyx.client.mixins;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import io.github.seraphina.nyx.client.manager.CommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private EditBox input;

    @Shadow
    @Final
    private boolean onlyShowIfCursorPastError;

    @Shadow
    @Final
    private List<FormattedCharSequence> commandUsage;

    @Shadow
    @Nullable
    private ParseResults<ClientSuggestionProvider> currentParse;

    @Shadow
    @Nullable
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    @Nullable
    private CommandSuggestions.SuggestionsList suggestions;

    @Shadow
    private boolean keepSuggestions;

    @Shadow
    private void updateUsageInfo() {
    }

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void nyx$updateClientCommandInfo(CallbackInfo info) {
        String value = this.input.getValue();
        if (!CommandManager.isCommandInput(value)) {
            return;
        }

        if (this.minecraft.player == null || this.minecraft.player.connection == null) {
            return;
        }

        if (this.currentParse != null && !this.currentParse.getReader().getString().equals(value)) {
            this.currentParse = null;
        }

        if (!this.keepSuggestions) {
            this.input.setSuggestion(null);
            this.suggestions = null;
        }

        this.commandUsage.clear();
        String prefix = CommandManager.getPrefix();
        StringReader reader = new StringReader(value);
        for (int index = 0; index < prefix.length() && reader.canRead(); index++) {
            reader.skip();
        }

        CommandDispatcher<ClientSuggestionProvider> commandDispatcher = CommandManager.getDispatcher();
        ClientSuggestionProvider suggestionProvider = this.minecraft.player.connection.getSuggestionsProvider();
        if (this.currentParse == null) {
            this.currentParse = commandDispatcher.parse(reader, suggestionProvider);
        }

        int cursor = this.input.getCursorPosition();
        int startCursor = this.onlyShowIfCursorPastError ? reader.getCursor() : prefix.length();
        if (cursor >= startCursor && (this.suggestions == null || !this.keepSuggestions)) {
            this.pendingSuggestions = commandDispatcher.getCompletionSuggestions(this.currentParse, cursor);
            this.pendingSuggestions.thenRun(() -> {
                if (this.pendingSuggestions.isDone()) {
                    this.updateUsageInfo();
                }
            });
        }

        info.cancel();
    }
}
