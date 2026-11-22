package dev.gtnh.nodeathheads.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class DeathTransformer implements IClassTransformer {
    private static final String TINKER_EVENTS = "tconstruct.tools.TinkerToolEvents";
    private static final String OPENBLOCKS_EVENTS = "openblocks.common.PlayerDeathHandler";
    private static final String LIVING_DROPS = "Lnet/minecraftforge/event/entity/living/LivingDropsEvent;";
    private static final String PLAYER_DROPS = "Lnet/minecraftforge/event/entity/player/PlayerDropsEvent;";
    private static final String HOOKS = "dev/gtnh/nodeathheads/core/DeathHooks";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;

        boolean tinker = TINKER_EVENTS.equals(transformedName);
        if (!tinker && !OPENBLOCKS_EVENTS.equals(transformedName)) return bytes;

        ClassReader reader = new ClassReader(bytes);
        ClassNode target = new ClassNode();
        reader.accept(target, 0);

        String methodName = tinker ? "onLivingDrop" : "onPlayerDrops";
        MethodNode method = findHandler(target, methodName, tinker ? LIVING_DROPS : PLAYER_DROPS);
        int replacements = tinker ? suppressPlayerHeads(method) : keepEmptyGraves(method);
        if (replacements != 2) {
            throw new IllegalStateException(
                "No Death Heads expected 2 patch sites in " + transformedName + "." + methodName
                    + " but found " + replacements);
        }

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        target.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode findHandler(ClassNode target, String name, String eventType) {
        String descriptor = "(" + eventType + ")V";
        for (Object entry : target.methods) {
            MethodNode method = (MethodNode) entry;
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        }
        throw new IllegalStateException("No Death Heads could not find " + target.name + "." + name + descriptor);
    }

    private static int suppressPlayerHeads(MethodNode method) {
        boolean playerBranch = false;
        int replacements = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.INSTANCEOF
                && "net/minecraft/entity/player/EntityPlayer".equals(((TypeInsnNode) instruction).desc)) {
                playerBranch = true;
            }
            if (playerBranch && isCall(instruction, Opcodes.INVOKESTATIC, "tconstruct/util/ItemHelper",
                    "addDrops", "(" + LIVING_DROPS + "Lnet/minecraft/item/ItemStack;)V")) {
                // Discard the generated stack before ItemHelper creates an EntityItem and adds it to the drops.
                method.instructions.insertBefore(instruction, new InsnNode(Opcodes.POP));
                method.instructions.set(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                    "noteSuppressedHead", "(Ljava/lang/Object;)V", false));
                replacements++;
            }
        }
        return replacements;
    }

    private static int keepEmptyGraves(MethodNode method) {
        int replacements = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (isCall(instruction, Opcodes.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z")) {
                // Apply the same exception to the initial drop list and the filtered grave contents.
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "shouldSkipEmptyGrave",
                    "(ZLjava/lang/Object;)Z", false));
                method.instructions.insert(instruction, hook);
                replacements++;
            }
        }
        return replacements;
    }

    private static boolean isCall(AbstractInsnNode instruction, int opcode, String owner, String name,
            String descriptor) {
        if (instruction.getOpcode() != opcode) return false;
        MethodInsnNode call = (MethodInsnNode) instruction;
        return owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc);
    }
}
