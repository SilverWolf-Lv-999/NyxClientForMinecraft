package io.github.seraphina.nyx.client.events.bus;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.api.events.Cancellable;
import io.github.seraphina.nyx.client.events.api.events.EventStoppable;
import io.github.seraphina.nyx.client.events.bus.listeners.IListener;
import io.github.seraphina.nyx.client.events.bus.listeners.LambdaListener;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class EventBus {
    public static final EventBus INSTANCE = new EventBus();

    private static final LambdaListener.Factory DEFAULT_FACTORY = (lookupInMethod, klass) ->
            (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup());

    private final Map<Object, List<IListener>> listenerCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<IListener>> staticListenerCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<IListener>> listenerMap = new ConcurrentHashMap<>();
    private final List<LambdaFactoryInfo> lambdaFactoryInfos = new ArrayList<>();

    public void registerLambdaFactory(String packagePrefix, LambdaListener.Factory factory) {
        synchronized (lambdaFactoryInfos) {
            lambdaFactoryInfos.add(new LambdaFactoryInfo(packagePrefix, factory));
        }
    }

    public boolean isListening(Class<?> eventClass) {
        List<IListener> listeners = listenerMap.get(eventClass);
        return listeners != null && !listeners.isEmpty();
    }

    public <T> T post(T event) {
        List<IListener> listeners = listenerMap.get(event.getClass());

        if (event instanceof Cancellable cancellable) {
            cancellable.setCancelled(false);
        }

        if (listeners != null) {
            for (IListener listener : listeners) {
                listener.call(event);

                if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                    break;
                }

                if (event instanceof EventStoppable stoppable && stoppable.isStopped()) {
                    break;
                }
            }
        }

        return event;
    }

    public void subscribe(Object object) {
        subscribe(getListeners(object.getClass(), object), false, null);
    }

    public void subscribe(Object object, Class<?> eventClass) {
        subscribe(getListeners(object.getClass(), object), false, eventClass);
    }

    public void subscribe(Class<?> klass) {
        subscribe(getListeners(klass, null), true, null);
    }

    public void subscribe(IListener listener) {
        subscribe(listener, false);
    }

    private void subscribe(List<IListener> listeners, boolean onlyStatic, Class<?> eventClass) {
        for (IListener listener : listeners) {
            if (eventClass == null || listener.getTarget().equals(eventClass)) {
                subscribe(listener, onlyStatic);
            }
        }
    }

    private void subscribe(IListener listener, boolean onlyStatic) {
        if (!onlyStatic || listener.isStatic()) {
            insert(listenerMap.computeIfAbsent(listener.getTarget(), ignored -> new CopyOnWriteArrayList<>()), listener);
        }
    }

    private void insert(List<IListener> listeners, IListener listener) {
        int index = 0;
        for (; index < listeners.size(); index++) {
            if (listener.getPriority() > listeners.get(index).getPriority()) {
                break;
            }
        }

        listeners.add(index, listener);
    }

    public void unsubscribe(Object object) {
        unsubscribe(getListeners(object.getClass(), object), false, null);
    }

    public void unsubscribe(Object object, Class<?> eventClass) {
        unsubscribe(getListeners(object.getClass(), object), false, eventClass);
    }

    public void unsubscribe(Class<?> klass) {
        unsubscribe(getListeners(klass, null), true, null);
    }

    public void unsubscribe(IListener listener) {
        unsubscribe(listener, false);
    }

    private void unsubscribe(List<IListener> listeners, boolean onlyStatic, Class<?> eventClass) {
        for (IListener listener : listeners) {
            if (eventClass == null || listener.getTarget().equals(eventClass)) {
                unsubscribe(listener, onlyStatic);
            }
        }
    }

    private void unsubscribe(IListener listener, boolean onlyStatic) {
        List<IListener> listeners = listenerMap.get(listener.getTarget());

        if (listeners != null && (!onlyStatic || listener.isStatic())) {
            listeners.remove(listener);
        }
    }

    public void removeListeners(Class<?> eventClass) {
        listenerMap.remove(eventClass);
    }

    public void clean(boolean onlyEmptyEntries) {
        Iterator<Map.Entry<Class<?>, List<IListener>>> iterator = listenerMap.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Class<?>, List<IListener>> entry = iterator.next();
            if (!onlyEmptyEntries || entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private List<IListener> getListeners(Class<?> klass, Object object) {
        Function<Object, List<IListener>> factory = ignored -> {
            List<IListener> listeners = new CopyOnWriteArrayList<>();
            collectListeners(listeners, klass, object);
            return listeners;
        };

        if (object == null) {
            return staticListenerCache.computeIfAbsent(klass, factory);
        }

        for (Object key : listenerCache.keySet()) {
            if (key == object) {
                return listenerCache.get(key);
            }
        }

        List<IListener> listeners = factory.apply(object);
        listenerCache.put(object, listeners);
        return listeners;
    }

    private void collectListeners(List<IListener> listeners, Class<?> klass, Object object) {
        for (Method method : klass.getDeclaredMethods()) {
            if (isValid(method)) {
                listeners.add(new LambdaListener(getLambdaFactory(klass), klass, object, method));
            }
        }

        if (klass.getSuperclass() != null) {
            collectListeners(listeners, klass.getSuperclass(), object);
        }
    }

    private boolean isValid(Method method) {
        if (!method.isAnnotationPresent(EventHandler.class) && !method.isAnnotationPresent(EventTarget.class)) {
            return false;
        }

        if (method.getReturnType() != void.class) {
            return false;
        }

        if (method.getParameterCount() != 1) {
            return false;
        }

        return !method.getParameterTypes()[0].isPrimitive();
    }

    private LambdaListener.Factory getLambdaFactory(Class<?> klass) {
        synchronized (lambdaFactoryInfos) {
            for (LambdaFactoryInfo info : lambdaFactoryInfos) {
                if (klass.getName().startsWith(info.packagePrefix())) {
                    return info.factory();
                }
            }
        }

        return DEFAULT_FACTORY;
    }

    private record LambdaFactoryInfo(String packagePrefix, LambdaListener.Factory factory) {
    }
}
