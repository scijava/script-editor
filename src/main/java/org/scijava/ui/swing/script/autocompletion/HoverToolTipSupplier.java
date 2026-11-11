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

import java.awt.event.MouseEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.ToolTipSupplier;
import org.scijava.code.lsp.ScriptSession;
import org.scijava.log.Logger;

/**
 * Shows what the language server knows about the code under the mouse
 * ({@code textDocument/hover}), e.g. a function's documentation, as a tooltip.
 * <p>
 * RSyntaxTextArea asks for tooltips as the mouse moves, on the event dispatch
 * thread: so the server is asked once per word, and awaited only briefly;
 * until it answers, there is no tooltip.
 * </p>
 *
 * @author Gabriel Selzer
 */
public class HoverToolTipSupplier implements ToolTipSupplier {

	/** How long to wait for an answer, in milliseconds. */
	private static final long BUDGET = 100;

	/** How much of an answer to show, at most, in lines. */
	private static final int MAX_LINES = 25;

	private final ScriptSession session;
	private final Logger log;

	/** The last question (text and word), and its answer. */
	private String askedText;
	private int askedWord = -1;
	private CompletableFuture<Hover> answer;

	public HoverToolTipSupplier(final ScriptSession session, final Logger log) {
		this.session = session;
		this.log = log;
	}

	@Override
	public String getToolTipText(final RTextArea textArea, final MouseEvent e) {
		return toolTip(textArea.getText(), textArea.viewToModel2D(e.getPoint()));
	}

	/** Gets the tooltip for an offset of a text, or null if none. */
	String toolTip(final String text, final int offset) {
		if (offset < 0 || offset >= text.length() || !Character
			.isJavaIdentifierPart(text.charAt(offset))) return null;
		int word = offset;
		while (word > 0 && Character.isJavaIdentifierPart(text.charAt(word - 1))) {
			word--;
		}
		if (word != askedWord || !text.equals(askedText)) {
			askedText = text;
			askedWord = word;
			answer = ask(text, offset);
		}
		try {
			final String html = html(answer.get(BUDGET, TimeUnit.MILLISECONDS));
			return html == null ? null : "<html>" + truncate(html) + "</html>";
		}
		catch (final Exception exc) {
			return null; // NB: Not yet; or failed.
		}
	}

	private CompletableFuture<Hover> ask(final String text, final int offset) {
		try {
			return session.hover(text, offset);
		}
		catch (final Exception | LinkageError exc) {
			// NB: Never let a misbehaving server break the editor.
			if (log != null) log.debug("Hover failed", exc);
			return CompletableFuture.completedFuture(null);
		}
	}

	/** A hover's contents, as HTML; or null if none. */
	static String html(final Hover hover) {
		if (hover == null || hover.getContents() == null) return null;
		final Either<java.util.List<Either<String, MarkedString>>, MarkupContent> contents =
			hover.getContents();
		if (contents.isRight()) {
			return SciJavaCompletionProvider.html(Either.forRight(contents
				.getRight()));
		}
		final StringBuilder sb = new StringBuilder();
		for (final Either<String, MarkedString> part : contents.getLeft()) {
			if (sb.length() > 0) sb.append("\n\n");
			sb.append(part.isLeft() ? part.getLeft() : part.getRight().getValue());
		}
		return SciJavaCompletionProvider.html(Either.forLeft(sb.toString()));
	}

	/** Keeps the first lines of long answers (e.g. docstrings). */
	static String truncate(final String html) {
		int end = -1;
		for (int i = 0; i < MAX_LINES; i++) {
			end = html.indexOf('\n', end + 1);
			if (end < 0) return html;
		}
		// NB: Close an open <pre>, lest the rest of the tooltip be preformatted.
		final String head = html.substring(0, end);
		final boolean pre = head.lastIndexOf("<pre>") > head.lastIndexOf("</pre>");
		return head + "\n…" + (pre ? "</pre>" : "");
	}
}
