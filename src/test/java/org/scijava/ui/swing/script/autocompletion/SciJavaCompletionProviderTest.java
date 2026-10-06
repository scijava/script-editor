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

import java.util.Arrays;
import java.util.List;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.Test;
import org.scijava.code.api.CompletionResult;
import org.scijava.code.api.SignatureHelp.Fit;

/**
 * Tests that {@link SciJavaCompletionProvider} faithfully bridges neutral
 * {@link org.scijava.code.api.Completion}s to RSTA completions, including
 * honoring the completer's reported replacement span.
 *
 * @author Curtis Rueden
 */
public class SciJavaCompletionProviderTest {

	@Test
	public void testBridgesNeutralCompletions() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("foo.ba");
		area.setCaretPosition(area.getDocument().getLength()); // offset 6

		// A completer that, regardless of input, suggests two members of "foo",
		// replacing the partial token "ba" (which starts at offset 4).
		final int replaceStart = 4;
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			request -> new CompletionResult(Arrays.asList(
				org.scijava.code.api.Completion.builder("bar")
					.summary("the bar member").build(),
				org.scijava.code.api.Completion.builder("baz").build()),
				replaceStart),
			null);

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
		final org.scijava.code.api.Completion.TextEdit importEdit =
			org.scijava.code.api.Completion.TextEdit.insert(0,
				"from ij.gui import Roi\n");
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			request -> new CompletionResult(java.util.Collections.singletonList(
				org.scijava.code.api.Completion.builder("Roi")
					.additionalEdits(java.util.Collections.singletonList(importEdit))
					.build()),
				0),
			null);

		final List<Completion> completions = provider.getCompletionsImpl(area);
		assertEquals(1, completions.size());
		// Completions carrying extra edits must be SciJavaCompletions so the
		// SciJavaAutoCompletion applies their auto-import on acceptance.
		final Completion c = completions.get(0);
		org.junit.Assert.assertTrue(c instanceof SciJavaCompletion);
		assertEquals(1, ((SciJavaCompletion) c).getAdditionalEdits().size());
	}

	@Test
	public void testCallablesBecomeFunctionCompletionsWithChoices() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("addRoi(");
		area.setCaretPosition(area.getDocument().getLength());

		// A callable completion taking one ij.gui.Roi parameter, plus a
		// parameter-choices resolver that offers an in-scope variable for it.
		final org.scijava.code.api.Completion.Parameter roiParam =
			new org.scijava.code.api.Completion.Parameter("roi", "ij.gui.Roi");
		final org.scijava.code.api.Completion method =
			org.scijava.code.api.Completion.builder("addRoi")
				.kind(org.scijava.code.api.Completion.Kind.METHOD)
				.parameters(java.util.Collections.singletonList(roiParam))
				.returnType("void").build();
		final org.scijava.code.api.ParameterChoices choices = p -> //
			"ij.gui.Roi".equals(p.type())
				? java.util.Collections.singletonList(
					org.scijava.code.api.Completion.of("myRoi"))
				: java.util.Collections.emptyList();
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			request -> new CompletionResult(java.util.Collections.singletonList(
				method), area.getDocument().getLength(), choices),
			null);

		// Callables surface as FunctionCompletions so parameter assistance works.
		final List<Completion> completions = provider.getCompletionsImpl(area);
		assertEquals(1, completions.size());
		org.junit.Assert.assertTrue(
			completions.get(0) instanceof SciJavaFunctionCompletion);

		// And the neutral ParameterChoices drives RSTA's parameter choices.
		final List<Completion> roiChoices = provider.parameterChoices(area,
			new org.fife.ui.autocomplete.ParameterizedCompletion.Parameter(
				"ij.gui.Roi", "roi"));
		assertEquals(1, roiChoices.size());
		assertEquals("myRoi", roiChoices.get(0).getReplacementText());
	}

	@Test
	public void testCallablesRenderWithSignatures() {
		final String completionText = "img.getAt";
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText(completionText);
		area.setCaretPosition(area.getDocument().getLength());

		final org.scijava.code.api.Completion method =
			org.scijava.code.api.Completion.builder(completionText)
				.kind(org.scijava.code.api.Completion.Kind.METHOD)
				.parameters(java.util.Collections.singletonList(
					new org.scijava.code.api.Completion.Parameter("pos", "long[]")))
				.returnType("java.lang.Object").build();
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			request -> new CompletionResult(java.util.Collections.singletonList(
				method), 0),
			null);

		// The popup list must show a callable's parameters, not just its name.
		final Completion c = provider.getCompletionsImpl(area).get(0);
		final javax.swing.JLabel label = (javax.swing.JLabel) provider
			.getListCellRenderer().getListCellRendererComponent(
				new javax.swing.JList<>(), c, 0, false, false);
		final String text = label.getText();
		org.junit.Assert.assertTrue(text, text.contains("long[]"));
		org.junit.Assert.assertTrue(text, text.contains("pos"));
	}

	@Test
	public void testParameterTooltipListsOtherOverloads() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("img.getAt");
		area.setCaretPosition(area.getDocument().getLength());

		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			request -> new CompletionResult(Arrays.asList( //
				method("img.getAt", "int[]"), //
				method("img.getAt", "net.imglib2.Localizable"), //
				method("img.getAt", "int[]"), // e.g. a bridge method
				method("img.getAtX", "long[]")), 0),
			null);
		final List<Completion> completions = provider.getCompletionsImpl(area);

		// Each overload's tooltip lists the others, once each, even before RSTA
		// inserts the argument list...
		assertEquals("<hr>Localizable p0",
			((org.fife.ui.autocomplete.ParameterizedCompletion) completions.get(0))
				.getParam(0).getDescription());
		// ...but the side description window does not.
		final String summary = completions.get(0).getSummary();
		org.junit.Assert.assertFalse(summary, summary.contains("Localizable"));

		// Inside the argument list, likewise.
		area.setText("img.getAt(p0)");
		area.setCaretPosition(10);
		final String desc = ((org.fife.ui.autocomplete.ParameterizedCompletion) //
		completions.get(0)).getParam(0).getDescription();
		assertEquals("<hr>Localizable p0", desc);
		// ...and a callable with no other overloads gets no description.
		org.junit.Assert.assertNull(((org.fife.ui.autocomplete.ParameterizedCompletion) //
		completions.get(3)).getParam(0).getDescription());
	}

	@Test
	public void testParameterTooltipShowsSignatureHelp() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("a");
		area.setCaretPosition(1);

		final org.scijava.code.api.Completion ints = method("a", "int", "int");
		final org.scijava.code.api.Completion strings = method("a",
			"java.lang.String");
		final org.scijava.code.api.Completion floats = method("a", "float",
			"float");
		final org.scijava.code.api.Completion longs = method("a", "long", "long");
		final org.scijava.code.api.CompletionRequest[] asked = { null };
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			new org.scijava.code.api.CodeCompleter() {

				@Override
				public CompletionResult complete(
					final org.scijava.code.api.CompletionRequest request)
				{
					return new CompletionResult(Arrays.asList(ints, strings, floats,
						longs), 0);
				}

				@Override
				public org.scijava.code.api.SignatureHelp signatureHelp(
					final org.scijava.code.api.CompletionRequest request)
				{
					asked[0] = request;
					// The completer rates and orders the signatures: here, as for a
					// float typed as the first argument.
					return new org.scijava.code.api.SignatureHelp(Arrays.asList(
						signature(floats, Fit.MATCH), //
						signature(ints, Fit.MISMATCH), //
						signature(longs, Fit.CONVERSION), //
						signature(strings, Fit.MISMATCH)), 1, 1);
				}
			}, null);
		final List<Completion> completions = provider.getCompletionsImpl(area);
		final org.fife.ui.autocomplete.ParameterizedCompletion intOverload =
			(org.fife.ui.autocomplete.ParameterizedCompletion) completions.get(0);
		final String gray = "<font color=\"" + SciJavaCompletionProvider
			.dimColor() + "\">";

		// Accepted a(int, int), typed a float, and tabbed onward: the completer's
		// order, styled by fit, without the accepted overload.
		area.setText("a(1.5, p1)");
		area.setCaretPosition(7);
		assertEquals("<hr>float p0, float p1" + //
			"<hr>" + gray + "long p0, long p1</font>" + //
			"<hr>" + gray + "<s>String p0</s></font>", //
			intOverload.getParam(1).getDescription());
		// The completer was asked about the caret.
		assertEquals(7, asked[0].offset());
		assertEquals("a(1.5, p1)", asked[0].text());
	}

	@Test
	public void testParameterTooltipIgnoresOtherCalls() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("a");
		area.setCaretPosition(1);
		final org.scijava.code.api.Completion ints = method("a", "int", "int");
		final org.scijava.code.api.Completion floats = method("a", "float",
			"float");
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			new org.scijava.code.api.CodeCompleter() {

				@Override
				public CompletionResult complete(
					final org.scijava.code.api.CompletionRequest request)
				{
					return new CompletionResult(Arrays.asList(ints, floats), 0);
				}

				@Override
				public org.scijava.code.api.SignatureHelp signatureHelp(
					final org.scijava.code.api.CompletionRequest request)
				{
					// The caret is in a call nested in a's arguments: b's signatures.
					return new org.scijava.code.api.SignatureHelp(Arrays.asList(
						signature(method("b", "double"), Fit.MATCH)), 0, 2);
				}
			}, null);
		final org.fife.ui.autocomplete.ParameterizedCompletion intOverload =
			(org.fife.ui.autocomplete.ParameterizedCompletion) provider
				.getCompletionsImpl(area).get(0);
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
		final java.util.concurrent.atomic.AtomicInteger asked =
			new java.util.concurrent.atomic.AtomicInteger();
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			request -> new CompletionResult(Arrays.asList( //
				org.scijava.code.api.Completion.builder("zeros").kind(
					org.scijava.code.api.Completion.Kind.METHOD).parameters(Arrays
						.asList(new org.scijava.code.api.Completion.Parameter("shape",
							"int"))).lazyDescription(() -> {
								asked.incrementAndGet();
								return "Return a new array of zeros.";
							}).build(), //
				org.scijava.code.api.Completion.builder("zeta").lazyDescription(
					() -> {
						asked.incrementAndGet();
						return "The zeta function.";
					}).build()), 3), null);
		final List<Completion> completions = provider.getCompletionsImpl(area);
		// Nothing is described until RSTA shows a completion's description...
		assertEquals(0, asked.get());
		// ...and then it is shown, for functions as for everything else.
		org.junit.Assert.assertTrue(completions.get(0).getSummary().contains(
			"Return a new array of zeros."));
		assertEquals("The zeta function.", completions.get(1).getSummary());
		assertEquals(2, asked.get());
	}

	@Test
	public void testResultUpdates() throws Exception {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("np.z");
		area.setCaretPosition(4);
		final java.util.concurrent.CompletableFuture<CompletionResult> later =
			new java.util.concurrent.CompletableFuture<>();
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			request -> CompletionResult.EMPTY.withUpdate(later), null);
		final Boolean[] notified = { null };
		provider.setUpdateListener(wasEmpty -> notified[0] = wasEmpty);

		// At first, nothing; then the better result arrives.
		assertEquals(0, provider.getCompletionsImpl(area).size());
		later.complete(new CompletionResult(Arrays.asList(
			org.scijava.code.api.Completion.of("zeros")), 3));
		javax.swing.SwingUtilities.invokeAndWait(() -> {});
		assertEquals(Boolean.TRUE, notified[0]);
		final List<Completion> completions = provider.getCompletionsImpl(area);
		assertEquals(1, completions.size());
		assertEquals("zeros", completions.get(0).getReplacementText());

		// An update for a request the user has typed past is ignored.
		final java.util.concurrent.CompletableFuture<CompletionResult> stale =
			new java.util.concurrent.CompletableFuture<>();
		final SciJavaCompletionProvider provider2 = new SciJavaCompletionProvider(
			request -> CompletionResult.EMPTY.withUpdate(stale), null);
		notified[0] = null;
		provider2.setUpdateListener(wasEmpty -> notified[0] = wasEmpty);
		provider2.getCompletionsImpl(area);
		area.setText("np.ze");
		area.setCaretPosition(5);
		stale.complete(new CompletionResult(Arrays.asList(
			org.scijava.code.api.Completion.of("zeros")), 3));
		javax.swing.SwingUtilities.invokeAndWait(() -> {});
		org.junit.Assert.assertNull(notified[0]);
	}

	@Test
	public void testPrepareOnInstall() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("import numpy\n");
		final org.scijava.code.api.CompletionRequest[] prepared = { null };
		final java.io.File file = new java.io.File("scripts", "blur.py");
		final CodeCompleterLanguageSupport support =
			new CodeCompleterLanguageSupport(new org.scijava.code.api.CodeCompleter()
			{

				@Override
				public CompletionResult complete(
					final org.scijava.code.api.CompletionRequest request)
				{
					return CompletionResult.EMPTY;
				}

				@Override
				public void prepare(
					final org.scijava.code.api.CompletionRequest request)
				{
					prepared[0] = request;
				}
			}, null, null, () -> file);
		support.install(area);
		assertEquals("import numpy\n", prepared[0].text());
		assertEquals(file.getPath(), prepared[0].path());
		support.uninstall(area);
	}

	@Test
	public void testDimColorFollowsLookAndFeel() {
		final Object old = javax.swing.UIManager.get("Label.disabledForeground");
		try {
			javax.swing.UIManager.put("Label.disabledForeground",
				new java.awt.Color(0x12, 0x34, 0xab));
			assertEquals("#1234ab", SciJavaCompletionProvider.dimColor());
		}
		finally {
			javax.swing.UIManager.put("Label.disabledForeground", old);
		}
	}

	@Test
	public void testRequestCarriesScriptPath() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("x");
		area.setCaretPosition(1);
		final String[] path = { "unset" };
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			request -> {
				path[0] = request.path();
				return CompletionResult.EMPTY;
			}, null);

		// Without a file supplier (e.g. in an interpreter): no path.
		provider.getCompletionsImpl(area);
		org.junit.Assert.assertNull(path[0]);

		// With one: the script's path, even if it changes (e.g. Save As).
		final java.io.File[] file = { new java.io.File("scripts", "a.py") };
		provider.setFile(() -> file[0]);
		area.setText("xy");
		area.setCaretPosition(2);
		provider.getCompletionsImpl(area);
		assertEquals(file[0].getPath(), path[0]);
	}

	private static org.scijava.code.api.SignatureHelp.Signature signature(
		final org.scijava.code.api.Completion callable, final Fit fit)
	{
		return new org.scijava.code.api.SignatureHelp.Signature(callable, fit);
	}

	private static org.scijava.code.api.Completion method(final String name,
		final String... paramTypes)
	{
		final List<org.scijava.code.api.Completion.Parameter> params =
			new java.util.ArrayList<>();
		for (int i = 0; i < paramTypes.length; i++) {
			params.add(new org.scijava.code.api.Completion.Parameter("p" + i,
				paramTypes[i]));
		}
		return org.scijava.code.api.Completion.builder(name).kind(
			org.scijava.code.api.Completion.Kind.METHOD).parameters(params)
			.returnType("java.lang.Object").build();
	}
}
