package io.github.seraphina.nyx.client.utility;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import net.minecraft.util.ClassInstanceMultiMap;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConcurrentClassInstanceMultiMap<T> extends ClassInstanceMultiMap<T> {
    private final Map<Class<?>, CopyOnWriteArrayList<T>> byClass = new ConcurrentHashMap<>();
    private final Class<T> baseClass;
    private final CopyOnWriteArrayList<T> allInstances = new CopyOnWriteArrayList<>();

    public ConcurrentClassInstanceMultiMap(Class<T> baseClass) {
        super(baseClass);
        this.baseClass = baseClass;
        byClass.put(baseClass, allInstances);
    }

    @Override
    public synchronized boolean add(T value) {
        boolean changed = false;
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<T>> entry : byClass.entrySet()) {
            if (entry.getKey().isInstance(value)) {
                changed |= entry.getValue().add(value);
            }
        }
        return changed;
    }

    @Override
    public synchronized boolean remove(Object value) {
        boolean changed = false;
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<T>> entry : byClass.entrySet()) {
            if (entry.getKey().isInstance(value)) {
                changed |= entry.getValue().remove(value);
            }
        }
        return changed;
    }

    @Override
    public synchronized boolean contains(Object value) {
        return find(value.getClass()).contains(value);
    }

    @Override
    public synchronized <S> Collection<S> find(Class<S> clazz) {
        if (!baseClass.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Don't know how to search for " + clazz);
        }

        List<T> values = byClass.computeIfAbsent(clazz, ignored -> {
            CopyOnWriteArrayList<T> list = new CopyOnWriteArrayList<>();
            for (T value : allInstances) {
                if (clazz.isInstance(value)) {
                    list.add(value);
                }
            }
            return list;
        });
        @SuppressWarnings("unchecked")
        Collection<S> castValues = (Collection<S>) values;
        return Collections.unmodifiableCollection(castValues);
    }

    @Override
    public synchronized Iterator<T> iterator() {
        if (allInstances.isEmpty()) {
            return Collections.emptyIterator();
        }
        return Iterators.unmodifiableIterator(allInstances.iterator());
    }

    @Override
    public synchronized List<T> getAllInstances() {
        return ImmutableList.copyOf(allInstances);
    }

    @Override
    public synchronized int size() {
        return allInstances.size();
    }
}
