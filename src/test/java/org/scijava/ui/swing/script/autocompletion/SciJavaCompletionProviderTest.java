/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2025 SciJava developers.
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
import org.scijava.script.complete.CompletionResult;

/**
 * Tests that {@link SciJavaCompletionProvider} faithfully bridges neutral
 * {@link org.scijava.script.complete.Completion}s to RSTA completions, including
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
				org.scijava.script.complete.Completion.builder("bar")
					.summary("the bar member").build(),
				org.scijava.script.complete.Completion.builder("baz").build()),
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
		final org.scijava.script.complete.Completion.TextEdit importEdit =
			org.scijava.script.complete.Completion.TextEdit.insert(0,
				"from ij.gui import Roi\n");
		final SciJavaCompletionProvider provider = new SciJavaCompletionProvider(
			request -> new CompletionResult(java.util.Collections.singletonList(
				org.scijava.script.complete.Completion.builder("Roi")
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
		final org.scijava.script.complete.Completion.Parameter roiParam =
			new org.scijava.script.complete.Completion.Parameter("roi", "ij.gui.Roi");
		final org.scijava.script.complete.Completion method =
			org.scijava.script.complete.Completion.builder("addRoi")
				.kind(org.scijava.script.complete.Completion.Kind.METHOD)
				.parameters(java.util.Collections.singletonList(roiParam))
				.returnType("void").build();
		final org.scijava.script.complete.ParameterChoices choices = p -> //
			"ij.gui.Roi".equals(p.type())
				? java.util.Collections.singletonList(
					org.scijava.script.complete.Completion.of("myRoi"))
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
}
