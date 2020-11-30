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
package org.scijava.ui.swing.script.languagesupport;

import org.fife.rsta.ac.AbstractLanguageSupport;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.scijava.plugin.Plugin;
import org.scijava.ui.swing.script.LanguageSupportPlugin;
import org.scijava.ui.swing.script.LanguageSupportService;
import org.scijava.ui.swing.script.autocompletion.JythonAutocompletionProvider;
import org.scijava.ui.swing.script.autocompletion.JythonImportFormat;
import org.scijava.ui.swing.script.autocompletion.JythonAutoCompletion;

/**
 * {@link LanguageSupportPlugin} for the jython language.
 *
 * @author Albert Cardona
 * 
 * @see LanguageSupportService
 */
@Plugin(type = LanguageSupportPlugin.class)
public class JythonLanguageSupportPlugin extends  AbstractLanguageSupport implements LanguageSupportPlugin
{
	
	private AutoCompletion ac;
	private RSyntaxTextArea text_area;
	
	public JythonLanguageSupportPlugin() {
		setAutoCompleteEnabled(true);
		setShowDescWindow(true);
	}

	@Override
	public String getLanguageName() {
		return "python";
	}

	@Override
	public void install(final RSyntaxTextArea textArea) {
		this.text_area = textArea;
		this.ac = this.createAutoCompletion(null);
		this.ac.install(textArea);
		// store upstream
		super.installImpl(textArea, this.ac);
	}

	@Override
	public void uninstall(final RSyntaxTextArea textArea) {
		if (textArea == this.text_area) {
			super.uninstallImpl(textArea); // will call this.acp.uninstall();
		}
	}
	
	/**
	 * Ignores the argument.
	 */
	@Override
	protected AutoCompletion createAutoCompletion(CompletionProvider p) {
		 return new JythonAutoCompletion(new JythonAutocompletionProvider(text_area, new JythonImportFormat()));
	}

}
