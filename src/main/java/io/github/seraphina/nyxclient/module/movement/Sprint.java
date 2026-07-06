package io.github.seraphina.nyxclient.module.movement;

import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.MoveInputEvent;
import io.github.seraphina.nyxclient.events.impl.StrafeEvent;
import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;
import io.github.seraphina.nyxclient.utility.player.MovingUtility;

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
