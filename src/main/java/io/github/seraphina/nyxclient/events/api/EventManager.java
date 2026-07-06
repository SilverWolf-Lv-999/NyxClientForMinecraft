package io.github.seraphina.nyxclient.events.api;

import io.github.seraphina.nyxclient.events.api.events.Event;
import io.github.seraphina.nyxclient.events.bus.EventBus;

import java.lang.invoke.MethodHandles;

public final class EventManager {
    private EventManager() {
    }

    static {
        EventBus.INSTANCE.registerLambdaFactory(rootPackage(), (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
    }

    public static void register(Object object) {
        EventBus.INSTANCE.subscribe(object);
    }

    public static void register(Object object, Class<? extends Event> eventClass) {
        EventBus.INSTANCE.subscribe(object, eventClass);
    }

    public static void unregister(Object object) {
        EventBus.INSTANCE.unsubscribe(object);
    }

    public static void unregister(Object object, Class<? extends Event> eventClass) {
        EventBus.INSTANCE.unsubscribe(object, eventClass);
    }

    public static void removeEntry(Class<? extends Event> eventClass) {
        EventBus.INSTANCE.removeListeners(eventClass);
    }

    public static void cleanMap(boolean onlyEmptyEntries) {
        EventBus.INSTANCE.clean(onlyEmptyEntries);
    }

    public static Event call(Event event) {
        return EventBus.INSTANCE.post(event);
    }

    public static <T extends Event> T post(T event) {
        return EventBus.INSTANCE.post(event);
    }

    private static String rootPackage() {
        String packageName = EventManager.class.getPackageName();
        int eventsIndex = packageName.indexOf(".events");
        return eventsIndex == -1 ? packageName : packageName.substring(0, eventsIndex);
    }
}
