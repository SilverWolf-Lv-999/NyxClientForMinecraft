package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.movementsync.name", description = "nyxclient.module.movementsync.description", category = Category.MOVEMENT)
public final class MovementSync extends Module {
    public static final MovementSync INSTANCE = new MovementSync();

    private MovementSync() {
    }
}
