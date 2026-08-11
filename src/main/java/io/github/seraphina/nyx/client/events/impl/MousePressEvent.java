package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.callables.EventCancellable;
import lombok.Getter;

@Getter
public class MousePressEvent extends EventCancellable {

    private final int button;
    private final int action;
    private final int modifiers;

    public MousePressEvent(int button, int action, int modifiers) {
        this.button = button;
        this.action = action;
        this.modifiers = modifiers;
    }

}
