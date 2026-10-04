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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.script.ScriptEngine;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionCellRenderer;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ParameterChoicesProvider;
import org.fife.ui.autocomplete.ParameterizedCompletion;
import org.scijava.log.Logger;
import org.scijava.script.ScriptLanguage;
import org.scijava.code.api.CodeCompleter;
import org.scijava.code.api.CompletionRequest;
import org.scijava.code.api.CompletionResult;
import org.scijava.code.api.ParameterChoices;

/**
 * The single bridge between SciJava's toolkit-agnostic code completion SPI
 * ({@link CodeCompleter}, {@link org.scijava.code.api.Completion}) and
 * RSyntaxTextArea's Swing-based completion machinery.
 * <p>
 * This provider delegates all language intelligence to a {@link CodeCompleter},
 * then translates the resulting neutral {@link CompletionResult} into RSTA
 * {@link Completion}s. Language adapters therefore need no dependency on Swing or
 * RSTA: they implement {@link CodeCompleter} (via a
 * {@link org.scijava.code.api.CodeCompleterPlugin}) and the script editor
 * renders the suggestions here.
 * </p>
 *
 * @author Curtis Rueden
 */
public class SciJavaCompletionProvider extends DefaultCompletionProvider {

	private final CodeCompleter completer;
	private final ScriptLanguage language;

	/** Optional logger, for reporting completer failures. */
	private Logger log;

	/** Optional live engine, set when completing in an interpreter. */
	private ScriptEngine engine;

	// Cache so getAlreadyEnteredText() and getCompletionsImpl() — which RSTA
	// calls in quick succession for the same caret — stay consistent and avoid
	// recomputation.
	private int cachedCaret = -1;
	private String cachedText;
	private CompletionResult cachedResult;

	/** The parameter-choices provider installed for the cached result, if any. */
	private ParameterChoicesProvider choicesProvider;

	public SciJavaCompletionProvider(final CodeCompleter completer,
		final ScriptLanguage language)
	{
		this.completer = completer;
		this.language = language;
		setParameterizedCompletionParams('(', ", ", ')');
		// Show each callable's parameters and return type in the popup list.
		setListCellRenderer(new CompletionCellRenderer());
		// Auto-activate after a letter, digit, '.' or '_'.
		setAutoActivationRules(true, ".");
	}

	/** Sets a logger with which to report completer failures. */
	public void setLogger(final Logger log) {
		this.log = log;
	}

	/** Sets a live script engine to enable variable/binding-based completion. */
	public void setEngine(final ScriptEngine engine) {
		this.engine = engine;
	}

	@Override
	public boolean isValidChar(final char c) {
		return Character.isLetterOrDigit(c) || c == '.' || c == '_';
	}

	@Override
	public String getAlreadyEnteredText(final JTextComponent comp) {
		final CompletionResult result = compute(comp);
		final int caret = comp.getCaretPosition();
		final int start = Math.min(Math.max(result.replaceStart(), 0), caret);
		try {
			return comp.getText(start, caret - start);
		}
		catch (final BadLocationException exc) {
			return "";
		}
	}

