package io.github.seraphina.nyx.client.manager;

import io.github.seraphina.nyx.client.command.Command;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CommandManager {
    private static final Set<Command> languages = new HashSet<>();

    CommandManager() {
        throw new RuntimeException("CommandManager can't be instantiated");
    }

    public static void registerCommand(Command... command) {
        Collections.addAll(languages, command);
    }

    public static void init() {

    }
}
