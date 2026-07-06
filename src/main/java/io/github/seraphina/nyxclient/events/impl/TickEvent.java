package io.github.seraphina.nyxclient.events.impl;

import io.github.seraphina.nyxclient.events.api.events.Event;

public class TickEvent implements Event {

    public static class Pre extends TickEvent {
    }

    public static class Post extends TickEvent {
    }

}
