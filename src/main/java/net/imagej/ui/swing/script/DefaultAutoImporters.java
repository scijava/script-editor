/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2014 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
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

package net.imagej.ui.swing.script;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.scijava.module.ModuleException;
import org.scijava.script.ScriptLanguage;

/**
 * Generates the statements for the auto-imports (for internal use by the script
 * editor only).
 * <p>
 * This class generates import statements for the deprecated auto-import feature
 * of the script editor and prefixes the {@link Reader} with those statements.
 * </p>
 * 
 * @author Johannes Schindelin
 */
class DefaultAutoImporters {

	static Reader prefixAutoImports(final ScriptLanguage language,
		final Collection<AutoImporter> importers, final Reader reader,
		final Writer errors) throws ModuleException
	{
		final String name = language == null ? null : language.getLanguageName();
		final ImportStatementGenerator generator;
		if ("Javascript".equals(name) || "ECMAScript".equals(name)) {
			generator = new DefaulImportStatements("importClass(Packages.", ");\n");
		}
		else if ("Beanshell".equals(name)) {
			generator = new DefaulImportStatements("import ", ";\n");
		}
		else if ("Ruby".equals(name)) {
			generator = new DefaulImportStatements("java_import '", "'\n");
		}
		else if ("Python".equals(name)) {
			generator = new ImportStatementGenerator() {

				@Override
				public void generate(final StringBuilder builder,
					final String packageName, final List<String> classNames)
				{
					// Due to the construction (filtering duplicate names),
					// classNames can be empty
					if (classNames.size() == 0) return;

					builder.append("from ").append(packageName).append(" import ");
					boolean first = true;
					for (final String className : classNames) {
						if (first) {
							first = false;
						}
						else {
							builder.append(", ");
						}
						builder.append(className);
					}
					builder.append("\n");
				}
			};
		}
		else {
			try {
				errors.write("[WARNING] Auto-imports not available for language '" +
					name + "'.\n");
			}
			catch (final IOException e) {
				throw new ModuleException(e);
			}
			return reader;
		}

		try {
			errors.write("[WARNING] Auto-imports are active, but deprecated.\n");
		}
		catch (final IOException e) {
			throw new ModuleException(e);
		}

		final StringBuilder builder = new StringBuilder();
		for (final AutoImporter importer : importers) {
			for (final Entry<String, List<String>> entry : importer
				.getDefaultImports().entrySet())
			{
				final String packageName = entry.getKey();
				final List<String> classNames = entry.getValue();
				generator.generate(builder, packageName, classNames);
			}
		}
		final String statements = builder.toString();

		try {
			final PushbackReader result =
				new PushbackReader(reader, statements.length());
			result.unread(statements.toCharArray());
			return result;
		}
		catch (final IOException e) {
			throw new ModuleException(e);
		}
	}

	private static interface ImportStatementGenerator {

		void generate(StringBuilder builder, String packageName,
			List<String> classNames);
	}

	private static class DefaulImportStatements implements
		ImportStatementGenerator
	{

		private final String prefix, suffix;
		private final Set<String> exclude;

		DefaulImportStatements(final String prefix, final String suffix,
			final String... classNamesToExclude)
		{
			this.prefix = prefix;
			this.suffix = suffix;
			exclude = new HashSet<String>(Arrays.asList(classNamesToExclude));
		}

		@Override
		public void generate(final StringBuilder builder, final String packageName,
			final List<String> classNames)
		{
			if ("java.lang".equals(packageName)) {
				return;
			}
			for (final String className : classNames) {
				if (exclude.contains(className)) continue;
				builder.append(prefix).append(packageName).append('.')
					.append(className).append(suffix);
			}
		}

	}

}
