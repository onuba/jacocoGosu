/*******************************************************************************
 * Copyright (c) 2009, 2013 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.agent.rt.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.logging.Logger;

import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.AgentOptions;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.WildcardMatcher;

/**
 * Class file transformer to instrument classes for code coverage analysis.
 */
public class CoverageTransformer implements ClassFileTransformer {

	private static final String AGENT_PREFIX;

	private static Logger log = Logger.getLogger("transformer");

	static {
		final String name = CoverageTransformer.class.getName();
		AGENT_PREFIX = toVMName(name.substring(0, name.lastIndexOf('.')));
	}

	private final IExceptionLogger logger;
	private final Instrumenter instrumenter;
	private final WildcardMatcher includes;
	private final WildcardMatcher excludes;
	private final WildcardMatcher exclClassloader;
	private final IRuntime runtime;
	private final File classDumpDir;
	
	/**
	 * New transformer with the given delegates.
	 * 
	 * @param runtime
	 *            coverage runtime
	 * @param options
	 *            configuration options for the generator
	 * @param logger
	 *            logger for exceptions during instrumentation
	 */
	public CoverageTransformer(final IRuntime runtime, final AgentOptions options,
			final IExceptionLogger logger) {

		this.instrumenter = new Instrumenter(runtime);
		this.logger = logger;
		this.runtime = runtime;

		// Class names will be reported in VM notation:
		includes = new WildcardMatcher(toWildcard(toVMName(options.getIncludes())));
		excludes = new WildcardMatcher(toWildcard(toVMName(options.getExcludes())));
		exclClassloader = new WildcardMatcher(toWildcard(options.getExclClassloader()));
		classDumpDir = new File(options.getClassDumpDir());
	}

	public byte[] transform(final ClassLoader loader, final String classname, final Class<?> classBeingRedefined,
			final ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {

		if (!filter(loader, classname)) {
			return null;
		}

		try {
			// classFileDumper.dump(classname, classfileBuffer);
			log.finest("Transforming class " + classname + " size " + classfileBuffer.length + " bytes");
			
			// try to load the byte code file
			/*final String classNameFile = classname.replace('/', '.') + ".class";
			final URL is = loader.getResource(classNameFile);// getResourceAsStream(classNameFile);
			if (is == null) {
				System.out.println("Fichero no encontrado para " + classNameFile);
			} else {
				System.out.println("Fichero encontrado en " + is.toURI().getPath());
			}*/
			
			possiblyDump(classname, ".class", classfileBuffer, isGeneratedClass(classBeingRedefined, classname, loader));
			
			if (classBeingRedefined != null) {
				//System.out.println("***************************************** No nulo!");
				// For redefined classes we must clear the execution data
				// reference as probes might have changed.
				runtime.disconnect(classBeingRedefined);
			}
			
			//System.out.println("Transforming class " + classname + " size " + classfileBuffer.length + " bytes");
			//System.out.println("class buffer " + new String(classfileBuffer));
			return instrumenter.instrument(classfileBuffer, classname);
		} catch (final Exception ex) {
			final IllegalClassFormatException wrapper = new IllegalClassFormatException(ex.getMessage());
			wrapper.initCause(ex);
			// Report this, as the exception is ignored by the JVM:
			logger.logExeption(wrapper);
			throw wrapper;
		}
	}

	// Check if the class is dynamically generated; ie it is without a class
	// file.
	private boolean isGeneratedClass(Class<?> classBeingRedefined, String classname, ClassLoader loader) throws ClassNotFoundException {
		//System.out.println("isGeneratedClass " + classBeingRedefined);
		if (classBeingRedefined != null) {
			for (Class<?> interfaceClass : classBeingRedefined.getInterfaces()) {
				System.out.println("isGeneratedClass Interface name: " + interfaceClass.getName());
				if (interfaceClass.getName().startsWith("IGosu")) {
					return true;
				}
			}
		}
		
		return false;
	}

	/**
	 * Dump bytecode if configured with location to write to.
	 */
	private void possiblyDump(String name, String suffix, byte[] bytecode, boolean generatedClass) {
		if (classDumpDir != null && generatedClass) {
			File file = new File(classDumpDir, name + suffix);
			if (!file.exists()) {
				file.getParentFile().mkdirs();
				OutputStream out = null;
				try {
					log.info("Dumping " + name + " to file " + file.toString());
					out = new FileOutputStream(file);
					out.write(bytecode);
				} catch (IOException e) {
					logger.logExeption(e);
				} finally {
					if (out != null) {
						try {
							out.close();
						} catch (IOException e) {
							logger.logExeption(e);
						}
					}
				}
			}
		}
	}	

	/**
	 * Checks whether this class should be instrumented.
	 * 
	 * @param loader
	 *            loader for the class
	 * @param classname
	 *            VM name of the class to check
	 * @return <code>true</code> if the class should be instrumented
	 */
	protected boolean filter(final ClassLoader loader, final String classname) {
		// Don't instrument classes of the bootstrap loader:
		return loader != null &&

				!classname.startsWith(AGENT_PREFIX) &&

				!exclClassloader.matches(loader.getClass().getName()) &&

				includes.matches(classname) &&

				!excludes.matches(classname);
	}

	private String toWildcard(final String src) {
		if (src.indexOf('|') != -1) {
			final IllegalArgumentException ex = new IllegalArgumentException(
					"Usage of '|' as a list separator for JaCoCo agent options is deprecated and will not work in future versions - use ':' instead.");
			logger.logExeption(ex);
			return src.replace('|', ':');
		}
		return src;
	}

	private static String toVMName(final String srcName) {
		return srcName.replace('.', '/');
	}

}
