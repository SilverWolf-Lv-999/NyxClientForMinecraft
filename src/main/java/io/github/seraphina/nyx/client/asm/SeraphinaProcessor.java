package io.github.seraphina.nyx.client.asm;

import io.github.seraphina.nyx.client.utility.MethodUtility;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.Set;

public class SeraphinaProcessor implements ClassProcessor {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ProcessorName NAME;
    private static final String ASM_UTILITY;
    private static final Set<String> VANILLA_TARGETS;
    private static final List<String> COMPATIBILITY_PREFIXES;

    static {
        LOGGER.info("Loading Seraphina Processor.....");
        NAME = new ProcessorName("nyxclient", "thread_ripper_concurrency");
        ASM_UTILITY = "io/github/seraphina/nyx/client/utility/ASMUtility";
        VANILLA_TARGETS = Set.of(
                "net/minecraft/util/ClassInstanceMultiMap",
                "net/minecraft/world/level/entity/EntitySection",
                "net/minecraft/world/level/entity/EntitySectionStorage",
                "net/minecraft/world/level/entity/EntityLookup",
                "net/minecraft/world/level/entity/EntityTickList"
        );
        COMPATIBILITY_PREFIXES = List.of(
                "dev/tr7zw/entityculling/",
                "net/raphimc/immediatelyfast/",
                "com/seibel/distanthorizons/",
                "dev/tonimatas/packetfixer/"
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
        String owner = context.type().getInternalName();
        return VANILLA_TARGETS.contains(owner) || isCompatibilityTarget(owner);
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        ClassNode classNode = context.node();
        String owner = context.type().getInternalName();
        boolean changed;
        if (VANILLA_TARGETS.contains(owner)) {
            changed = switch (owner) {
                case "net/minecraft/util/ClassInstanceMultiMap" -> transformClassInstanceMultiMap(classNode);
                case "net/minecraft/world/level/entity/EntitySection" -> transformEntitySection(classNode);
                case "net/minecraft/world/level/entity/EntitySectionStorage" -> transformEntitySectionStorage(classNode);
                case "net/minecraft/world/level/entity/EntityLookup" -> transformEntityLookup(classNode);
                case "net/minecraft/world/level/entity/EntityTickList" -> transformEntityTickList(classNode);
                default -> false;
            };
        } else {
            changed = transformCompatibilityClass(classNode);
        }

        if (!changed) {
            return ComputeFlags.NO_REWRITE;
        }

        context.audit("Applied ThreadRipper concurrency guards", owner);
        return ComputeFlags.COMPUTE_FRAMES;
    }

    private static boolean isCompatibilityTarget(String owner) {
        for (String prefix : COMPATIBILITY_PREFIXES) {
            if (owner.startsWith(prefix) && owner.contains("/mixin")) {
                return true;
            }
        }
        return false;
    }

    private static boolean transformClassInstanceMultiMap(ClassNode classNode) {
        boolean changed = applyThreadSafeFactoryReplacements(classNode);

        changed |= MethodUtility.markSynchronized(classNode, "add", "(Ljava/lang/Object;)Z");
        changed |= MethodUtility.markSynchronized(classNode, "remove", "(Ljava/lang/Object;)Z");
        changed |= MethodUtility.markSynchronized(classNode, "contains", "(Ljava/lang/Object;)Z");
        changed |= MethodUtility.markSynchronized(classNode, "find", "(Ljava/lang/Class;)Ljava/util/Collection;");
        changed |= MethodUtility.markSynchronized(classNode, "iterator", "()Ljava/util/Iterator;");
        changed |= MethodUtility.markSynchronized(classNode, "getAllInstances", "()Ljava/util/List;");
        changed |= MethodUtility.markSynchronized(classNode, "size", "()I");
        return changed;
    }

    private static boolean transformEntitySection(ClassNode classNode) {
        boolean changed = replaceEntitySectionStorageConstruction(classNode);
        changed |= MethodUtility.markSynchronized(classNode, "add", "(Lnet/minecraft/world/level/entity/EntityAccess;)V");
        changed |= MethodUtility.markSynchronized(classNode, "remove", "(Lnet/minecraft/world/level/entity/EntityAccess;)Z");
        changed |= MethodUtility.markSynchronized(classNode, "getEntities", "(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;");
        changed |= MethodUtility.markSynchronized(classNode, "getEntities", "(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;");
        changed |= MethodUtility.markSynchronized(classNode, "isEmpty", "()Z");
        changed |= MethodUtility.markSynchronized(classNode, "getEntities", "()Ljava/util/stream/Stream;");
        changed |= MethodUtility.markSynchronized(classNode, "getStatus", "()Lnet/minecraft/world/level/entity/Visibility;");
        changed |= MethodUtility.markSynchronized(classNode, "updateChunkStatus", "(Lnet/minecraft/world/level/entity/Visibility;)Lnet/minecraft/world/level/entity/Visibility;");
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

    private static boolean transformCompatibilityClass(ClassNode classNode) {
        boolean changed = applyThreadSafeFactoryReplacements(classNode);
        changed |= markTickCallbackHandlersSynchronized(classNode);
        return changed;
    }

    private static boolean applyThreadSafeFactoryReplacements(ClassNode classNode) {
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
            "com/google/common/collect/Sets",
            "newHashSet",
            "()Ljava/util/HashSet;",
            ASM_UTILITY,
            "newConcurrentSet",
            "()Ljava/util/Set;"
        ) > 0;
        changed |= MethodUtility.replaceMethodCalls(
            classNode,
            "com/google/common/collect/Queues",
            "newArrayDeque",
            "()Ljava/util/ArrayDeque;",
            ASM_UTILITY,
            "newConcurrentQueue",
            "()Ljava/util/Queue;"
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
        changed |= MethodUtility.replaceMethodCalls(
            classNode,
            "java/util/stream/StreamSupport",
            "stream",
            "(Ljava/util/Spliterator;Z)Ljava/util/stream/Stream;",
            ASM_UTILITY,
            "streamSnapshot",
            "(Ljava/util/Spliterator;Z)Ljava/util/stream/Stream;"
        ) > 0;
        return changed;
    }

    private static boolean markTickCallbackHandlersSynchronized(ClassNode classNode) {
        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if ((method.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
                continue;
            }
            if (!method.desc.contains("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;")) {
                continue;
            }
            String name = method.name.toLowerCase();
            if (!name.contains("tick")) {
                continue;
            }
            method.access |= Opcodes.ACC_SYNCHRONIZED;
            changed = true;
        }
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

    private static boolean replaceEntitySectionStorageConstruction(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (!MethodUtility.methodMatches(method, "<init>", "(Ljava/lang/Class;Lnet/minecraft/world/level/entity/Visibility;)V")) {
                continue;
            }

            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction.getOpcode() != Opcodes.NEW || !(instruction instanceof TypeInsnNode typeInsn)) {
                    continue;
                }
                if (!typeInsn.desc.equals("net/minecraft/util/ClassInstanceMultiMap")) {
                    continue;
                }

                AbstractInsnNode dup = nextInstruction(instruction);
                AbstractInsnNode baseClass = nextInstruction(dup);
                AbstractInsnNode constructor = nextInstruction(baseClass);
                if (dup == null || dup.getOpcode() != Opcodes.DUP
                    || !(baseClass instanceof VarInsnNode varInsn) || varInsn.getOpcode() != Opcodes.ALOAD || varInsn.var != 1
                    || !(constructor instanceof MethodInsnNode methodInsn)
                    || methodInsn.getOpcode() != Opcodes.INVOKESPECIAL
                    || !methodInsn.owner.equals("net/minecraft/util/ClassInstanceMultiMap")
                    || !methodInsn.name.equals("<init>")
                    || !methodInsn.desc.equals("(Ljava/lang/Class;)V")) {
                    continue;
                }

                InsnList replacement = new InsnList();
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
                replacement.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    ASM_UTILITY,
                    "newConcurrentClassInstanceMultiMap",
                    "(Ljava/lang/Class;)Lnet/minecraft/util/ClassInstanceMultiMap;",
                    false
                ));
                method.instructions.insertBefore(instruction, replacement);
                method.instructions.remove(constructor);
                method.instructions.remove(baseClass);
                method.instructions.remove(dup);
                method.instructions.remove(instruction);
                return true;
            }
        }
        return false;
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction == null ? null : instruction.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }
}
