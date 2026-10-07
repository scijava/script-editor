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

import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.junit.Test;
import org.scijava.code.lsp.Environment;
import org.scijava.code.lsp.ScriptLanguageServer;
import org.scijava.code.lsp.ScriptSession;

/**
 * Tests {@link HoverToolTipSupplier}, {@link DiagnosticsParser} and
 * {@link SignaturePopup}, with a fake server.
 *
 * @author Gabriel Selzer
 */
public class CodeIntelligenceUITest {

	@Test
	public void testHover() {
		final AtomicInteger asked = new AtomicInteger();
		final ScriptLanguageServer server = new FakeServer() {

			@Override
			protected Hover hover(final Document doc, final int at) {
				asked.incrementAndGet();
				final String text = doc.text();
				if (!text.startsWith("dumps", at - 1) && !text.startsWith("dumps", at -
					2)) return null;
				final Hover hover = new Hover(new MarkupContent(MarkupKind.MARKDOWN,
					"<pre>Serialize.</pre>"));
				hover.setRange(doc.range(0, 5));
				return hover;
			}
		};
		final HoverToolTipSupplier supplier = new HoverToolTipSupplier(
			ScriptSession.owning(server, null), null);
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
		final RSyntaxTextArea[] area = { null };
		SwingUtilities.invokeAndWait(() -> area[0] = new RSyntaxTextArea(
			"x = 1\ny = (1 +\n"));
		final DiagnosticsParser parser = new DiagnosticsParser(area[0]);
		final RSyntaxDocument doc = (RSyntaxDocument) area[0].getDocument();
		// None published yet: no problems.
		assertTrue(parser.parse(doc, null).getNotices().isEmpty());
		// Published: a problem on the second line.
		final org.eclipse.lsp4j.Diagnostic d = new org.eclipse.lsp4j.Diagnostic(
			new org.eclipse.lsp4j.Range(new org.eclipse.lsp4j.Position(1, 4),
				new org.eclipse.lsp4j.Position(1, 6)), "Unclosed (");
		d.setSeverity(org.eclipse.lsp4j.DiagnosticSeverity.Error);
		parser.accept(Collections.singletonList(d));
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
		final String html = SignaturePopup.html(new org.eclipse.lsp4j.SignatureHelp(
			Arrays.asList(rated("max(double a, double b) -> double",
				org.scijava.code.lsp.RatedSignatureInformation.Fit.MATCH, "double a",
				"double b"), rated("max(int a, int b)",
					org.scijava.code.lsp.RatedSignatureInformation.Fit.MISMATCH, "int a",
					"int b")), 0, 1));
		// The parameter being typed in bold; mismatches struck out.
		assertTrue(html, html.startsWith(
			"<html>max(double a, <b>double b</b>) → double<br>"));
		assertTrue(html, html.contains("<s>max(int a, <b>int b</b>)</s>"));
	}

	private static org.eclipse.lsp4j.SignatureInformation rated(
		final String label, final org.scijava.code.lsp.RatedSignatureInformation.Fit fit,
		final String... params)
	{
		final List<org.eclipse.lsp4j.ParameterInformation> ps =
			new java.util.ArrayList<>();
		for (final String p : params) {
			ps.add(new org.eclipse.lsp4j.ParameterInformation(p));
		}
		return new org.scijava.code.lsp.RatedSignatureInformation(label, ps, fit);
	}

	/** A server completing nothing. */
	private static class FakeServer extends ScriptLanguageServer {

		FakeServer() {
			super(Environment.NONE);
		}

		@Override
		protected CompletionList complete(final Document doc, final int offset) {
			return new CompletionList();
		}
	}
}
