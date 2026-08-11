package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.callables.EventCancellable;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.screens.Screen;

@Setter
@Getter
public class SetScreenEvent extends EventCancellable {
    private Screen screen;
    public SetScreenEvent(Screen screen) {
        this.screen = screen;
    }

}
