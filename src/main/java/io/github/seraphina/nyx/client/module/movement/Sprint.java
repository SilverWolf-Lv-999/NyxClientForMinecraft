package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.sprint.name", description = "nyxclient.module.sprint.description", category = Category.MOVEMENT)
public class Sprint extends Module {
    public static final Sprint INSTANCE = new Sprint();

    public Sprint() {

    }

    @EventTarget
    public void onInput(MoveInputEvent event) {
        event.setSprint(true);
    }
}
