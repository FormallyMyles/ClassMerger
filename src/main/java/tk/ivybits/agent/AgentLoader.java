package tk.ivybits.agent;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import us.myles.transformer.ClassMerger;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.jar.*;
import java.util.jar.Attributes.Name;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import static tk.ivybits.agent.Tools.getBytesFromStream;

/**
 * A utility class for loading Java agents.
 *
 * @author Tudor
 */
public class AgentLoader {

	private static ClassMerger[] redefine = new ClassMerger[0];

	/**
	 * Loads an agent into a JVM.
	 *
	 * @param agent     The main agent class.
	 * @param resources Array of classes to be included with agent.
	 * @param pid       The ID of the target JVM.
	 * @throws IOException
	 * @throws AttachNotSupportedException
	 * @throws AgentLoadException
	 * @throws AgentInitializationException
	 */
	public static void attachAgentToJVM(String pid, Class agent, Class... resources)
			throws IOException, AttachNotSupportedException, AgentLoadException, AgentInitializationException {
		VirtualMachine vm = VirtualMachine.attach(pid);
		vm.loadAgent(generateAgentJar(agent, resources).getAbsolutePath());
		vm.detach();
	}

	/**
	 * Generates a temporary agent file to be loaded.
	 *
	 * @param agent     The main agent class.
	 * @param resources Array of classes to be included with agent.
	 * @return Returns a temporary jar file with the specified classes included.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static File generateAgentJar(Class agent, Class... resources) throws IOException {
		File jarFile = File.createTempFile("agent", ".jar");
		jarFile.deleteOnExit();
		Manifest manifest = new Manifest();
		Attributes mainAttributes = manifest.getMainAttributes();
		// Create manifest stating that agent is allowed to transform classes
		mainAttributes.put(Name.MANIFEST_VERSION, "1.0");
		mainAttributes.put(new Name("Agent-Class"), agent.getName());
		mainAttributes.put(new Name("Can-Retransform-Classes"), "true");
		mainAttributes.put(new Name("Can-Redefine-Classes"), "true");
		String s = "";
		for (ClassMerger cm : toRedefine()) {
			if (s.length() == 0) {
				s = cm.getA() + "-" + cm.getB();
			} else {
				s = s + ";" + cm.getA() + "-" + cm.getB();
			}
		}
		mainAttributes.put(new Name("Map"), s);
		JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest);

		jos.putNextEntry(new JarEntry(agent.getName().replace('.', '/') + ".class"));

		jos.write(getBytesFromStream(agent.getClassLoader().getResourceAsStream(unqualify(agent))));
		jos.closeEntry();

		for (Class clazz : resources) {
			String name = unqualify(clazz);
			jos.putNextEntry(new JarEntry(name));
			jos.write(getBytesFromStream(clazz.getClassLoader().getResourceAsStream(name)));
			jos.closeEntry();
		}
		for (ClassMerger clazz : toRedefine()) {
			try {
				Class c = Class.forName(clazz.getB());
				String name = unqualify(c);
				jos.putNextEntry(new JarEntry(name));
				jos.write(getBytesFromStream(c.getClassLoader().getResourceAsStream(name)));
				jos.closeEntry();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		File j2 = null;
		try {
			j2 = new File(agent.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		if (j2.isFile()) {
			final JarFile jar = new JarFile(j2);
			final Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				ZipEntry ze = entries.nextElement();
				if (ze.getName().startsWith("javassist")
						|| ze.getName().startsWith("us/myles/transformer")
						|| ze.getName().startsWith("tk/ivybits/agent")
						|| ze.getName().startsWith("com/sun")
						|| ze.getName().startsWith("sun/")) {
					try {
						jos.putNextEntry(ze);
						byte[] buffer = new byte[4096];
						InputStream is = jar.getInputStream(ze);
						int bytesRead;
						while ((bytesRead = is.read(buffer)) != -1) {
							jos.write(buffer, 0, bytesRead);
						}
						is.close();
						jos.closeEntry();
					} catch (ZipException e) {
					}
				}
			}
		}

		jos.close();
		return jarFile;
	}

	private static String unqualify(Class clazz) {
		return clazz.getName().replace('.', '/') + ".class";
	}

	public static void clearRedefine() {
		redefine = new ClassMerger[0];
	}

	public static ClassMerger[] toRedefine() {
		return redefine;
	}

	public static void setRedefine(ClassMerger... rd) {
		redefine = rd;
	}
}