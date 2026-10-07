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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.ParameterizedCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.Test;
import org.scijava.code.lsp.Callable;
import org.scijava.code.lsp.Environment;
import org.scijava.code.lsp.Positions;
import org.scijava.code.lsp.RatedSignatureInformation.Fit;
import org.scijava.code.lsp.ScriptLanguageServer;
import org.scijava.code.lsp.UpdatingCompletionList;

/**
 * Tests that {@link SciJavaCompletionProvider} faithfully bridges a language
 * server's completions to RSTA completions, including honoring the range each
 * completion replaces.
 *
 * @author Curtis Rueden
 * @author Gabriel Selzer
 */
public class SciJavaCompletionProviderTest {

	@Test
	public void testBridgesCompletions() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("foo.ba");
		area.setCaretPosition(area.getDocument().getLength()); // offset 6

		// A server that, regardless of input, suggests two members of "foo",
		// replacing the partial token "ba" (which starts at offset 4).
		final FakeServer server = new FakeServer();
		server.complete = (doc, caret) -> {
			final CompletionItem bar = new CompletionItem("bar");
			bar.setDetail("the bar member");
			return FakeServer.items(doc, 4, caret, bar, new CompletionItem("baz"));
		};
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			server, null);

		// The provider must report the already-entered text as the replaced span.
		assertEquals("ba", provider.getAlreadyEnteredText(area));

		// And it must surface both suggestions as RSTA completions, in order.
		final List<Completion> completions = provider.getCompletionsImpl(area);
		assertEquals(2, completions.size());
		assertEquals("bar", completions.get(0).getReplacementText());
		assertEquals("baz", completions.get(1).getReplacementText());
	}

	@Test
	public void testAdditionalEditsSurfaceAsSciJavaCompletion() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("Roi");
		area.setCaretPosition(area.getDocument().getLength());

		// A class-name completion that also auto-imports at the top of the file.
		final FakeServer server = new FakeServer();
		server.complete = (doc, caret) -> {
			final CompletionItem roi = new CompletionItem("Roi");
			roi.setAdditionalTextEdits(Collections.singletonList(new TextEdit(
				doc.range(0, 0), "from ij.gui import Roi\n")));
			return FakeServer.items(doc, 0, caret, roi);
		};
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			server, null);

		final List<Completion> completions = provider.getCompletionsImpl(area);
		assertEquals(1, completions.size());
		// Completions carrying extra edits must be SciJavaCompletions so the
		// SciJavaAutoCompletion applies their auto-import on acceptance.
		final Completion c = completions.get(0);
		assertTrue(c instanceof SciJavaCompletion);
		final List<AdditionalEdits.Edit> edits = ((SciJavaCompletion) c)
			.getAdditionalEdits();
		assertEquals(1, edits.size());
		assertEquals(0, edits.get(0).start());
		assertEquals("from ij.gui import Roi\n", edits.get(0).newText());
	}

	@Test
	public void testCallablesBecomeFunctionCompletionsWithChoices() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("addR");
		area.setCaretPosition(area.getDocument().getLength());

		// A callable completion taking one ij.gui.Roi parameter; and, inside its
		// argument list, an in-scope variable fitting it (and a method).
		final Callable addRoi = new Callable("addRoi", Collections.singletonList(
			new Callable.Param("roi", "ij.gui.Roi")), "void").kind(
				CompletionItemKind.Method);
		final FakeServer server = new FakeServer();
		server.complete = (doc, caret) -> {
			if (!doc.lineTo(caret).endsWith("(")) {
				return FakeServer.items(doc, 0, caret, addRoi.toItem());
			}
			final CompletionItem myRoi = new CompletionItem("myRoi");
			myRoi.setKind(CompletionItemKind.Variable);
			return FakeServer.items(doc, caret, caret, myRoi, addRoi.toItem());
		};
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			server, null);

		// Callables surface as FunctionCompletions so parameter assistance works.
		final List<Completion> completions = provider.getCompletionsImpl(area);
		assertEquals(1, completions.size());
		assertTrue(completions.get(0) instanceof SciJavaFunctionCompletion);

		// Parameter choices: the variables completed at the parameter.
		area.setText("addRoi(");
		area.setCaretPosition(area.getDocument().getLength());
		final List<Completion> roiChoices = provider.parameterChoices(area,
			new ParameterizedCompletion.Parameter("ij.gui.Roi", "roi"));
		assertEquals(1, roiChoices.size());
		assertEquals("myRoi", roiChoices.get(0).getReplacementText());
	}

	@Test
	public void testCallablesRenderWithSignatures() {
		final String completionText = "img.getAt";
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText(completionText);
		area.setCaretPosition(area.getDocument().getLength());

		final FakeServer server = new FakeServer();
		server.complete = (doc, caret) -> FakeServer.items(doc, 0, caret, method(
			completionText, "long[]").toItem());
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			server, null);

		// The popup list must show a callable's parameters, not just its name.
		final Completion c = provider.getCompletionsImpl(area).get(0);
		final JLabel label = (JLabel) provider.getListCellRenderer()
			.getListCellRendererComponent(new JList<>(), c, 0, false, false);
		final String text = label.getText();
		assertTrue(text, text.contains("long[]"));
		assertTrue(text, text.contains("p0"));
	}

	@Test
	public void testParameterTooltipListsOtherOverloads() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("img.getAt");
		area.setCaretPosition(area.getDocument().getLength());

		final FakeServer server = new FakeServer();
		server.complete = (doc, caret) -> FakeServer.items(doc, 0, caret, //
			method("img.getAt", "int[]").toItem(), //
			method("img.getAt", "net.imglib2.Localizable").toItem(), //
			method("img.getAt", "int[]").toItem(), // e.g. a bridge method
			method("img.getAtX", "long[]").toItem());
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			server, null);
		final List<Completion> completions = provider.getCompletionsImpl(area);

		// Each overload's tooltip lists the others, once each, even before RSTA
		// inserts the argument list...
		assertEquals("<hr>Localizable p0", ((ParameterizedCompletion) completions
			.get(0)).getParam(0).getDescription());
		// ...but the side description window does not.
		final String summary = completions.get(0).getSummary();
		assertFalse(summary, summary != null && summary.contains("Localizable"));

		// Inside the argument list, likewise.
		area.setText("img.getAt(p0)");
		area.setCaretPosition(10);
		final String desc = ((ParameterizedCompletion) completions.get(0))
			.getParam(0).getDescription();
		assertEquals("<hr>Localizable p0", desc);
		// ...and a callable with no other overloads gets no description.
		assertNull(((ParameterizedCompletion) completions.get(completions.size() -
			1)).getParam(0).getDescription());
	}

	@Test
	public void testParameterTooltipShowsSignatureHelp() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("a");
		area.setCaretPosition(1);

		final Callable ints = method("a", "int", "int");
		final Callable strings = method("a", "java.lang.String");
		final Callable floats = method("a", "float", "float");
		final Callable longs = method("a", "long", "long");
		final String[] askedText = { null };
		final int[] askedCaret = { -1 };
		final FakeServer server = new FakeServer();
		server.complete = (doc, caret) -> FakeServer.items(doc, 0, caret, ints
			.toItem(), strings.toItem(), floats.toItem(), longs.toItem());
		server.help = (doc, caret) -> {
			askedText[0] = doc.text();
			askedCaret[0] = caret;
			// The server rates and orders the signatures: here, as for a float
			// typed as the first argument.
			return new SignatureHelp(Arrays.asList(floats.toSignature(Fit.MATCH),
				ints.toSignature(Fit.MISMATCH), longs.toSignature(Fit.CONVERSION),
				strings.toSignature(Fit.MISMATCH)), 0, 1);
		};
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			server, null);
		final List<Completion> completions = provider.getCompletionsImpl(area);
		final ParameterizedCompletion intOverload =
			(ParameterizedCompletion) completions.get(0);
		final String gray = "<font color=\"" + SciJavaCompletionProvider
			.dimColor() + "\">";

		// Accepted a(int, int), typed a float, and tabbed onward: the server's
		// order, styled by fit, without the accepted overload.
		area.setText("a(1.5, p1)");
		area.setCaretPosition(7);
		assertEquals("<hr>float p0, float p1" + //
			"<hr>" + gray + "long p0, long p1</font>" + //
			"<hr>" + gray + "<s>String p0</s></font>", //
			intOverload.getParam(1).getDescription());
		// The server was asked about the caret.
		assertEquals(7, askedCaret[0]);
		assertEquals("a(1.5, p1)", askedText[0]);
	}

	@Test
	public void testParameterTooltipIgnoresOtherCalls() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("a");
		area.setCaretPosition(1);
		final Callable ints = method("a", "int", "int");
		final Callable floats = method("a", "float", "float");
		final FakeServer server = new FakeServer();
		server.complete = (doc, caret) -> FakeServer.items(doc, 0, caret, ints
			.toItem(), floats.toItem());
		// The caret is in a call nested in a's arguments: b's signatures.
		server.help = (doc, caret) -> new SignatureHelp(Collections.singletonList(
			method("b", "double").toSignature(Fit.MATCH)), 0, 0);
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			server, null);
		final ParameterizedCompletion intOverload =
			(ParameterizedCompletion) provider.getCompletionsImpl(area).get(0);
		area.setText("a(b(|), p1)");
		area.setCaretPosition(4);
		// Not b's: a's other overloads, unranked.
		assertEquals("<hr>float p0, float p1", intOverload.getParam(0)
			.getDescription());
	}

	@Test
	public void testLazyDescriptions() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("np.ze");
		area.setCaretPosition(5);
		final AtomicInteger asked = new AtomicInteger();
		final FakeServer server = new FakeServer();
		server.complete = (doc, caret) -> FakeServer.items(doc, 3, caret, //
			server.described(new Callable("zeros", Collections.singletonList(
				new Callable.Param("shape", "int")), null).kind(
					CompletionItemKind.Method).toItem(), () -> {
						asked.incrementAndGet();
						return "Return a new array of zeros.";
					}), //
			server.described(new CompletionItem("zeta"), () -> {
				asked.incrementAndGet();
				return "The zeta function.";
			}));
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			server, null);
		final List<Completion> completions = provider.getCompletionsImpl(area);
		// Nothing is described until RSTA shows a completion's description...
		assertEquals(0, asked.get());
		// ...and then it is shown, for functions as for everything else.
		assertTrue(completions.get(0).getSummary().contains(
			"Return a new array of zeros."));
		assertTrue(completions.get(1).getSummary().contains("The zeta function."));
		assertEquals(2, asked.get());
	}

	@Test
	public void testResultUpdates() throws Exception {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("np.z");
		area.setCaretPosition(4);
		final CompletableFuture<CompletionList> later = new CompletableFuture<>();
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			new UpdatingServer(later), null);
		final Boolean[] notified = { null };
		provider.setUpdateListener(wasEmpty -> notified[0] = wasEmpty);

		// At first, nothing; then the better result arrives.
		assertEquals(0, provider.getCompletionsImpl(area).size());
		later.complete(zeros("np.z"));
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(Boolean.TRUE, notified[0]);
		final List<Completion> completions = provider.getCompletionsImpl(area);
		assertEquals(1, completions.size());
		assertEquals("zeros", completions.get(0).getReplacementText());

		// An update for a request the user has typed past is ignored.
		final CompletableFuture<CompletionList> stale = new CompletableFuture<>();
		final SciJavaCompletionProvider provider2 = new SciJavaCompletionProvider(
			new UpdatingServer(stale), null);
		notified[0] = null;
		provider2.setUpdateListener(wasEmpty -> notified[0] = wasEmpty);
		area.setText("np.z");
		area.setCaretPosition(4);
		provider2.getCompletionsImpl(area);
		area.setText("np.ze");
		area.setCaretPosition(5);
		stale.complete(zeros("np.z"));
		SwingUtilities.invokeAndWait(() -> {});
		assertNull(notified[0]);
	}

	@Test
	public void testOpenOnInstall() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("import numpy\n");
		final File file = new File("scripts", "blur.py");
		final FakeServer server = new FakeServer();
		final LanguageServerLanguageSupport support =
			new LanguageServerLanguageSupport(server, null, null, () -> file);
		support.install(area);
		assertEquals(1, server.opened.size());
		assertEquals("import numpy\n", server.opened.get(0).text());
		assertEquals(file.getAbsolutePath(), server.opened.get(0).path());
		support.uninstall(area);
		assertEquals(1, server.closed.size());
	}

	@Test
	public void testDimColorFollowsLookAndFeel() {
		final Object old = UIManager.get("Label.disabledForeground");
		try {
			UIManager.put("Label.disabledForeground", new java.awt.Color(0x12, 0x34,
				0xab));
			assertEquals("#1234ab", SciJavaCompletionProvider.dimColor());
		}
		finally {
			UIManager.put("Label.disabledForeground", old);
		}
	}

	@Test
	public void testRequestCarriesScriptPath() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("x");
		area.setCaretPosition(1);
		final String[] path = { "unset" };
		final FakeServer server = new FakeServer();
		server.complete = (doc, caret) -> {
			path[0] = doc.path();
			return new CompletionList();
		};
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			server, null);

		// Without a file supplier (e.g. in an interpreter): no path.
		provider.getCompletionsImpl(area);
		assertNull(path[0]);

		// With one: the script's path, even if it changes (e.g. Save As).
		final File[] file = { new File("scripts", "a.py") };
		provider.setFile(() -> file[0]);
		area.setText("xy");
		area.setCaretPosition(2);
		provider.getCompletionsImpl(area);
		assertEquals(file[0].getAbsolutePath(), path[0]);
	}

	// -- Helper methods --

	private static Callable method(final String name,
		final String... paramTypes)
	{
		final List<Callable.Param> params = new ArrayList<>();
		for (int i = 0; i < paramTypes.length; i++) {
			params.add(new Callable.Param("p" + i, paramTypes[i]));
		}
		return new Callable(name, params, "java.lang.Object").kind(
			CompletionItemKind.Method);
	}

	/** "zeros", replacing what follows the dot, up to the end of the text. */
	private static CompletionList zeros(final String text) {
		final CompletionItem zeros = new CompletionItem("zeros");
		zeros.setTextEdit(Either.forLeft(new TextEdit(new Range(Positions.position(
			text, text.indexOf('.') + 1), Positions.position(text, text.length())),
			"zeros")));
		return new CompletionList(false, Collections.singletonList(zeros));
	}

	// -- Helper classes --

	/** A server answering as its functions say. */
	private static class FakeServer extends ScriptLanguageServer {

		BiFunction<Document, Integer, CompletionList> complete = (doc,
			caret) -> new CompletionList();
		BiFunction<Document, Integer, SignatureHelp> help = (doc, caret) -> null;
		final List<Document> opened = new ArrayList<>();
		final List<Document> closed = new ArrayList<>();

		FakeServer() {
			super(Environment.NONE);
		}

		/** Completions replacing the text from {@code start} to the caret. */
		static CompletionList items(final Document doc, final int start,
			final int caret, final CompletionItem... items)
		{
			return list(doc, start, caret, new ArrayList<>(Arrays.asList(items)));
		}

		/** A completion documented on demand. */
		CompletionItem described(final CompletionItem item,
			final Supplier<String> html)
		{
			return describe(item, html);
		}

		@Override
		protected CompletionList complete(final Document doc, final int offset) {
			return complete.apply(doc, offset);
		}

		@Override
		protected SignatureHelp signatureHelp(final Document doc,
			final int offset)
		{
			return help.apply(doc, offset);
		}

		@Override
		protected void opened(final Document doc) {
			opened.add(doc);
		}

		@Override
		protected void closed(final Document doc) {
			closed.add(doc);
		}
	}

	/** A server with nothing at first, and more to come. */
	private static class UpdatingServer extends FakeServer {

		private final CompletableFuture<CompletionList> update;

		UpdatingServer(final CompletableFuture<CompletionList> update) {
			this.update = update;
		}

		@Override
		public CompletableFuture<Either<List<CompletionItem>, CompletionList>>
			completion(final CompletionParams params)
		{
			return CompletableFuture.completedFuture(Either.forRight(
				new UpdatingCompletionList(Collections.emptyList(), update)));
		}
	}
}
