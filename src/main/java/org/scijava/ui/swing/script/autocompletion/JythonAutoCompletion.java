/*-
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2022 SciJava developers.
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
package org.scijava.ui.swing.script.autocompletion;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.BadLocationException;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.scijava.ui.swing.script.EditorPane;

public class JythonAutoCompletion extends AutoCompletion {

	public JythonAutoCompletion(final CompletionProvider provider) {
		super(provider);
		this.setShowDescWindow(true);
	}
	
	static private final Pattern importPattern = Pattern.compile("^(from[ \\t]+([a-zA-Z_][a-zA-Z0-9._]*)[ \\t]+|)import[ \\t]+([a-zA-Z_][a-zA-Z0-9_]*[ \\ta-zA-Z0-9_,]*)[ \\t]*([\\\\]*|)[  \\t]*(#.*|)$"),
								 tripleQuotePattern = Pattern.compile("\"\"\""),
								 variableDeclarationPattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9._]*)[ \\t]*=[ \\t]*([A-Z_][a-zA-Z0-9._]*)(?:\\()"); // E.g., in 'imp=ImagePlus()' group1: imp; group2: ImagePlus

	static public class Import {
		final public String className,
							alias; // same as simple class name, or the alias from "as <alias>"
		final public int lineNumber;
		
		public Import(final String className, final String alias, final int lineNumber) {
			this.className = className;
			this.alias = null != alias ? alias : className.substring(className.lastIndexOf('.') + 1);
			this.lineNumber = lineNumber;
		}
		
		// E.g. handle "ImageJFunctions as IL" -> ["ImageJFunctions", "as", "IL"]
		public Import(final String packageName, final String[] parts, final int lineNumber) {
			this(packageName + "." + parts[0], 3 == parts.length ? parts[2] : null, lineNumber);
		}
	}

	static public final String findClassAliasOfVariable(final String variable, String inputText) {
		final String[] lines = inputText.split("\n");
		for (int i = 0; i < lines.length; ++i) {
			final String line = lines[i];
			final Matcher matcher = variableDeclarationPattern.matcher(line);
			if (matcher.find()) {
				// a line containing a variable declaration
//				System.out.println("Queried variable: " + variable);
//				System.out.println("Hit: line #" + i + ": " + line);
//				System.out.println("Matcher g1: " + matcher.group(1));
//				System.out.println("Matcher g2: " + matcher.group(2));
				if (variable.equals(matcher.group(1))) {
					return matcher.group(2);
				}
			}
		}
		return null;
	}

	static public final HashMap<String, Import> findImportedClasses(final String text) {
		final HashMap<String, Import> importedClasses = new HashMap<>();
		String packageName = "";
		boolean endingBackslash = false;
		boolean insideTripleQuotes = false;
		
		// Scan the whole file for imports
		final String[] lines = text.split("\n");
		for (int i=0; i<lines.length; ++i) {
			String line = lines[i];
			final String trimmed = line.trim();
			if (0 == trimmed.length() || '#' == trimmed.charAt(0)) continue;
			final Matcher mq = tripleQuotePattern.matcher(line);
			int n_triple_quotes = 0;
			while (mq.find()) ++n_triple_quotes;
			if (insideTripleQuotes) {
				if (0 != n_triple_quotes % 2) { // odd number
					insideTripleQuotes = false;
				}
				continue;
			} else {
				// If odd, enter
				if (0 != n_triple_quotes % 2) {
					insideTripleQuotes = true;
					continue;
				}
			}
			// Handle classes imported in a truncated import statement
			if (endingBackslash) {
				String importLine = line;
				final int backslash = line.lastIndexOf('\\');
				if (backslash > -1) importLine = importLine.substring(0, backslash);
				else {
					final int sharp = importLine.lastIndexOf('#');
					if (sharp > -1) importLine = importLine.substring(0, sharp);
				}
				for (final String simpleClassName : importLine.split(",")) {
					final Import im = new Import(packageName, simpleClassName.trim().split("\\s"), i);
					importedClasses.put(im.alias, im);
				}
				endingBackslash = -1 != backslash; // otherwise there is another line with classes of the same package
				continue;
			}
			final Matcher m = importPattern.matcher(line);
			if (m.find()) {
				packageName = null == m.group(2) ? "" : m.group(2);
				for (final String simpleClassName : m.group(3).split(",")) {
					final Import im = new Import(packageName, simpleClassName.trim().split("\\s"), i);
					importedClasses.put(im.alias, im);
				}
				endingBackslash = null != m.group(4) && m.group(4).length() > 0 && '\\' == m.group(4).charAt(0);
			}
		}
		
		return importedClasses;
	}
	
	@Override
	protected void insertCompletion(final Completion c, final boolean typedParamListStartChar) {
		if (c instanceof ImportCompletion) {
			final EditorPane editor = (EditorPane) super.getTextComponent();
			editor.beginAtomicEdit();
			try {
				super.insertCompletion(c, typedParamListStartChar);
				final ImportCompletion cc = (ImportCompletion)c;
				final HashMap<String, Import> importedClasses = findImportedClasses(editor.getText());
				// Insert import statement after the last import, if not there already
				for (final Import im : importedClasses.values()) {
					if (im.className.contentEquals(cc.getClassName()))
						return; // don't insert
				}
				try {
					final int insertAtLine = 0 == importedClasses.size() ? 0
							: importedClasses.values().stream().map(im -> im.lineNumber).reduce(Math::max).get();
					editor.insert(cc.getImportStatement() + "\n", editor.getLineStartOffset(0 == insertAtLine ? 0 : insertAtLine + 1));
				} catch (BadLocationException e) {
					e.printStackTrace();
				}
			} finally {
				editor.endAtomicEdit();
			}
		} else {
			super.insertCompletion(c, typedParamListStartChar);
		}
	}
}
