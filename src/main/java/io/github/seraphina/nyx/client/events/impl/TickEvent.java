package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;

public class TickEvent implements Event {

    public static class Pre extends TickEvent {
    }

    public static class Post extends TickEvent {
    }

}
