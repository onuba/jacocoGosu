/*******************************************************************************
 * Copyright (c) 2009, 2013 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Brock Janiczak - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.ant;

import static java.lang.String.format;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.Union;
import org.apache.tools.ant.util.FileUtils;
import org.jacoco.core.data.ExecFileLoader;
import org.jacoco.core.data.ExecutionDataWriter;

/**
 * Task for merging a set of execution data store files into a single file
 */
public class MergeTask extends Task {

	private File destfile;

	private final Union files = new Union();

	/**
	 * Sets the location of the merged data store
	 * 
	 * @param destfile
	 *            Destination data store location
	 */
	public void setDestfile(final File destfile) {
		this.destfile = destfile;
	}

	/**
	 * This task accepts any number of execution data resources.
	 * 
	 * @param resources
	 *            Execution data resources
	 */
	public void addConfigured(final ResourceCollection resources) {
		files.add(resources);
	}

	@Override
	public void execute() throws BuildException {
		if (destfile == null) {
			throw new BuildException("Destination file must be supplied");
		}
		
		if (destfile.exists() && (!destfile.canWrite() || !destfile.isFile())) {
			throw new BuildException("Unable to write to destination file");
		}

		final ExecFileLoader loader = new ExecFileLoader();

		load(loader);
		save(loader);
	}

	private void load(final ExecFileLoader loader) {
		
		int numFilesMerged = 0;
		
		final Iterator<?> resourceIterator = files.iterator();
		while (resourceIterator.hasNext()) {
			final Resource resource = (Resource) resourceIterator.next();

			if (resource.isDirectory()) {
				continue;
			}

			//log(format("Loading execution data file %s", resource));
			log(String.format("Merging %s", resource.getName()),
					Project.MSG_DEBUG);

			InputStream resourceStream = null;
			try {
				resourceStream = resource.getInputStream();
				loader.load(resourceStream);
				
				numFilesMerged++;
			} catch (final IOException e) {
				throw new BuildException(format("Unable to read %s", resource),
						e);
			} finally {
				FileUtils.close(resourceStream);
			}
		}
		
		log(String.format("%d files merged", Integer.valueOf(numFilesMerged)),
				Project.MSG_INFO);
	}

	private void save(final ExecFileLoader loader) {
		log(format("Writing merged execution data to %s",
				destfile.getAbsolutePath()));
		OutputStream outputStream = null;
		try {
			FileUtils.getFileUtils().createNewFile(destfile, true);

			outputStream = new BufferedOutputStream(new FileOutputStream(
					destfile));
			final ExecutionDataWriter dataWriter = new ExecutionDataWriter(
					outputStream);
			loader.getSessionInfoStore().accept(dataWriter);
			loader.getExecutionDataStore().accept(dataWriter);
		} catch (final IOException e) {
			throw new BuildException(format("Unable to write merged file %s",
					destfile.getAbsolutePath()), e, getLocation());
		} finally {
			FileUtils.close(outputStream);
		}
	}

}
