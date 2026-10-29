/*-
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2026 SciJava developers.
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
package org.scijava.ui.swing.script;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.script.ScriptEngine;

import org.junit.Test;
import org.scijava.script.AbstractScriptLanguage;

public class EditorPaneFileNameTest {

	@Test
	public void testDetectsLanguageBeforeSettingFileName() {
		final TestEditorPane editorPane = new TestEditorPane();

		TextEditor.setNewDocumentLanguageAndFileName(editorPane, "Macro.ijm");

		assertEquals(Arrays.asList("language", "filename"), editorPane.operations);
	}

	@Test
	public void testStripsExistingLanguageExtension() {
		assertEquals("Macro", EditorPane.stripLanguageExtension("Macro.ijm",
			new TestMacroLanguage()));
	}

	private static final class TestEditorPane extends EditorPane {

		private final List<String> operations = new ArrayList<>();

		@Override
		protected void setLanguageByFileName(final String name) {
			operations.add("language");
		}

		@Override
		public void setFileName(final String baseName) {
			operations.add("filename");
		}
	}

	private static final class TestMacroLanguage extends AbstractScriptLanguage {

		@Override
		public String getLanguageName() {
			return "ImageJ Macro";
		}

		@Override
		public List<String> getExtensions() {
			return Collections.singletonList("ijm");
		}

		@Override
		public ScriptEngine getScriptEngine() {
			throw new UnsupportedOperationException("Test language has no script engine");
		}
	}
}
