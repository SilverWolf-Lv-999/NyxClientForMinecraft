package io.github.seraphina.nyx.client.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.seraphina.nyx.client.command.CommandInfo;
import io.github.seraphina.nyx.client.command.KeyNames;
import io.github.seraphina.nyx.client.manager.CommandManager;
import io.github.seraphina.nyx.client.manager.ConfigManager;
import io.github.seraphina.nyx.client.manager.ModuleManager;
import io.github.seraphina.nyx.client.module.Module;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

@CommandInfo(command = "bind")
public final class BindCommand extends io.github.seraphina.nyx.client.command.Command {
    private static final DynamicCommandExceptionType UNKNOWN_MODULE =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown module: " + value));
    private static final DynamicCommandExceptionType UNKNOWN_KEY =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown key: " + value));

    @Override
    protected void configure(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("module", word())
                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(
                        ModuleManager.getModules().stream().map(BindCommand::getCommandModuleName),
                        suggestions
                ))
                .then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("key", StringArgumentType.word())
                        .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(KeyNames.suggestions(), suggestions))
                        .executes(this::execute)));
    }

    private int execute(com.mojang.brigadier.context.CommandContext<ClientSuggestionProvider> context) throws CommandSyntaxException {
        String moduleName = getString(context, "module");
        Module module = ModuleManager.getModule(moduleName)
                .orElseThrow(() -> UNKNOWN_MODULE.create(moduleName));

        String keyName = getString(context, "key");
        String normalizedKeyName;
        int key;
        try {
            normalizedKeyName = KeyNames.getName(keyName);
            key = KeyNames.getKey(keyName);
        } catch (IllegalArgumentException exception) {
            throw UNKNOWN_KEY.create(keyName);
        }

        module.setKey(key);
        ConfigManager.save();
        CommandManager.send(Component.literal("bind " + getCommandModuleName(module) + " " + normalizedKeyName));
        return Command.SINGLE_SUCCESS;
    }

    private static String getCommandModuleName(Module module) {
        return module.getConfigName().toLowerCase(Locale.ROOT);
    }
}
