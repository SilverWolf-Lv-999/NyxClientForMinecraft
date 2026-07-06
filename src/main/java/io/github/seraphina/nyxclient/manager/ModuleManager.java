package io.github.seraphina.nyxclient.manager;

import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.movement.Scaffold;
import io.github.seraphina.nyxclient.module.other.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ModuleManager {
    public static final Set<Module> MODULES = new HashSet<>();

    public static void init() {
        //Client
        registerModule(

        );
        //Combat
        registerModule(

        );
        //Movement
        registerModule(
                Scaffold.INSTANCE
        );
        //Other
        registerModule(
                Test.INSTANCE
        );
        //Player
        registerModule(

        );
        //Visual
        registerModule(

        );
    }

    public static void registerModule(Module... module) {
        MODULES.addAll(Arrays.asList(module));
    }
}
