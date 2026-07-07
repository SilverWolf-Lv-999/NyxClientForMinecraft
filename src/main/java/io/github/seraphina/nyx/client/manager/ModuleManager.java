package io.github.seraphina.nyx.client.manager;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.client.ClickGui;
import io.github.seraphina.nyx.client.module.client.Client;
import io.github.seraphina.nyx.client.module.combat.KillAura;
import io.github.seraphina.nyx.client.module.movement.BHop;
import io.github.seraphina.nyx.client.module.movement.LowHop;
import io.github.seraphina.nyx.client.module.movement.Scaffold;
import io.github.seraphina.nyx.client.module.movement.Sprint;
import io.github.seraphina.nyx.client.module.other.Test;
import io.github.seraphina.nyx.client.module.player.DelayRemover;
import io.github.seraphina.nyx.client.module.player.FastPlace;
import io.github.seraphina.nyx.client.module.visual.Cape;

import java.util.*;
import java.util.stream.Collectors;

public final class ModuleManager {
    public static final Set<Module> MODULES = new LinkedHashSet<>();
    private static boolean initialized;

    private ModuleManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        //Client
        registerModule(
                ClickGui.INSTANCE,
                Client.INSTANCE
        );
        //Combat
        registerModule(
                KillAura.INSTANCE
        );
        //Movement
        registerModule(
                Scaffold.INSTANCE,
                BHop.INSTANCE,
                LowHop.INSTANCE,
                Sprint.INSTANCE
        );
        //Other
        registerModule(
                Test.INSTANCE
        );
        //Player
        registerModule(
                FastPlace.INSTANCE,
                DelayRemover.INSTANCE
        );
        //Visual
        registerModule(
                Cape.INSTANCE
        );

        initialized = true;
    }

    public static void registerModule(Module... module) {
        MODULES.addAll(Arrays.asList(module));
    }

    public static Set<Module> getModules() {
        return Collections.unmodifiableSet(MODULES);
    }

    public static List<Module> getModules(Category category) {
        return MODULES.stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toUnmodifiableList());
    }

    public static Optional<Module> getModule(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        String normalizedName = name.toLowerCase(Locale.ROOT);
        return MODULES.stream()
                .filter(module -> module.getConfigName().toLowerCase(Locale.ROOT).equals(normalizedName)
                        || module.getName().toLowerCase(Locale.ROOT).equals(normalizedName))
                .findFirst();
    }

    public static <T extends Module> Optional<T> getModule(Class<T> moduleClass) {
        if (moduleClass == null) {
            return Optional.empty();
        }

        return MODULES.stream()
                .filter(moduleClass::isInstance)
                .map(moduleClass::cast)
                .findFirst();
    }
}
