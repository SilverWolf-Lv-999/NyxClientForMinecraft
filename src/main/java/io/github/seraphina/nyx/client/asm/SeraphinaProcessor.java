package io.github.seraphina.nyx.client.asm;

import io.github.seraphina.nyx.client.utility.MethodUtility;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Set;

public class SeraphinaProcessor implements ClassProcessor {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ProcessorName NAME;
    private static final String ASM_UTILITY;
    private static final Set<String> TARGETS;

    static {
        LOGGER.info("Loading Seraphina Processor.....");
        NAME = new ProcessorName("nyxclient", "thread_ripper_concurrency");
        ASM_UTILITY = "io/github/seraphina/nyx/client/utility/ASMUtility";
        TARGETS = Set.of(
                "net/minecraft/util/ClassInstanceMultiMap",
                "net/minecraft/world/level/entity/EntitySectionStorage",
                "net/minecraft/world/level/entity/EntityLookup",
                "net/minecraft/world/level/entity/EntityTickList"
        );
        LOGGER.info("clinit done...");
        LOGGER.debug("ClassLoader: {}", SeraphinaProcessor.class.getClassLoader());
    }

    public SeraphinaProcessor() {
        LOGGER.debug("init...");
        LOGGER.debug("Now ClassLoading: {}", this.getClass().getClassLoader());
    }

    @Override
    public ProcessorName name() {
        return NAME;
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        return TARGETS.contains(context.type().getInternalName());
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        ClassNode classNode = context.node();
        String owner = context.type().getInternalName();
        boolean changed = switch (owner) {
            case "net/minecraft/util/ClassInstanceMultiMap" -> transformClassInstanceMultiMap(classNode);
            case "net/minecraft/world/level/entity/EntitySectionStorage" -> transformEntitySectionStorage(classNode);
            case "net/minecraft/world/level/entity/EntityLookup" -> transformEntityLookup(classNode);
            case "net/minecraft/world/level/entity/EntityTickList" -> transformEntityTickList(classNode);
            default -> false;
        };

        if (!changed) {
            return ComputeFlags.NO_REWRITE;
        }

        context.audit("Applied ThreadRipper concurrency guards", owner);
        return ComputeFlags.COMPUTE_FRAMES;
    }

    private static boolean transformClassInstanceMultiMap(ClassNode classNode) {
        boolean changed = false;
        changed |= MethodUtility.replaceMethodCalls(
            classNode,
            "com/google/common/collect/Maps",
            "newHashMap",
            "()Ljava/util/HashMap;",
            ASM_UTILITY,
            "newConcurrentMap",
            "()Ljava/util/Map;"
        ) > 0;
        changed |= MethodUtility.replaceMethodCalls(
            classNode,
            "com/google/common/collect/Lists",
            "newArrayList",
            "()Ljava/util/ArrayList;",
            ASM_UTILITY,
            "newCopyOnWriteList",
            "()Ljava/util/List;"
        ) > 0;
        changed |= MethodUtility.replaceMethodCalls(
            classNode,
            "net/minecraft/Util",
            "toMutableList",
            "()Ljava/util/stream/Collector;",
            ASM_UTILITY,
            "toCopyOnWriteListCollector",
            "()Ljava/util/stream/Collector;"
        ) > 0;

        changed |= MethodUtility.markSynchronized(classNode, "add", "(Ljava/lang/Object;)Z");
        changed |= MethodUtility.markSynchronized(classNode, "remove", "(Ljava/lang/Object;)Z");
        changed |= MethodUtility.markSynchronized(classNode, "contains", "(Ljava/lang/Object;)Z");
        changed |= MethodUtility.markSynchronized(classNode, "find", "(Ljava/lang/Class;)Ljava/util/Collection;");
        changed |= MethodUtility.markSynchronized(classNode, "iterator", "()Ljava/util/Iterator;");
        changed |= MethodUtility.markSynchronized(classNode, "getAllInstances", "()Ljava/util/List;");
        changed |= MethodUtility.markSynchronized(classNode, "size", "()I");
        return changed;
    }

