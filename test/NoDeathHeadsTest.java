package dev.gtnh.nodeathheads;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

public final class NoDeathHeadsTest {
    public static void main(String[] args) throws Exception {
        IClassTransformer transformer = loadTransformer(args[0]);
        verifyTinkerDrops(transformer, args[1]);
        verifyGravesUnmodified(transformer, args[2], "openblocks.common.PlayerDeathHandler");
        verifyGravesUnmodified(transformer, args[2], "openblocks.common.tileentity.TileEntityGrave");
        System.out.println("PASS: packaged coremod replaces player heads and leaves OpenBlocks intact");
    }

    private static IClassTransformer loadTransformer(String path) throws Exception {
        try (JarFile jar = new JarFile(path)) {
            Attributes manifest = jar.getManifest().getMainAttributes();
            check("true".equals(manifest.getValue("FMLCorePluginContainsFMLMod")), "FML also discovers the regular mod");
            Mod mod = NoDeathHeads.class.getAnnotation(Mod.class);
            check(mod != null && "*".equals(mod.acceptableRemoteVersions()), "Clients may connect without the mod");
            String pluginClass = manifest.getValue("FMLCorePlugin");
            IFMLLoadingPlugin plugin = Class.forName(pluginClass).asSubclass(IFMLLoadingPlugin.class)
                .getDeclaredConstructor().newInstance();
            return Class.forName(plugin.getASMTransformerClass()[0]).asSubclass(IClassTransformer.class)
                .getDeclaredConstructor().newInstance();
        }
    }

    private static void verifyTinkerDrops(IClassTransformer transformer, String path) throws Exception {
        String className = "tconstruct.tools.TinkerToolEvents";
        byte[] original = loadClass(path, className);
        ClassNode node = new ClassNode();
        new ClassReader(transformer.transform(className, className, original)).accept(node, 0);
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (!"onLivingDrop".equals(method.name)) continue;
            new Analyzer(new BasicVerifier()).analyze(node.name, method);
            int drops = 0;
            int cobblestoneDrops = 0;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ("tconstruct/util/ItemHelper".equals(call.owner) && "addDrops".equals(call.name)) {
                    drops++;
                    if (isCobblestoneDrop(call)) cobblestoneDrops++;
                }
            }
            check(drops == 7, "All original drop calls remain");
            check(cobblestoneDrops == 2, "The two player-head paths drop one cobblestone each");
            return;
        }
        throw new AssertionError("Missing Tinkers' Construct death drop handler");
    }

    private static boolean isCobblestoneDrop(MethodInsnNode drop) {
        if (drop.getPrevious().getOpcode() != Opcodes.INVOKESPECIAL
                || drop.getPrevious().getPrevious().getOpcode() != Opcodes.GETSTATIC) return false;
        MethodInsnNode constructor = (MethodInsnNode) drop.getPrevious();
        FieldInsnNode block = (FieldInsnNode) constructor.getPrevious();
        return "net/minecraft/item/ItemStack".equals(constructor.owner) && "<init>".equals(constructor.name)
            && "(Lnet/minecraft/block/Block;)V".equals(constructor.desc)
            && "net/minecraft/init/Blocks".equals(block.owner) && "field_150347_e".equals(block.name);
    }

    private static void verifyGravesUnmodified(IClassTransformer transformer, String path, String className)
            throws IOException {
        byte[] original = loadClass(path, className);
        check(Arrays.equals(original, transformer.transform(className, className, original)),
            className + " bytecode is unchanged");
    }

    private static byte[] loadClass(String path, String className) throws IOException {
        String classEntry = className.replace('.', '/') + ".class";
        try (JarFile jar = new JarFile(path); InputStream input = jar.getInputStream(jar.getJarEntry(classEntry))) {
            return readClass(input);
        }
    }

    private static byte[] readClass(InputStream input) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) data.write(buffer, 0, count);
        return data.toByteArray();
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
