package dev.gtnh.nodeathheads.core;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

public final class DeathTransformer implements IClassTransformer {
    private static final Logger LOG = Logger.getLogger("NoDeathHeads");
    private static final String TINKER_EVENTS = "tconstruct.tools.TinkerToolEvents";
    private static final String LIVING_DROPS = "Lnet/minecraftforge/event/entity/living/LivingDropsEvent;";
    private static final String ITEM_STACK = "net/minecraft/item/ItemStack";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TINKER_EVENTS.equals(transformedName)) return bytes;

        try {
            ClassReader reader = new ClassReader(bytes);
            ClassNode target = new ClassNode();
            reader.accept(target, 0);

            MethodNode method = findDropHandler(target);
            List<AbstractInsnNode> sites = findPlayerHeadDrops(target.name, method);
            if (sites.size() == 2) {
                for (AbstractInsnNode site : sites) replacePlayerHead(method, site);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                target.accept(writer);
                return writer.toByteArray();
            }
            LOG.warning("No Death Heads skipped the Tinkers' Construct patch: expected two player-head drops, found "
                + sites.size() + ". Using original death drops.");
        } catch (AnalyzerException | RuntimeException e) {
            LOG.log(Level.WARNING, "No Death Heads could not patch Tinkers' Construct. Using original death drops.", e);
        }
        return bytes;
    }

    private static MethodNode findDropHandler(ClassNode target) {
        String descriptor = "(" + LIVING_DROPS + ")V";
        for (Object entry : target.methods) {
            MethodNode method = (MethodNode) entry;
            if ("onLivingDrop".equals(method.name) && descriptor.equals(method.desc)) return method;
        }
        throw new IllegalStateException("No Death Heads could not find " + target.name + ".onLivingDrop" + descriptor);
    }

    private static List<AbstractInsnNode> findPlayerHeadDrops(String owner, MethodNode method)
            throws AnalyzerException {
        List<AbstractInsnNode> sites = new ArrayList<>();
        Frame[] frames = new Analyzer(new SourceInterpreter()).analyze(owner, method);
        JumpInsnNode guard = findPlayerGuard(method);
        if (guard == null) return sites;

        for (AbstractInsnNode instruction = guard.getNext(); instruction != null && instruction != guard.label;
                instruction = instruction.getNext()) {
            if (!isCall(instruction, Opcodes.INVOKESTATIC, "tconstruct/util/ItemHelper",
                    "addDrops", "(" + LIVING_DROPS + "L" + ITEM_STACK + ";)V")) continue;
            if (isPlayerHead(method, frames, instruction)) sites.add(instruction);
        }
        return sites;
    }

    private static JumpInsnNode findPlayerGuard(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.INSTANCEOF
                && "net/minecraft/entity/player/EntityPlayer".equals(((TypeInsnNode) instruction).desc)
                && next != null && next.getOpcode() == Opcodes.IFEQ) {
                return (JumpInsnNode) next;
            }
        }
        return null;
    }

    private static boolean isPlayerHead(MethodNode method, Frame[] frames, AbstractInsnNode drop) {
        // Follow the stack argument back to its unique local store and constructor.
        AbstractInsnNode load = stackSource(frames[method.instructions.indexOf(drop)], 0);
        if (load == null || load.getOpcode() != Opcodes.ALOAD) return false;
        Frame loadFrame = frames[method.instructions.indexOf(load)];
        AbstractInsnNode store = onlySource((SourceValue) loadFrame.getLocal(((VarInsnNode) load).var));
        if (store == null || store.getOpcode() != Opcodes.ASTORE) return false;
        AbstractInsnNode constructor = store.getPrevious();
        if (!isCall(constructor, Opcodes.INVOKESPECIAL, ITEM_STACK, "<init>", "(Lnet/minecraft/item/Item;II)V")) {
            return false;
        }

        Frame beforeInit = frames[method.instructions.indexOf(constructor)];
        AbstractInsnNode receiver = stackSource(beforeInit, 3);
        if (receiver == null || receiver.getOpcode() != Opcodes.DUP) return false;
        AbstractInsnNode allocation = receiver.getPrevious();
        AbstractInsnNode copy = stackSource(beforeInit, 4);
        // ASM versions track the surviving copy as either the NEW instruction or the DUP.
        if (allocation == null || allocation.getOpcode() != Opcodes.NEW
                || (copy != allocation && copy != receiver)
                || copy != stackSource(frames[method.instructions.indexOf(store)], 0)) return false;
        AbstractInsnNode metadata = stackSource(beforeInit, 0);
        AbstractInsnNode count = stackSource(beforeInit, 1);
        AbstractInsnNode item = stackSource(beforeInit, 2);
        if (metadata == null || metadata.getOpcode() != Opcodes.ICONST_3 || count == null
                || count.getOpcode() != Opcodes.ICONST_1 || item == null || item.getOpcode() != Opcodes.GETSTATIC) {
            return false;
        }
        FieldInsnNode field = (FieldInsnNode) item;
        return "net/minecraft/init/Items".equals(field.owner) && "field_151144_bL".equals(field.name)
            && "Lnet/minecraft/item/Item;".equals(field.desc);
    }

    private static AbstractInsnNode stackSource(Frame frame, int fromTop) {
        if (frame == null || frame.getStackSize() <= fromTop) return null;
        return onlySource((SourceValue) frame.getStack(frame.getStackSize() - 1 - fromTop));
    }

    private static AbstractInsnNode onlySource(SourceValue value) {
        return value.insns.size() == 1 ? (AbstractInsnNode) value.insns.iterator().next() : null;
    }

    private static void replacePlayerHead(MethodNode method, AbstractInsnNode site) {
        // Keep the event on the stack and replace the pending head with one cobblestone.
        InsnList cobblestone = new InsnList();
        cobblestone.add(new InsnNode(Opcodes.POP));
        cobblestone.add(new TypeInsnNode(Opcodes.NEW, ITEM_STACK));
        cobblestone.add(new InsnNode(Opcodes.DUP));
        cobblestone.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/init/Blocks",
            "field_150347_e", "Lnet/minecraft/block/Block;")); // Blocks.cobblestone
        cobblestone.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ITEM_STACK,
            "<init>", "(Lnet/minecraft/block/Block;)V", false));
        method.instructions.insertBefore(site, cobblestone);
    }

    private static boolean isCall(AbstractInsnNode instruction, int opcode, String owner, String name,
            String descriptor) {
        if (instruction == null || instruction.getOpcode() != opcode) return false;
        MethodInsnNode call = (MethodInsnNode) instruction;
        return owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc);
    }
}
