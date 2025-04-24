/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2025 SciJava developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.ui.swing.script.languagesupport;

import java.io.File;
import java.io.IOException;

import org.fife.rsta.ac.java.JavaLanguageSupport;
import org.fife.rsta.ac.java.buildpath.JarLibraryInfo;
import org.fife.rsta.ac.java.buildpath.LibraryInfo;
import org.fife.rsta.ac.java.buildpath.ZipSourceLocation;
import org.scijava.plugin.Plugin;
import org.scijava.ui.swing.script.LanguageSupportPlugin;
import org.scijava.ui.swing.script.LanguageSupportService;

/**
 * {@link LanguageSupportPlugin} for the java language.
 *
 * @author Jonathan Hale
 * @see JavaLanguageSupport
 * @see LanguageSupportService
 */
@Plugin(type = LanguageSupportPlugin.class)
public class JavaLanguageSupportPlugin extends JavaLanguageSupport implements
	LanguageSupportPlugin
{

	public JavaLanguageSupportPlugin() throws IOException {
		final LibraryInfo info = getMainJreJarInfo();
		if (info != null) getJarManager().addClassFileSource(info);
	}

	@Override
	public String getLanguageName() {
		return "java";
	}

	// -- Helper methods --

	/**
	 * Replacement for {@link LibraryInfo#getMainJreJarInfo()}, which is smarter
	 * about Java 9+, and which does not spew messages to stderr.
	 */
	private static LibraryInfo getMainJreJarInfo() {
		String javaHome = System.getProperty("java.home");
		return getJreJarInfo(new File(javaHome));
	}

	/**
	 * Replacement for {@link LibraryInfo#getJreJarInfo(java.io.File)}, which is
	 * smarter about Java 9+, and which does not spew messages to stderr.
	 */
	private static LibraryInfo getJreJarInfo(final File jreHome) {
		final File classesArchive = findExistingPath(jreHome, "lib/rt.jar",
			"../Classes/classes.jar", "jmods/java.base.jmod");
		if (classesArchive == null) return null; // unsupported JRE structure

		final LibraryInfo info = new JarLibraryInfo(classesArchive);

		final File sourcesArchive = findExistingPath(jreHome, "lib/src.zip",
			"lib/src.jar", "src.zip", "../src.zip", "src.jar", "../src.jar");
		if (sourcesArchive != null) {
			info.setSourceLocation(new ZipSourceLocation(sourcesArchive));
		}

		return info;
	}

	private static File findExistingPath(final File baseDir, String... paths) {
		for (final String path : paths) {
			File file = new File(baseDir, path);
			if (file.exists()) return file;
		}
		return null;
	}
}