    private static boolean transformEntitySectionStorage(ClassNode classNode) {
        boolean changed = false;
        changed |= MethodUtility.markSynchronized(classNode, "forEachAccessibleNonEmptySection", "(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V");
        changed |= MethodUtility.markSynchronized(classNode, "getExistingSectionPositionsInChunk", "(J)Ljava/util/stream/LongStream;");
        changed |= MethodUtility.markSynchronized(classNode, "getChunkSections", "(II)Lit/unimi/dsi/fastutil/longs/LongSortedSet;");
        changed |= MethodUtility.markSynchronized(classNode, "getExistingSectionsInChunk", "(J)Ljava/util/stream/Stream;");
        changed |= MethodUtility.markSynchronized(classNode, "getOrCreateSection", "(J)Lnet/minecraft/world/level/entity/EntitySection;");
        changed |= MethodUtility.markSynchronized(classNode, "getSection", "(J)Lnet/minecraft/world/level/entity/EntitySection;");
        changed |= MethodUtility.markSynchronized(classNode, "createSection", "(J)Lnet/minecraft/world/level/entity/EntitySection;");
        changed |= MethodUtility.markSynchronized(classNode, "getAllChunksWithExistingSections", "()Lit/unimi/dsi/fastutil/longs/LongSet;");
        changed |= MethodUtility.markSynchronized(classNode, "getEntities", "(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V");
        changed |= MethodUtility.markSynchronized(classNode, "getEntities", "(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V");
        changed |= MethodUtility.markSynchronized(classNode, "remove", "(J)V");
        changed |= MethodUtility.markSynchronized(classNode, "count", "()I");
        changed |= MethodUtility.replaceMethodCalls(
            classNode,
            "java/util/stream/StreamSupport",
            "longStream",
            "(Ljava/util/Spliterator$OfLong;Z)Ljava/util/stream/LongStream;",
            ASM_UTILITY,
            "longStreamSnapshot",
            "(Ljava/util/Spliterator$OfLong;Z)Ljava/util/stream/LongStream;"
        ) > 0;
        changed |= replaceExistingSectionsInChunk(classNode);
        return changed;
    }

    private static boolean transformEntityLookup(ClassNode classNode) {
        boolean changed = false;
        changed |= MethodUtility.markSynchronized(classNode, "getEntities", "(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/util/AbortableIterationConsumer;)V");
        changed |= MethodUtility.markSynchronized(classNode, "getAllEntities", "()Ljava/lang/Iterable;");
        changed |= MethodUtility.markSynchronized(classNode, "add", "(Lnet/minecraft/world/level/entity/EntityAccess;)V");
        changed |= MethodUtility.markSynchronized(classNode, "remove", "(Lnet/minecraft/world/level/entity/EntityAccess;)V");
        changed |= MethodUtility.markSynchronized(classNode, "getEntity", "(I)Lnet/minecraft/world/level/entity/EntityAccess;");
        changed |= MethodUtility.markSynchronized(classNode, "getEntity", "(Ljava/util/UUID;)Lnet/minecraft/world/level/entity/EntityAccess;");
        changed |= MethodUtility.markSynchronized(classNode, "count", "()I");
        changed |= MethodUtility.replaceMethodCalls(
            classNode,
            "com/google/common/collect/Iterables",
            "unmodifiableIterable",
            "(Ljava/lang/Iterable;)Ljava/lang/Iterable;",
            ASM_UTILITY,
            "snapshotIterable",
            "(Ljava/lang/Iterable;)Ljava/lang/Iterable;"
        ) > 0;
        return changed;
    }

    private static boolean transformEntityTickList(ClassNode classNode) {
        boolean changed = false;
        changed |= MethodUtility.markSynchronized(classNode, "ensureActiveIsNotIterated", "()V");
        changed |= MethodUtility.markSynchronized(classNode, "add", "(Lnet/minecraft/world/entity/Entity;)V");
        changed |= MethodUtility.markSynchronized(classNode, "remove", "(Lnet/minecraft/world/entity/Entity;)V");
        changed |= MethodUtility.markSynchronized(classNode, "contains", "(Lnet/minecraft/world/entity/Entity;)Z");
        changed |= MethodUtility.markSynchronized(classNode, "forEach", "(Ljava/util/function/Consumer;)V");
        return changed;
    }

    private static boolean replaceExistingSectionsInChunk(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (!MethodUtility.methodMatches(method, "getExistingSectionsInChunk", "(J)Ljava/util/stream/Stream;")) {
                continue;
            }

            InsnList replacement = new InsnList();
            replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
            replacement.add(new VarInsnNode(Opcodes.LLOAD, 1));
            replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                ASM_UTILITY,
                "getExistingSectionsInChunkSnapshot",
                "(Lnet/minecraft/world/level/entity/EntitySectionStorage;J)Ljava/util/stream/Stream;",
                false
            ));
            replacement.add(new InsnNode(Opcodes.ARETURN));

            method.instructions.clear();
            method.instructions.add(replacement);
            method.tryCatchBlocks.clear();
            method.localVariables.clear();
            return true;
        }
        return false;
    }
}
