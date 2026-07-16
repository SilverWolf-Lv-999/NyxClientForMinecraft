package io.github.seraphina.nyx.client.utility;

import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public final class ASMUtility {
    private ASMUtility() {
    }

    public static <K, V> Map<K, V> newConcurrentMap() {
        return new ConcurrentHashMap<>();
    }

    public static <T> List<T> newCopyOnWriteList() {
        return new CopyOnWriteArrayList<>();
    }

    public static <T> ClassInstanceMultiMap<T> newConcurrentClassInstanceMultiMap(Class<T> baseClass) {
        return new ConcurrentClassInstanceMultiMap<>(baseClass);
    }

    public static <T> Collector<T, ?, List<T>> toCopyOnWriteListCollector() {
        return Collectors.toCollection(CopyOnWriteArrayList::new);
    }

    public static <T> Iterable<T> snapshotIterable(Iterable<T> iterable) {
        List<T> snapshot = new ArrayList<>();
        iterable.forEach(snapshot::add);
        return snapshot;
    }

    public static LongStream longStreamSnapshot(Spliterator.OfLong spliterator, boolean parallel) {
        LongStream.Builder builder = LongStream.builder();
        spliterator.forEachRemaining((long value) -> builder.add(value));
        LongStream stream = builder.build();
        return parallel ? stream.parallel() : stream.sequential();
    }

    public static <T extends EntityAccess> Stream<EntitySection<T>> getExistingSectionsInChunkSnapshot(EntitySectionStorage<T> storage, long chunkKey) {
        List<EntitySection<T>> snapshot = new ArrayList<>();
        storage.getExistingSectionPositionsInChunk(chunkKey).forEach(sectionKey -> {
            EntitySection<T> section = storage.getSection(sectionKey);
            if (section != null) {
                snapshot.add(section);
            }
        });
        return snapshot.stream();
    }
}
