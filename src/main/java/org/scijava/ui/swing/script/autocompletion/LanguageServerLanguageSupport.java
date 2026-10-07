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
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.fife.rsta.ac.AbstractLanguageSupport;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.scijava.code.api.CodeCompleter;
import org.scijava.code.lsp.compat.CodeCompleterLanguageServer;
import org.scijava.log.Logger;
import org.scijava.script.ScriptLanguage;

/**
 * RSyntaxTextArea language support from a language server (usually one
 * standing for all of a script language's, see
 * {@code org.scijava.code.lsp.LanguageServerService}): completion with
 * parameter assistance, documentation on hover, problems as squiggles, and
 * signatures as a call is typed. The edited script is kept in sync with the
 * server (see {@link DocumentSync}): opened on install, changed with each
 * edit, closed on uninstall.
 *
 * @author Curtis Rueden
 * @author Gabriel Selzer
 */
public class LanguageServerLanguageSupport extends AbstractLanguageSupport {

	private final LanguageServer server;
	private final ScriptLanguage language;
	private final Logger log;
	private final Supplier<File> file;
	private boolean hoverEnabled = true;
	private boolean diagnosticsEnabled = true;

	/** What was installed in each text area, to uninstall. */
	private final Map<RSyntaxTextArea, Installed> installed =
		new WeakHashMap<>();

	/**
	 * @param server The language server (one per support: it is told about the
	 *          script, and publishes its problems to this support).
	 * @param language The script's language.
	 * @param log Where to report problems, or null.
	 * @param file Supplies the file of the script being edited (or null if
	 *          unsaved).
	 */
	public LanguageServerLanguageSupport(final LanguageServer server,
		final ScriptLanguage language, final Logger log,
		final Supplier<File> file)
	{
		this.server = server;
		this.language = language;
		this.log = log;
		this.file = file;
		setAutoCompleteEnabled(true);
		setParameterAssistanceEnabled(true);
		setShowDescWindow(true);
	}

	/**
	 * Uses a single code-api completer. TEMP: Until code-api is removed.
	 */
	public LanguageServerLanguageSupport(final CodeCompleter completer,
		final ScriptLanguage language, final Logger log,
		final Supplier<File> file)
	{
		this(new CodeCompleterLanguageServer(completer, language), language, log,
			file);
	}

	/**
	 * Sets whether to show what the server knows about the code under the
	 * mouse. Applies to later installs.
	 */
	public void setHoverEnabled(final boolean enabled) {
		hoverEnabled = enabled;
	}

	/**
	 * Sets whether to show the problems the server finds. Applies to later
	 * installs.
	 */
	public void setDiagnosticsEnabled(final boolean enabled) {
		diagnosticsEnabled = enabled;
	}

	@Override
	public void install(final RSyntaxTextArea textArea) {
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			server, language);
		provider.setLogger(log);
		provider.setFile(file);
		final DocumentSync sync = provider.sync();
		final AutoCompletion ac = new SciJavaAutoCompletion(provider);
		ac.setAutoCompleteEnabled(isAutoCompleteEnabled());
		ac.setAutoActivationEnabled(isAutoActivationEnabled());
		ac.setParameterAssistanceEnabled(isParameterAssistanceEnabled());
		ac.setShowDescWindow(getShowDescWindow());
		ac.install(textArea);
		installImpl(textArea, ac);

		// Documentation on hover, problems as squiggles, signatures as typed.
		final Installed extras = new Installed(sync);
		if (hoverEnabled) {
			textArea.setToolTipSupplier(new HoverToolTipSupplier(sync, log));
		}
		if (diagnosticsEnabled) {
			extras.parser = new DiagnosticsParser(textArea);
			textArea.addParser(extras.parser);
		}
		if (server instanceof LanguageClientAware) {
			((LanguageClientAware) server).connect(new Client(sync, extras.parser));
		}
		if (isParameterAssistanceEnabled()) {
			extras.popup = new SignaturePopup(textArea, provider::signatureHelp);
			extras.popup.install();
		}

		// Keep the server's copy of the script up to date: opened now (which
		// lets the servers get ready for it), changed with each edit.
		extras.listener = new DocumentListener() {

			@Override
			public void insertUpdate(final DocumentEvent e) {
				changed();
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {
				changed();
			}

			@Override
			public void changedUpdate(final DocumentEvent e) {
				// NB: Attributes only.
			}

			private void changed() {
				try {
					sync.sync(textArea.getText());
				}
				catch (final Exception | LinkageError exc) {
					// NB: Never let a misbehaving server break the editor.
					if (log != null) log.debug("Document sync failed", exc);
				}
			}
		};
		textArea.getDocument().addDocumentListener(extras.listener);
		installed.put(textArea, extras);
		try {
			sync.sync(textArea.getText());
		}
		catch (final Exception | LinkageError exc) {
			if (log != null) log.debug("Document sync failed", exc);
		}
	}

	@Override
	public void uninstall(final RSyntaxTextArea textArea) {
		uninstallImpl(textArea);
		final Installed extras = installed.remove(textArea);
		if (extras == null) return;
		textArea.getDocument().removeDocumentListener(extras.listener);
		if (textArea.getToolTipSupplier() instanceof HoverToolTipSupplier) {
			textArea.setToolTipSupplier(null);
		}
		if (extras.parser != null) textArea.removeParser(extras.parser);
		if (extras.popup != null) extras.popup.uninstall();
		// Let the servers release what they keep for this script.
		try {
			extras.sync.close();
		}
		catch (final Exception | LinkageError exc) {
			if (log != null) log.debug("Document close failed", exc);
		}
	}

	private static final class Installed {

		private final DocumentSync sync;
		private DiagnosticsParser parser;
		private SignaturePopup popup;
		private DocumentListener listener;

		private Installed(final DocumentSync sync) {
			this.sync = sync;
		}
	}

	/** Receives the server's messages: the script's problems, for the parser. */
	private final class Client implements LanguageClient {

		private final DocumentSync sync;
		private final DiagnosticsParser parser;

		private Client(final DocumentSync sync, final DiagnosticsParser parser) {
			this.sync = sync;
			this.parser = parser;
		}

		@Override
		public void publishDiagnostics(final PublishDiagnosticsParams p) {
			if (parser != null && p.getUri().equals(sync.uri())) {
				parser.accept(p.getDiagnostics());
			}
		}

		@Override
		public void telemetryEvent(final Object object) {}

		@Override
		public void showMessage(final MessageParams m) {
			if (log != null) log.debug("Language server: " + m.getMessage());
		}

		@Override
		public CompletableFuture<MessageActionItem> showMessageRequest(
			final ShowMessageRequestParams r)
		{
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void logMessage(final MessageParams m) {
			if (log != null) log.debug("Language server: " + m.getMessage());
		}
	}
}
