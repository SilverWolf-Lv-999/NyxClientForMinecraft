package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TextEvent implements Event {
    private String text;

    public TextEvent(String text) {
        this.text = text;
    }

}
