package io.github.seraphina.nyxclient.events.bus.listeners;

public interface IListener {
    void call(Object event);

    Class<?> getTarget();

    int getPriority();

    @Deprecated
    boolean isStatic();
}
