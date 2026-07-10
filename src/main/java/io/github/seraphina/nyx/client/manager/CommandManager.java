package io.github.seraphina.nyx.client.manager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.seraphina.nyx.client.command.NyxCommand;
import io.github.seraphina.nyx.client.command.impl.BindCommand;
import io.github.seraphina.nyx.client.command.impl.FriendCommand;
import io.github.seraphina.nyx.client.command.impl.ToggleCommand;
import io.github.seraphina.nyx.client.module.client.Client;
import io.github.seraphina.nyx.client.utility.IMinecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class CommandManager implements IMinecraft {
    private static final Set<NyxCommand> commands = new LinkedHashSet<>();
    private static final CommandDispatcher<ClientSuggestionProvider> dispatcher = new CommandDispatcher<>();
    private static boolean initialized;

    private CommandManager() {
        throw new RuntimeException("CommandManager can't be instantiated");
    }

    public static void registerCommand(NyxCommand... command) {
        Collections.addAll(commands, command);
        for (NyxCommand value : command) {
            dispatcher.getRoot().addChild(value.build().build());
        }
    }

    public static void init() {
        if (initialized) {
            return;
        }

        registerCommand(
                new BindCommand(),
                new FriendCommand(),
                new ToggleCommand()
        );

        initialized = true;
    }

    public static Set<NyxCommand> getCommands() {
        init();
        return Collections.unmodifiableSet(commands);
    }

    public static CommandDispatcher<ClientSuggestionProvider> getDispatcher() {
        init();
        return dispatcher;
    }

    public static String getPrefix() {
        String prefix = Client.INSTANCE.commandPrefix.getValue();
        return prefix == null ? "" : prefix;
    }

    public static boolean isCommandInput(String input) {
        String prefix = getPrefix();
        return input != null && !prefix.isEmpty() && input.startsWith(prefix);
    }

    public static boolean handleChatInput(String input, boolean addRecentChat) {
        if (!isCommandInput(input)) {
            return false;
        }

        if (addRecentChat)
            mc.gui.getChat().addRecentChat(input);

        execute(input);
        return true;
    }

    public static void execute(String input) {
        String prefix = getPrefix();
        if (input == null || prefix.isEmpty() || !input.startsWith(prefix)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        String commandLine = input.substring(prefix.length());
        try {
            getDispatcher().execute(commandLine, minecraft.player.connection.getSuggestionsProvider());
        } catch (CommandSyntaxException exception) {
            send(ComponentUtils.fromMessage(exception.getRawMessage()).copy().withStyle(ChatFormatting.RED));
        } catch (RuntimeException exception) {
            send(Component.literal(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
                    .withStyle(ChatFormatting.RED));
        }
    }

    public static void send(Component component) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.getChat().addMessage(component);
    }

    public static String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
