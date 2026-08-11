package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class WindowsTitleEvent implements Event {
    private String title;

    public WindowsTitleEvent(String title) {
        this.title = title;
    }

}
