package us.myles.transformer;

import javassist.*;
import tk.ivybits.agent.AgentLoader;
import tk.ivybits.agent.Tools;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class MagicTransformer implements ClassFileTransformer {
	private final ClassMerger[] classMergers;

	public MagicTransformer(ClassMerger[] classMergers) {
		this.classMergers = classMergers;
	}

	public static void agentmain(String string, Instrumentation instrument) {
		List<ClassMerger> mergers = new ArrayList<ClassMerger>();
		URL url = MagicTransformer.class.getProtectionDomain().getCodeSource().getLocation();
		try {
			File file = new File(url.toURI());
			try {
				JarFile jar = new JarFile(file);
				Manifest manifest = jar.getManifest();
				String s = manifest.getMainAttributes().getValue("Map");
				for (String x : s.split(";")) {
					mergers.add(new ClassMerger(x.split("-")[0], x.split("-")[1]));
				}
			} catch (IOException E) {
				// handle
			}
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		instrument.addTransformer(new MagicTransformer(mergers.toArray(new ClassMerger[0])));
		try {
			List<ClassDefinition> redefine = new ArrayList<ClassDefinition>();
			for (ClassMerger c : mergers) {
				Class target = Class.forName(c.getA());
				redefine.add(new ClassDefinition(target, Tools.getBytesFromClass(target)));
			}
			instrument.redefineClasses(redefine.toArray(new ClassDefinition[0]));
			AgentLoader.clearRedefine();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (UnmodifiableClassException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean shouldTransform(String name) {
		for (ClassMerger cm : classMergers) {
			if (cm.getA().equalsIgnoreCase(name)) return true;
		}
		return false;
	}

	public String getTarget(String name) {
		for (ClassMerger cm : classMergers) {
			if (cm.getA().equalsIgnoreCase(name))
				return cm.getB();
		}
		return null;
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		if (shouldTransform(classBeingRedefined.getName())) {
			try {
				CtClass original = ClassPool.getDefault().get(classBeingRedefined.getName());
				CtClass target = ClassPool.getDefault().get(getTarget(classBeingRedefined.getName()));
				System.out.println("Injecting " + original.getName());
				for (CtMethod m : target.getDeclaredMethods()) {
					if (!m.hasAnnotation(Ignore.class)) {
						try {
							original.getDeclaredMethod(m.getName()).setBody(m, null);
						} catch (NotFoundException e) {
							System.out.println("Failed to add " + m.getName() + " you cannot create new methods.");
						}
					}
				}
				byte[] bytes = original.toBytecode();
				original.detach();
				return bytes;
			} catch (NotFoundException e) {
				e.printStackTrace();
				return null; // not found
			} catch (CannotCompileException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
}
