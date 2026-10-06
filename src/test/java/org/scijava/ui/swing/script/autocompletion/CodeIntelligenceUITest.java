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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.junit.Test;
import org.scijava.code.api.CodeCompleter;
import org.scijava.code.api.Completion;
import org.scijava.code.api.CompletionRequest;
import org.scijava.code.api.CompletionResult;
import org.scijava.code.api.Diagnostic;
import org.scijava.code.api.Hover;
import org.scijava.code.api.SignatureHelp;
import org.scijava.code.api.SignatureHelp.Fit;

/**
 * Tests {@link HoverToolTipSupplier}, {@link DiagnosticsParser} and
 * {@link SignaturePopup}, with a fake completer.
 *
 * @author Gabriel Selzer
 */
public class CodeIntelligenceUITest {

	@Test
	public void testHover() {
		final AtomicInteger asked = new AtomicInteger();
		final CodeCompleter completer = new FakeCompleter() {

			@Override
			public Hover hover(final CompletionRequest request) {
				asked.incrementAndGet();
				final String text = request.text();
				final int at = request.offset();
				return text.startsWith("dumps", at - 1) || text.startsWith("dumps",
					at - 2) ? new Hover("<pre>Serialize.</pre>", 0, 5) : Hover.NONE;
			}
		};
		final HoverToolTipSupplier supplier = new HoverToolTipSupplier(completer,
			null, null, null);
		final String text = "x.dumps(1)";
		assertEquals("<html><pre>Serialize.</pre></html>", supplier.toolTip(text,
			3));
		// Elsewhere in the same word: the same answer, not asked again.
		assertEquals("<html><pre>Serialize.</pre></html>", supplier.toolTip(text,
			4));
		assertEquals(1, asked.get());
		// Not on a word: nothing, without asking.
		assertNull(supplier.toolTip(text, 7));
		assertNull(supplier.toolTip(text, -1));
		assertEquals(1, asked.get());
	}

	@Test
	public void testTruncate() {
		final StringBuilder long_ = new StringBuilder("<pre>");
		for (int i = 0; i < 40; i++) long_.append("line ").append(i).append('\n');
		final String cut = HoverToolTipSupplier.truncate(long_.append("</pre>")
			.toString());
		assertTrue(cut.contains("line 24"));
		assertTrue(!cut.contains("line 25"));
		assertTrue(cut.endsWith("…</pre>"));
		assertEquals("<pre>short</pre>", HoverToolTipSupplier.truncate(
			"<pre>short</pre>"));
	}

	@Test
	public void testDiagnostics() throws Exception {
		final CompletableFuture<List<Diagnostic>> found = new CompletableFuture<>();
		final CodeCompleter completer = new FakeCompleter() {

			@Override
			public CompletableFuture<List<Diagnostic>> diagnose(
				final CompletionRequest request)
			{
				return found;
			}
		};
		final RSyntaxTextArea[] area = { null };
		SwingUtilities.invokeAndWait(() -> area[0] = new RSyntaxTextArea(
			"x = 1\ny = (1 +\n"));
		final DiagnosticsParser parser = new DiagnosticsParser(area[0], completer,
			null, null, null);
		final RSyntaxDocument doc = (RSyntaxDocument) area[0].getDocument();
		// Asked; no answer yet: no problems.
		assertTrue(parser.parse(doc, null).getNotices().isEmpty());
		// The answer: a problem on the second line.
		found.complete(Collections.singletonList(new Diagnostic(10, 12,
			Diagnostic.Severity.ERROR, "Unclosed (", "fake")));
		SwingUtilities.invokeAndWait(() -> {});
		final ParseResult result = parser.parse(doc, null);
		assertEquals(1, result.getNotices().size());
		final ParserNotice notice = result.getNotices().get(0);
		assertEquals("Unclosed (", notice.getMessage());
		assertEquals(1, notice.getLine());
		assertEquals(10, notice.getOffset());
		assertEquals(2, notice.getLength());
		assertEquals(ParserNotice.Level.ERROR, notice.getLevel());
	}

	@Test
	public void testSignaturesHTML() {
		final Completion max = Completion.builder("max").kind(
			Completion.Kind.METHOD).parameters(Arrays.asList(new Completion.Parameter(
				"a", "double"), new Completion.Parameter("b", "double"))).returnType(
					"double").build();
		final Completion maxInt = Completion.builder("max").kind(
			Completion.Kind.METHOD).parameters(Arrays.asList(new Completion.Parameter(
				"a", "int"), new Completion.Parameter("b", "int"))).build();
		final String html = SignaturePopup.html(new SignatureHelp(Arrays.asList(
			new SignatureHelp.Signature(max, Fit.MATCH), new SignatureHelp.Signature(
				maxInt, Fit.MISMATCH)), 1, 8));
		// The parameter being typed in bold; mismatches struck out.
		assertTrue(html, html.startsWith(
			"<html>max(double a, <b>double b</b>) → double<br>"));
		assertTrue(html, html.contains("<s>max(int a, <b>int b</b>)</s>"));
	}

	/** A completer completing nothing. */
	private static class FakeCompleter implements CodeCompleter {

		@Override
		public CompletionResult complete(final CompletionRequest request) {
			return CompletionResult.EMPTY;
		}
	}
}
