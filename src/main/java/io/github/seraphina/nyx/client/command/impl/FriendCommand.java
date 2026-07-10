package io.github.seraphina.nyx.client.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.seraphina.nyx.client.command.CommandInfo;
import io.github.seraphina.nyx.client.command.NyxCommand;
import io.github.seraphina.nyx.client.manager.FriendManager;
import io.github.seraphina.nyx.client.utility.MsgUtility;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;

@CommandInfo(command = "friend")
public final class FriendCommand extends NyxCommand {
    @Override
    protected void configure(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> usage());

        builder.then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("list")
                .executes(context -> list()));

        builder.then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("add")
                .then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("name", StringArgumentType.word())
                        .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(
                                context.getSource().getOnlinePlayerNames().stream()
                                        .filter(name -> !FriendManager.isFriend(name)),
                                suggestions
                        ))
                        .executes(context -> add(getString(context, "name")))));

        builder.then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("remove")
                .then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("name", StringArgumentType.word())
                        .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(FriendManager.getFriends(), suggestions))
                        .executes(context -> remove(getString(context, "name")))));
    }

    private int usage() {
        MsgUtility.info("friend list | friend add <name> | friend remove <name>");
        return Command.SINGLE_SUCCESS;
    }

    private int list() {
        List<String> friends = FriendManager.getFriends();
        if (friends.isEmpty()) {
            MsgUtility.info("Friend list is empty.");
            return Command.SINGLE_SUCCESS;
        }

        MsgUtility.info("Friends: ", String.join(", ", friends));
        return Command.SINGLE_SUCCESS;
    }

    private int add(String name) {
        if (FriendManager.add(name)) {
            MsgUtility.info("Added friend: ", name);
        } else if (FriendManager.isFriend(name)) {
            MsgUtility.info(name, " is already a friend.");
        } else {
            MsgUtility.info("Invalid player name.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int remove(String name) {
        if (FriendManager.remove(name)) {
            MsgUtility.info("Removed friend: ", name);
        } else {
            MsgUtility.info(name, " is not in the friend list.");
        }

        return Command.SINGLE_SUCCESS;
    }
}
