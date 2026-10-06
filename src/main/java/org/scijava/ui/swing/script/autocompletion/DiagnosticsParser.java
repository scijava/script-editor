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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.scijava.code.api.CodeCompleter;
import org.scijava.code.api.CompletionRequest;
import org.scijava.code.api.Diagnostic;
import org.scijava.log.Logger;
import org.scijava.script.ScriptLanguage;

/**
 * Shows the problems the completer finds in the script (see
 * {@link CodeCompleter#diagnose}), e.g. syntax errors, as squiggles, with the
 * problem as tooltip.
 * <p>
 * RSyntaxTextArea parses a while after each edit, on the event dispatch
 * thread; this parser asks the completer then, and shows its answer once it
 * arrives (parsing anew). Until then, the problems found before stay.
 * </p>
 *
 * @author Gabriel Selzer
 */
public class DiagnosticsParser extends AbstractParser {

	private final RSyntaxTextArea textArea;
	private final CodeCompleter completer;
	private final ScriptLanguage language;
	private final Supplier<File> file;
	private final Logger log;

	/** The text asked about last, if not answered yet. */
	private String pending;

	/** The text answered last, and the problems found in it. */
	private String answered;
	private List<Diagnostic> problems = Collections.emptyList();

	public DiagnosticsParser(final RSyntaxTextArea textArea,
		final CodeCompleter completer, final ScriptLanguage language,
		final Supplier<File> file, final Logger log)
	{
		this.textArea = textArea;
		this.completer = completer;
		this.language = language;
		this.file = file;
		this.log = log;
	}

	@Override
	public ParseResult parse(final RSyntaxDocument doc, final String style) {
		final DefaultParseResult result = new DefaultParseResult(this);
		final String text;
		try {
			text = doc.getText(0, doc.getLength());
		}
		catch (final BadLocationException exc) {
			return result;
		}
		if (!text.equals(answered) && !text.equals(pending)) ask(text);
		// NB: Until the answer arrives, show the problems found before (where
		// they still fit).
		final Element root = doc.getDefaultRootElement();
		for (final Diagnostic d : problems) {
			if (d.start() >= text.length() && text.length() > 0) continue;
			final int start = Math.max(0, Math.min(d.start(), text.length() - 1));
			final int length = Math.max(1, Math.min(d.end(), text.length()) -
				start);
			final DefaultParserNotice notice = new DefaultParserNotice(this, d
				.message(), root.getElementIndex(start), start, length);
			notice.setLevel(level(d.severity()));
			result.addNotice(notice);
		}
		return result;
	}

	private void ask(final String text) {
		pending = text;
		CompletableFuture<List<Diagnostic>> answer;
		try {
			final File f = file == null ? null : file.get();
			answer = completer.diagnose(new CompletionRequest(text, text.length(),
				language, null, null, f == null ? null : f.getPath()));
		}
		catch (final Exception | LinkageError exc) {
			// NB: Never let a misbehaving completer break the editor.
			if (log != null) log.debug("Diagnosis failed", exc);
			answer = CompletableFuture.completedFuture(Collections.emptyList());
		}
		answer.whenComplete((found, error) -> SwingUtilities.invokeLater(() -> {
			if (!text.equals(pending)) return; // NB: Superseded.
			pending = null;
			answered = text;
			problems = found == null ? Collections.emptyList() : found;
			textArea.forceReparsing(this);
		}));
	}

	private static ParserNotice.Level level(final Diagnostic.Severity s) {
		switch (s) {
			case ERROR:
				return ParserNotice.Level.ERROR;
			case WARNING:
				return ParserNotice.Level.WARNING;
			default:
				return ParserNotice.Level.INFO;
		}
	}
}
