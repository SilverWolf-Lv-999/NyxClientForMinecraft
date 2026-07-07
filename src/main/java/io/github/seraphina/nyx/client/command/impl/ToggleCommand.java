package io.github.seraphina.nyx.client.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.seraphina.nyx.client.command.CommandInfo;
import io.github.seraphina.nyx.client.command.NyxCommand;
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

@CommandInfo(command = "toggle")
public final class ToggleCommand extends NyxCommand {
    private static final DynamicCommandExceptionType UNKNOWN_MODULE =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown module: " + value));

    @Override
    protected void configure(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("module", word())
                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(
                        ModuleManager.getModules().stream().map(ToggleCommand::getCommandModuleName),
                        suggestions
                ))
                .executes(this::execute));
    }

    private int execute(com.mojang.brigadier.context.CommandContext<ClientSuggestionProvider> context) throws CommandSyntaxException {
        String moduleName = getString(context, "module");
        Module module = ModuleManager.getModule(moduleName)
                .orElseThrow(() -> UNKNOWN_MODULE.create(moduleName));

        module.toggle();
        ConfigManager.save();
        CommandManager.send(Component.literal("toggle " + getCommandModuleName(module) + " "
                + (module.isEnabled() ? "on" : "off")));
        return Command.SINGLE_SUCCESS;
    }

    private static String getCommandModuleName(Module module) {
        return module.getConfigName().toLowerCase(Locale.ROOT);
    }
}
