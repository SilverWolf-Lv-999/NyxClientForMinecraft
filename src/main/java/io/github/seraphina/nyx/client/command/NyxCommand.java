package io.github.seraphina.nyx.client.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.util.Locale;

public abstract class NyxCommand {
    private final String name;

    protected NyxCommand() {
        CommandInfo info = this.getClass().getAnnotation(CommandInfo.class);
        String commandName = info == null ? "" : info.command();
        if (commandName == null || commandName.isBlank()) {
            commandName = this.getClass().getSimpleName();
            if (commandName.endsWith("Command")) {
                commandName = commandName.substring(0, commandName.length() - "Command".length());
            }
        }

        this.name = commandName.toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return this.name;
    }

    public LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        LiteralArgumentBuilder<ClientSuggestionProvider> builder = LiteralArgumentBuilder.literal(this.name);
        this.configure(builder);
        return builder;
    }

    protected abstract void configure(LiteralArgumentBuilder<ClientSuggestionProvider> builder);
}
