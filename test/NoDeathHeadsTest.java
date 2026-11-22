package dev.gtnh.nodeathheads;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import dev.gtnh.nodeathheads.core.DeathHooks;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

public final class NoDeathHeadsTest {
    public static void main(String[] args) throws Exception {
        IClassTransformer transformer = loadTransformer(args[0]);
        verifyUpstreamPatch(transformer, args[1], "tconstruct.tools.TinkerToolEvents", "onLivingDrop",
            "noteSuppressedHead", "tconstruct/util/ItemHelper", "addDrops", 5);
        verifyUpstreamPatch(transformer, args[2], "openblocks.common.PlayerDeathHandler", "onPlayerDrops",
            "shouldSkipEmptyGrave", "java/util/List", "isEmpty", 2);

        Object deathWithHead = new Object();
        Object deathWithoutHead = new Object();
        DeathHooks.noteSuppressedHead(deathWithHead);
        check(!DeathHooks.shouldSkipEmptyGrave(true, deathWithHead), "A suppressed head still allows an empty grave");
        check(!DeathHooks.shouldSkipEmptyGrave(true, deathWithHead), "Both OpenBlocks guards see the same event");
        check(DeathHooks.shouldSkipEmptyGrave(true, deathWithoutHead), "An unrelated empty death behaves as before");
        check(!DeathHooks.shouldSkipEmptyGrave(false, deathWithoutHead), "Existing drops still allow a grave");
        System.out.println("PASS: packaged coremod, GTNH 2.9.0 production bytecode and grave conditions");
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

    private static void verifyUpstreamPatch(IClassTransformer transformer, String path, String className,
            String methodName, String hook, String remainingOwner, String remainingMethod, int remainingCount)
            throws Exception {
        String classEntry = className.replace('.', '/') + ".class";
        try (JarFile jar = new JarFile(path); InputStream input = jar.getInputStream(jar.getJarEntry(classEntry))) {
            byte[] original = readClass(input);
            byte[] patched = transformer.transform(className, className, original);
            ClassNode node = new ClassNode();
            new ClassReader(patched).accept(node, 0);
            for (Object entry : node.methods) {
                MethodNode method = (MethodNode) entry;
                if (!methodName.equals(method.name)) continue;
                new Analyzer(new BasicVerifier()).analyze(node.name, method);
                check(countCalls(method, "dev/gtnh/nodeathheads/core/DeathHooks", hook) == 2,
                    className + " has both expected hooks");
                check(countCalls(method, remainingOwner, remainingMethod) == remainingCount,
                    className + " retains its other calls");
                return;
            }
            throw new AssertionError("Missing " + className + "." + methodName);
        }
    }

    private static byte[] readClass(InputStream input) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) data.write(buffer, 0, count);
        return data.toByteArray();
    }

    private static int countCalls(MethodNode method, String owner, String name) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (owner.equals(call.owner) && name.equals(call.name)) count++;
        }
        return count;
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
