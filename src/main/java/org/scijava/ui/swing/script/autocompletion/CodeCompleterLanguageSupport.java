/*
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

package org.scijava.ui.swing.script.autocompletion;

import java.io.File;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import org.fife.rsta.ac.AbstractLanguageSupport;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.scijava.log.Logger;
import org.scijava.script.ScriptLanguage;
import org.scijava.code.api.CodeCompleter;
import org.scijava.code.api.CompletionRequest;

/**
 * An RSTA {@link org.fife.rsta.ac.LanguageSupport} that drives code completion
 * from a toolkit-agnostic {@link CodeCompleter}, via
 * {@link SciJavaCompletionProvider}.
 * <p>
 * This is the lightweight completion tier of the script editor: any language
 * that contributes a {@link org.scijava.code.api.CodeCompleterPlugin}
 * gets editor completion through this class, with no language-specific Swing or
 * RSTA code required.
 * </p>
 *
 * @author Curtis Rueden
 */
public class CodeCompleterLanguageSupport extends AbstractLanguageSupport {

	private final CodeCompleter completer;
	private final ScriptLanguage language;
	private final Logger log;
	private final Supplier<File> file;
	private boolean hoverEnabled = true;
	private boolean diagnosticsEnabled = true;

	/** What was installed in each text area, to uninstall. */
	private final Map<RSyntaxTextArea, Installed> installed =
		new WeakHashMap<>();

	public CodeCompleterLanguageSupport(final CodeCompleter completer,
		final ScriptLanguage language)
	{
		this(completer, language, null);
	}

	public CodeCompleterLanguageSupport(final CodeCompleter completer,
		final ScriptLanguage language, final Logger log)
	{
		this(completer, language, log, null);
	}

	/**
	 * @param file Supplies the file of the script being edited (or null if
	 *          unsaved), so that completers can resolve files relative to it.
	 */
	public CodeCompleterLanguageSupport(final CodeCompleter completer,
		final ScriptLanguage language, final Logger log,
		final Supplier<File> file)
	{
		this.completer = completer;
		this.language = language;
		this.log = log;
		this.file = file;
		setAutoCompleteEnabled(true);
		setParameterAssistanceEnabled(true);
		setShowDescWindow(true);
	}

	/**
	 * Sets whether to show what the completer knows about the code under the
	 * mouse (see {@link CodeCompleter#hover}). Applies to later installs.
	 */
	public void setHoverEnabled(final boolean enabled) {
		hoverEnabled = enabled;
	}

	/**
	 * Sets whether to show the problems the completer finds (see
	 * {@link CodeCompleter#diagnose}). Applies to later installs.
	 */
	public void setDiagnosticsEnabled(final boolean enabled) {
		diagnosticsEnabled = enabled;
	}

	@Override
	public void install(final RSyntaxTextArea textArea) {
		final SciJavaCompletionProvider provider =
			new SciJavaCompletionProvider(completer, language);
		provider.setLogger(log);
		provider.setFile(file);
		final AutoCompletion ac = new SciJavaAutoCompletion(provider);
		ac.setAutoCompleteEnabled(isAutoCompleteEnabled());
		ac.setAutoActivationEnabled(isAutoActivationEnabled());
		ac.setParameterAssistanceEnabled(isParameterAssistanceEnabled());
		ac.setShowDescWindow(getShowDescWindow());
		ac.install(textArea);
		installImpl(textArea, ac);

		// Documentation on hover, problems as squiggles, signatures as typed.
		final Installed extras = new Installed();
		if (hoverEnabled) {
			textArea.setToolTipSupplier(new HoverToolTipSupplier(completer,
				language, file, log));
		}
		if (diagnosticsEnabled) {
			extras.parser = new DiagnosticsParser(textArea, completer, language,
				file, log);
			textArea.addParser(extras.parser);
		}
		if (isParameterAssistanceEnabled()) {
			extras.popup = new SignaturePopup(textArea, provider::signatureHelp);
			extras.popup.install();
		}
		installed.put(textArea, extras);

		// Let the completer get ready for this script, e.g. warm up caches.
		try {
			final File f = file == null ? null : file.get();
			final String text = textArea.getText();
			completer.prepare(new CompletionRequest(text, text.length(), language,
				null, null, f == null ? null : f.getPath()));
		}
		catch (final Exception | LinkageError exc) {
			// NB: Preparation is optional; never let it break the editor.
			if (log != null) log.debug("Completer preparation failed", exc);
		}
	}

	@Override
	public void uninstall(final RSyntaxTextArea textArea) {
		uninstallImpl(textArea);
		final Installed extras = installed.remove(textArea);
		if (extras != null) {
			if (textArea.getToolTipSupplier() instanceof HoverToolTipSupplier) {
				textArea.setToolTipSupplier(null);
			}
			if (extras.parser != null) textArea.removeParser(extras.parser);
			if (extras.popup != null) extras.popup.uninstall();
		}
		// Let the completer release what it keeps for this script.
		try {
			final File f = file == null ? null : file.get();
			final String text = textArea.getText();
			completer.closed(new CompletionRequest(text, text.length(), language,
				null, null, f == null ? null : f.getPath()));
		}
		catch (final Exception | LinkageError exc) {
			if (log != null) log.debug("Completer release failed", exc);
		}
	}

	private static final class Installed {

		private DiagnosticsParser parser;
		private SignaturePopup popup;
	}
}
