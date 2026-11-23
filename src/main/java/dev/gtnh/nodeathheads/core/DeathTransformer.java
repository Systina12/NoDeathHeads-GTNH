package dev.gtnh.nodeathheads.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class DeathTransformer implements IClassTransformer {
    private static final String TINKER_EVENTS = "tconstruct.tools.TinkerToolEvents";
    private static final String LIVING_DROPS = "Lnet/minecraftforge/event/entity/living/LivingDropsEvent;";
    private static final String ITEM_STACK = "net/minecraft/item/ItemStack";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TINKER_EVENTS.equals(transformedName)) return bytes;

        ClassReader reader = new ClassReader(bytes);
        ClassNode target = new ClassNode();
        reader.accept(target, 0);

        MethodNode method = findDropHandler(target);
        int replacements = replacePlayerHeads(method);
        if (replacements != 2) {
            throw new IllegalStateException(
                "No Death Heads expected 2 patch sites in " + transformedName + "." + method.name
                    + " but found " + replacements);
        }

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        target.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode findDropHandler(ClassNode target) {
        String descriptor = "(" + LIVING_DROPS + ")V";
        for (Object entry : target.methods) {
            MethodNode method = (MethodNode) entry;
            if ("onLivingDrop".equals(method.name) && descriptor.equals(method.desc)) return method;
        }
        throw new IllegalStateException("No Death Heads could not find " + target.name + ".onLivingDrop" + descriptor);
    }

    private static int replacePlayerHeads(MethodNode method) {
        boolean playerBranch = false;
        int replacements = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.INSTANCEOF
                && "net/minecraft/entity/player/EntityPlayer".equals(((TypeInsnNode) instruction).desc)) {
                playerBranch = true;
            }
            if (playerBranch && isCall(instruction, Opcodes.INVOKESTATIC, "tconstruct/util/ItemHelper",
                    "addDrops", "(" + LIVING_DROPS + "L" + ITEM_STACK + ";)V")) {
                // Keep the event on the stack and replace the pending head with one cobblestone.
                InsnList cobblestone = new InsnList();
                cobblestone.add(new InsnNode(Opcodes.POP));
                cobblestone.add(new TypeInsnNode(Opcodes.NEW, ITEM_STACK));
                cobblestone.add(new InsnNode(Opcodes.DUP));
                cobblestone.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/init/Blocks",
                    "field_150347_e", "Lnet/minecraft/block/Block;")); // Blocks.cobblestone
                cobblestone.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ITEM_STACK,
                    "<init>", "(Lnet/minecraft/block/Block;)V", false));
                method.instructions.insertBefore(instruction, cobblestone);
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