	@Override
	public List<Completion> getCompletionsImpl(final JTextComponent comp) {
		final CompletionResult result = compute(comp);
		final List<org.scijava.code.api.Completion> source =
			result.completions();
		final int n = source.size();
		final List<Completion> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(toRSTA(source.get(i), n - i));
		}
		return out;
	}

	// -- Helper methods --

	private CompletionResult compute(final JTextComponent comp) {
		final int caret = comp.getCaretPosition();
		final String text;
		try {
			text = comp.getText(0, comp.getDocument().getLength());
		}
		catch (final BadLocationException exc) {
			return CompletionResult.EMPTY;
		}
		if (caret == cachedCaret && text.equals(cachedText) &&
			cachedResult != null)
		{
			return cachedResult;
		}
		CompletionResult result;
		try {
			final CompletionRequest request = new CompletionRequest(text, caret,
				language, engine, engine == null ? null : engine.getContext());
			result = completer.complete(request);
			if (result == null) result = CompletionResult.EMPTY;
		}
		catch (final Exception | LinkageError exc) {
			// NB: Never let a misbehaving completer break the editor.
			if (log != null) log.debug("Code completion failed", exc);
			result = CompletionResult.EMPTY;
		}
		cachedCaret = caret;
		cachedText = text;
		cachedResult = result;
		// Wire up (or clear) parameter assistance for this result's callables.
		final ParameterChoices choices = result.parameterChoices();
		choicesProvider =
			choices == null ? null : new NeutralChoicesProvider(choices);
		setParameterChoicesProvider(choicesProvider);
		return result;
	}

	/**
	 * Returns the candidate values for the given parameter, per the completer's
	 * {@link ParameterChoices} for the completion state at {@code comp}'s caret.
	 * Mirrors what RSTA's parameter assistance shows; also handy for non-popup
	 * front ends and tests.
	 */
	List<Completion> parameterChoices(final JTextComponent comp,
		final ParameterizedCompletion.Parameter param)
	{
		compute(comp);
		return choicesProvider == null ? Collections.emptyList()
			: choicesProvider.getParameterChoices(comp, param);
	}

	/**
	 * Translates a neutral completion into an RSTA completion. Callables become
	 * {@link org.fife.ui.autocomplete.FunctionCompletion}s (so parameter
	 * assistance and parameter choices engage); everything else becomes a
	 * {@link BasicCompletion}. Either may carry additional edits (e.g.
	 * auto-imports). The {@code orderRelevance} argument preserves the completer's
	 * ordering when the completion does not specify its own relevance.
	 */
	private Completion toRSTA(final org.scijava.code.api.Completion c,
		final int orderRelevance)
	{
		final Completion rsta = c.isCallable() ? functionCompletion(c)
			: basicCompletion(c);
		final double rel = c.relevance();
		final int relevance = rel != 0 ? (int) Math.round(rel) : orderRelevance;
		if (rsta instanceof BasicCompletion) {
			((BasicCompletion) rsta).setRelevance(relevance);
		}
		return rsta;
	}

	private Completion basicCompletion(
		final org.scijava.code.api.Completion c)
	{
		return c.additionalEdits().isEmpty() //
			? new BasicCompletion(this, c.insertionText(), c.summary(),
				c.description()) //
			: new SciJavaCompletion(this, c.insertionText(), c.summary(),
				c.description(), c.additionalEdits());
	}

	private Completion functionCompletion(
		final org.scijava.code.api.Completion c)
	{
		// FunctionCompletion appends the parameter template itself, so strip any
		// trailing "()" the completer may have included in the insertion text.
		String name = c.insertionText();
		if (name.endsWith("()")) name = name.substring(0, name.length() - 2);
		final String returnType = c.returnType() == null ? "" : c.returnType();

		final SciJavaFunctionCompletion fc = new SciJavaFunctionCompletion(this,
			name, returnType, c.additionalEdits());
		fc.setReturnValueDescription(returnType);
		fc.setShortDescription(c.summary());
		if (c.description() != null) fc.setSummary(c.description());
		fc.setParams(toRSTAParams(c.parameters()));
		return fc;
	}

	private static List<ParameterizedCompletion.Parameter> toRSTAParams(
		final List<org.scijava.code.api.Completion.Parameter> params)
	{
		final List<ParameterizedCompletion.Parameter> out =
			new ArrayList<>(params.size());
		for (final org.scijava.code.api.Completion.Parameter p : params) {
			// Pass the type as the parameter's "type object" so the choices
			// provider can dispatch on it.
			out.add(new ParameterizedCompletion.Parameter(p.type(), p.name()));
		}
		return out;
	}

	/**
	 * Bridges a neutral {@link ParameterChoices} to RSTA's
	 * {@link ParameterChoicesProvider}, translating between the two parameter and
	 * completion representations.
	 */
	private final class NeutralChoicesProvider implements
		ParameterChoicesProvider
	{

		private final ParameterChoices choices;

		NeutralChoicesProvider(final ParameterChoices choices) {
			this.choices = choices;
		}

		@Override
		public List<Completion> getParameterChoices(final JTextComponent tc,
			final ParameterizedCompletion.Parameter param)
		{
			final org.scijava.code.api.Completion.Parameter neutral =
				new org.scijava.code.api.Completion.Parameter(param.getName(),
					param.getType());
			final List<org.scijava.code.api.Completion> result;
			try {
				result = choices.choicesFor(neutral);
			}
			catch (final Exception exc) {
				return Collections.emptyList();
			}
			if (result == null || result.isEmpty()) return Collections.emptyList();
			final List<Completion> out = new ArrayList<>(result.size());
			for (final org.scijava.code.api.Completion c : result) {
				out.add(new BasicCompletion(SciJavaCompletionProvider.this,
					c.insertionText(), c.summary(), c.description()));
			}
			return out;
		}
	}
}
