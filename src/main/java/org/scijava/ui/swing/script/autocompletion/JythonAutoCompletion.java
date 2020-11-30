/*-
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2020 SciJava developers.
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

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.BadLocationException;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.scijava.script.ScriptLanguage;
import org.scijava.ui.swing.script.EditorPane;

public class JythonAutoCompletion extends AutoCompletion {

	public JythonAutoCompletion(final CompletionProvider provider) {
		super(provider);
		this.setShowDescWindow(true);
	}
	
	static private final Pattern importPattern = Pattern.compile("^(from[ \\t]+([a-zA-Z_][a-zA-Z0-9._]*)[ \\t]+|)import[ \\t]+([a-zA-Z_][a-zA-Z0-9_]*[ \\ta-zA-Z0-9_,]*)[ \\t]*([\\\\]*|)[  \\t]*(#.*|)$");

	@Override
	protected void insertCompletion(final Completion c, final boolean typedParamListStartChar) {
		if (c instanceof ImportCompletion) {
			final EditorPane editor = (EditorPane) super.getTextComponent();
			editor.beginAtomicEdit();
			try {
				super.insertCompletion(c, typedParamListStartChar);
				final ImportCompletion cc = (ImportCompletion)c;
				final HashSet<String> classNames = new HashSet<>();
				String packageName = "";
				boolean endingBackslash = false;
				int insertAtLine = 0;
				
				// Scan the whole file for imports
				final String[] lines = editor.getText().split("\n");
				for (int i=0; i<lines.length; ++i) {
					final String line = lines[i];
				
					// Handle classes imported in a truncated import statement
					if (endingBackslash) {
						String importLine = line;
						final int backslash = line.lastIndexOf('\\');
						if (backslash > -1) importLine = importLine.substring(0, backslash);
						else {
							final int sharp = importLine.lastIndexOf('#');
							if (sharp > -1) importLine = importLine.substring(0, sharp);
						}
						for (final String simpleClassname : importLine.split(",")) {
							classNames.add(packageName + "." + simpleClassname.trim());
						}
						endingBackslash = -1 != backslash; // otherwise there is another line with classes of the same package
						insertAtLine = i;
						continue;
					}
					final Matcher m = importPattern.matcher(line);
					if (m.find()) {
						packageName = null == m.group(2) ? "" : m.group(2);
						for (final String simpleClassName : m.group(3).split(",")) {
							classNames.add(packageName + "." + simpleClassName.trim());
						}
						endingBackslash = null != m.group(4) && m.group(4).length() > 0 && '\\' == m.group(4).charAt(0);
						insertAtLine = i;
					}
				}
				for (final String className : classNames) {
					System.out.println(className);
				}
				// Insert import statement after the last import, if not there already
				if (!classNames.contains(cc.className))
					try {
						editor.insert(cc.importStatement + "\n", editor.getLineStartOffset(insertAtLine + 1));
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
