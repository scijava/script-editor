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

import java.util.Collections;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.scijava.code.lsp.Positions;

/**
 * Shows the problems language servers publish for the script (e.g. syntax
 * errors), as squiggles, with the problem as tooltip.
 * <p>
 * Servers publish problems when they like (e.g. a moment after each change):
 * each time, the text area parses anew to show them.
 * </p>
 *
 * @author Gabriel Selzer
 */
public class DiagnosticsParser extends AbstractParser {

	private final RSyntaxTextArea textArea;

	/** The problems last published. */
	private volatile List<Diagnostic> problems = Collections.emptyList();

	public DiagnosticsParser(final RSyntaxTextArea textArea) {
		this.textArea = textArea;
	}

	/** Shows the given problems (from any thread). */
	public void accept(final List<Diagnostic> found) {
		SwingUtilities.invokeLater(() -> {
			problems = found == null ? Collections.emptyList() : found;
			textArea.forceReparsing(this);
		});
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
		final Element root = doc.getDefaultRootElement();
		for (final Diagnostic d : problems) {
			int start = Positions.offset(text, d.getRange().getStart());
			final int end = Positions.offset(text, d.getRange().getEnd());
			if (start >= text.length() && text.length() > 0) {
				start = text.length() - 1; // NB: E.g. "unexpected end of file".
			}
			start = Math.max(0, start);
			final int length = Math.max(1, Math.min(end, text.length()) - start);
			final DefaultParserNotice notice = new DefaultParserNotice(this,
				message(d), root.getElementIndex(start), start, length);
			notice.setLevel(level(d.getSeverity()));
			result.addNotice(notice);
		}
		return result;
	}

	private static String message(final Diagnostic d) {
		if (d.getMessage() == null) return "";
		return d.getMessage().isLeft() ? d.getMessage().getLeft() : d.getMessage()
			.getRight().getValue();
	}

	private static ParserNotice.Level level(final DiagnosticSeverity s) {
		if (s == null || s == DiagnosticSeverity.Error) {
			return ParserNotice.Level.ERROR;
		}
		return s == DiagnosticSeverity.Warning ? ParserNotice.Level.WARNING
			: ParserNotice.Level.INFO;
	}
}
