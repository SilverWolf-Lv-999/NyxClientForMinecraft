package io.github.seraphina.nyx.client.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.seraphina.nyx.client.command.CommandInfo;
import io.github.seraphina.nyx.client.command.NyxCommand;
import io.github.seraphina.nyx.client.manager.ConfigManager;
import io.github.seraphina.nyx.client.utility.MsgUtility;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;

@CommandInfo(command = "config")
public final class ConfigCommand extends NyxCommand {
    private static final DynamicCommandExceptionType INVALID_CONFIG_NAME =
            new DynamicCommandExceptionType(value -> Component.literal(
                    "Invalid config name: " + value + ". Use letters, numbers, '_' or '-'."));
    private static final DynamicCommandExceptionType CONFIG_EXISTS =
            new DynamicCommandExceptionType(value -> Component.literal("Config already exists: " + value));
    private static final DynamicCommandExceptionType UNKNOWN_CONFIG =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown config: " + value));

    @Override
    protected void configure(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> usage());

        builder.then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("list")
                .executes(context -> list()));

        builder.then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("create")
                .then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("name", StringArgumentType.word())
                        .executes(context -> create(getString(context, "name")))));

        builder.then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("load")
                .then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("name", StringArgumentType.word())
                        .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(
                                ConfigManager.getConfigNames(),
                                suggestions
                        ))
                        .executes(context -> load(getString(context, "name")))));
    }

    private int usage() {
        MsgUtility.info("config list | config create <name> | config load <name>");
        return Command.SINGLE_SUCCESS;
    }

    private int list() {
        String selectedName = ConfigManager.getSelectedConfigName();
        List<String> configs = ConfigManager.getConfigNames().stream()
                .map(name -> name.equals(selectedName) ? name + " *" : name)
                .toList();
        MsgUtility.info("Configs: ", String.join(", ", configs));
        return Command.SINGLE_SUCCESS;
    }

    private int create(String name) throws CommandSyntaxException {
        if (!ConfigManager.isValidConfigName(name)) {
            throw INVALID_CONFIG_NAME.create(name);
        }

        if (ConfigManager.exists(name)) {
            throw CONFIG_EXISTS.create(name);
        }

        ConfigManager.create(name);
        MsgUtility.info("Created and selected config: ", ConfigManager.getSelectedConfigName());
        return Command.SINGLE_SUCCESS;
    }

    private int load(String name) throws CommandSyntaxException {
        if (!ConfigManager.isValidConfigName(name)) {
            throw INVALID_CONFIG_NAME.create(name);
        }

        if (!ConfigManager.exists(name)) {
            throw UNKNOWN_CONFIG.create(name);
        }

        ConfigManager.load(name);
        MsgUtility.info("Loaded config: ", ConfigManager.getSelectedConfigName());
        return Command.SINGLE_SUCCESS;
    }
}
