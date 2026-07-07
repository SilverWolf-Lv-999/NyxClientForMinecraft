package io.github.seraphina.nyx.client.events.bus;

public final class EventPriority {
    public static final int HIGHEST = 200;
    public static final int HIGH = 100;
    public static final int MEDIUM = 0;
    public static final int LOW = -100;
    public static final int LOWEST = -200;

    private EventPriority() {
    }
}
