package io.github.seraphina.nyx.client.utility;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class MethodUtility {
    private MethodUtility() {
    }

    public static boolean methodMatches(MethodNode method, String name, String descriptor) {
        return method.name.equals(name) && method.desc.equals(descriptor);
    }

    public static boolean markSynchronized(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (methodMatches(method, name, descriptor) && (method.access & Opcodes.ACC_SYNCHRONIZED) == 0) {
                method.access |= Opcodes.ACC_SYNCHRONIZED;
                return true;
            }
        }
        return false;
    }

    public static int replaceMethodCalls(ClassNode classNode, String owner, String name, String descriptor, String replacementOwner, String replacementName, String replacementDescriptor) {
        int replacements = 0;
        for (MethodNode method : classNode.methods) {
            replacements += replaceMethodCalls(method.instructions, owner, name, descriptor, replacementOwner, replacementName, replacementDescriptor);
        }
        return replacements;
    }

    public static int replaceMethodCalls(InsnList instructions, String owner, String name, String descriptor, String replacementOwner, String replacementName, String replacementDescriptor) {
        int replacements = 0;
        for (AbstractInsnNode instruction : instructions) {
            if (instruction instanceof MethodInsnNode methodInsn
                && methodInsn.owner.equals(owner)
                && methodInsn.name.equals(name)
                && methodInsn.desc.equals(descriptor)) {
                methodInsn.setOpcode(Opcodes.INVOKESTATIC);
                methodInsn.owner = replacementOwner;
                methodInsn.name = replacementName;
                methodInsn.desc = replacementDescriptor;
                methodInsn.itf = false;
                replacements++;
            }
        }
        return replacements;
    }
}
