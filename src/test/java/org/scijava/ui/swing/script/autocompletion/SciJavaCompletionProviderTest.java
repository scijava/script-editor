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
	public void testParameterTooltipRanksOverloadsByArgumentTypes() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("a");
		area.setCaretPosition(1);

		final org.scijava.code.api.TypeResolver types = expr -> {
			switch (expr) {
				case "1.5": return "double";
				case "1": return "long";
				default: return null;
			}
		};
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			request -> new CompletionResult(Arrays.asList( //
				method("a", "int", "int"), //
				method("a", "java.lang.String"), //
				method("a", "float", "float"), //
				method("a", "long", "long")), 0, null, types),
			null);
		final List<Completion> completions = provider.getCompletionsImpl(area);
		final org.fife.ui.autocomplete.ParameterizedCompletion intOverload =
			(org.fife.ui.autocomplete.ParameterizedCompletion) completions.get(0);
		final org.fife.ui.autocomplete.ParameterizedCompletion floatOverload =
			(org.fife.ui.autocomplete.ParameterizedCompletion) completions.get(2);
		final String gray = "<font color=\"gray\">";

		// Accepted a(int, int), typed a float, and tabbed onward: the float
		// overload matches, and the others are struck out.
		area.setText("a(1.5, p1)");
		area.setCaretPosition(7);
		assertEquals("<hr>float p0, float p1" + //
			"<hr>" + gray + "<s>String p0</s></font>" + //
			"<hr>" + gray + "<s>long p0, long p1</s></font>", //
			intOverload.getParam(1).getDescription());

		// Accepted a(float, float) and typed an int: the integral overloads
		// match, so they come first.
		area.setText("a(1, p1)");
		area.setCaretPosition(5);
		assertEquals("<hr>int p0, int p1<hr>long p0, long p1" + //
			"<hr>" + gray + "<s>String p0</s></font>", //
			floatOverload.getParam(1).getDescription());

		// Accepted a(int, int) and typed an int: the float overload would need a
		// conversion, so it comes after the long one, greyed.
		assertEquals("<hr>long p0, long p1" + //
			"<hr>" + gray + "float p0, float p1</font>" + //
			"<hr>" + gray + "<s>String p0</s></font>", //
			intOverload.getParam(1).getDescription());

		// Nothing typed yet: the overloads keep their order, unstyled.
		area.setText("a(p0, p1)");
		area.setCaretPosition(2);
		assertEquals("<hr>String p0<hr>float p0, float p1<hr>long p0, long p1", //
			intOverload.getParam(0).getDescription());
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
