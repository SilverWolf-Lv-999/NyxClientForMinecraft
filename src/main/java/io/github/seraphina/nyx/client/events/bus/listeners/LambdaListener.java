package io.github.seraphina.nyx.client.events.bus.listeners;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.bus.EventHandler;
import io.github.seraphina.nyx.client.events.bus.EventPriority;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

public class LambdaListener implements IListener {
    public interface Factory {
        MethodHandles.Lookup create(Method lookupInMethod, Class<?> klass) throws InvocationTargetException, IllegalAccessException;
    }

    private static boolean java8;
    private static Constructor<MethodHandles.Lookup> lookupConstructor;
    private static Method privateLookupInMethod;

    private final Class<?> target;
    private final boolean isStatic;
    private final int priority;
    private final Consumer<Object> executor;

    @SuppressWarnings("unchecked")
    public LambdaListener(Factory factory, Class<?> klass, Object object, Method method) {
        this.target = method.getParameterTypes()[0];
        this.isStatic = Modifier.isStatic(method.getModifiers());
        this.priority = readPriority(method);

        try {
            MethodHandles.Lookup lookup;

            if (java8) {
                boolean accessible = lookupConstructor.isAccessible();
                lookupConstructor.setAccessible(true);
                lookup = lookupConstructor.newInstance(klass);
                lookupConstructor.setAccessible(accessible);
            } else {
                lookup = factory.create(privateLookupInMethod, klass);
            }

            MethodType methodType = MethodType.methodType(void.class, target);
            MethodHandle methodHandle;
            MethodType invokedType;

            if (isStatic) {
                methodHandle = lookup.findStatic(klass, method.getName(), methodType);
                invokedType = MethodType.methodType(Consumer.class);
            } else {
                methodHandle = lookup.findVirtual(klass, method.getName(), methodType);
                invokedType = MethodType.methodType(Consumer.class, klass);
            }

            MethodHandle lambdaFactory = LambdaMetafactory.metafactory(
                    lookup,
                    "accept",
                    invokedType,
                    MethodType.methodType(void.class, Object.class),
                    methodHandle,
                    methodType
            ).getTarget();

            if (isStatic) {
                this.executor = (Consumer<Object>) lambdaFactory.invoke();
            } else {
                this.executor = (Consumer<Object>) lambdaFactory.invoke(object);
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to create event listener for " + klass.getName() + "#" + method.getName(), throwable);
        }
    }

    @Override
    public void call(Object event) {
        executor.accept(event);
    }

    @Override
    public Class<?> getTarget() {
        return target;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    private static int readPriority(Method method) {
        EventHandler handler = method.getAnnotation(EventHandler.class);
        if (handler != null) {
            return handler.priority();
        }

        EventTarget target = method.getAnnotation(EventTarget.class);
        if (target == null) {
            return EventPriority.MEDIUM;
        }

        return switch (target.value()) {
            case 0 -> EventPriority.HIGHEST;
            case 1 -> EventPriority.HIGH;
            case 3 -> EventPriority.LOW;
            case 4 -> EventPriority.LOWEST;
            default -> EventPriority.MEDIUM;
        };
    }

    static {
        try {
            java8 = System.getProperty("java.version").startsWith("1.8");

            if (java8) {
                lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
            } else {
                privateLookupInMethod = MethodHandles.class.getDeclaredMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
            }
        } catch (NoSuchMethodException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
