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

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.script.ScriptEngine;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
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
import org.scijava.code.api.SignatureHelp;
import org.scijava.code.api.SignatureHelp.Fit;

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

	/** Optional supplier of the edited script's file. */
	private Supplier<File> file;

	// Cache so getAlreadyEnteredText() and getCompletionsImpl() — which RSTA
	// calls in quick succession for the same caret — stay consistent and avoid
	// recomputation.
	private int cachedCaret = -1;
	private String cachedText;
	private CompletionResult cachedResult;

	/** How long the parameter tooltip waits for better signature help, in ms. */
	private static final long HELP_BUDGET = 150;

	// The signature help for the current text and caret.
	private String helpText;
	private int helpCaret = -1;
	private volatile SignatureHelp help;

	/** Notified when a better result arrives for the current request. */
	private volatile Consumer<Boolean> updateListener;

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

	/**
	 * Sets a supplier of the file of the script being edited (which may supply
	 * null, for an unsaved script).
	 */
	public void setFile(final Supplier<File> file) {
		this.file = file;
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
		// Group callables by name, so each can list its sibling overloads.
		final Map<String, List<org.scijava.code.api.Completion>> overloads =
			new HashMap<>();
		for (final org.scijava.code.api.Completion c : source) {
			if (!c.isCallable()) continue;
			overloads.computeIfAbsent(stripParens(c.insertionText()),
				k -> new ArrayList<>()).add(c);
		}
		final int n = source.size();
		final List<Completion> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(toRSTA(comp, source.get(i), n - i, overloads));
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
			final File f = file == null ? null : file.get();
			final CompletionRequest request = new CompletionRequest(text, caret,
				language, engine, engine == null ? null : engine.getContext(), f ==
					null ? null : f.getPath());
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
		adopt(result, comp);
		return result;
	}

	/**
	 * Sets a listener to notify when a better result has arrived for the
	 * current request (see {@link CompletionResult#update()}), e.g. to refresh
	 * the completion popup. Called on the event dispatch thread.
	 *
	 * @param listener Accepts whether the previous result had no completions
	 *          (so that no popup may be showing for it).
	 */
	public void setUpdateListener(final Consumer<Boolean> listener) {
		this.updateListener = listener;
	}

	/** Makes the given result current, and awaits its update, if any. */
	private void adopt(final CompletionResult result, final JTextComponent comp) {
		cachedResult = result;
		// Wire up (or clear) parameter assistance for this result's callables.
		final ParameterChoices choices = result.parameterChoices();
		choicesProvider =
			choices == null ? null : new NeutralChoicesProvider(choices);
		setParameterChoicesProvider(choicesProvider);

		final CompletableFuture<CompletionResult> update = result.update();
		if (update == null) return;
		final String text = cachedText;
		final int caret = cachedCaret;
		update.whenComplete((better, error) -> {
			if (better == null) return;
			SwingUtilities.invokeLater(() -> {
				// NB: Only if nothing has changed since the request.
				if (cachedResult != result || caret != comp.getCaretPosition() ||
					!text.equals(comp.getText())) return;
				final boolean wasEmpty = result.completions().isEmpty();
				adopt(better, comp);
				final Consumer<Boolean> listener = updateListener;
				if (listener != null) listener.accept(wasEmpty);
			});
		});
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
	private Completion toRSTA(final JTextComponent comp,
		final org.scijava.code.api.Completion c, final int orderRelevance,
		final Map<String, List<org.scijava.code.api.Completion>> overloads)
	{
		final Completion rsta = c.isCallable() //
			? functionCompletion(comp, c, overloads.get(stripParens(c
				.insertionText()))) //
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
		// NB: The description may be expensive (computed on demand): ask for it
		// only when RSTA shows it, i.e. when the completion is selected.
		return new SciJavaCompletion(this, c.insertionText(), c.summary(),
			c::description, c.additionalEdits());
	}

	private Completion functionCompletion(final JTextComponent comp,
		final org.scijava.code.api.Completion c,
		final List<org.scijava.code.api.Completion> overloads)
	{
		// FunctionCompletion appends the parameter template itself, so strip any
		// trailing "()" the completer may have included in the insertion text.
		final String name = stripParens(c.insertionText());
		final String returnType = c.returnType() == null ? "" : c.returnType();

		final SciJavaFunctionCompletion fc = new SciJavaFunctionCompletion(this,
			name, returnType, c.additionalEdits());
		fc.setReturnValueDescription(returnType);
		fc.setShortDescription(c.summary());
		fc.setDescription(c::description);
		fc.setParams(toRSTAParams(comp, c, overloads));
		return fc;
	}

	/**
	 * Lists the parameters of {@code c}'s other overloads as HTML, one overload
	 * per line, for RSTA's parameter tooltip; or returns null if there are none.
	 * <p>
	 * If the completer's signature help is for this call, its signatures are
	 * listed in its order, styled by how well they fit the arguments typed so
	 * far: matches plain, those needing a conversion greyed, mismatches struck
	 * out. Otherwise, {@code overloads} are listed, unstyled.
	 * </p>
	 */
	static String otherOverloads(final org.scijava.code.api.Completion c,
		final List<org.scijava.code.api.Completion> overloads,
		final SignatureHelp help)
	{
		final String own = parameterList(c);
		// NB: A map, since reflection can report the same signature twice.
		final Map<String, Fit> lines = new LinkedHashMap<>();
		if (help != null && isFor(help, c)) {
			for (final SignatureHelp.Signature s : help.signatures()) {
				final String sig = parameterList(s.callable());
				if (!sig.equals(own)) lines.putIfAbsent(sig, s.fit());
			}
		}
		else if (overloads != null) {
			for (final org.scijava.code.api.Completion o : overloads) {
				final String sig = parameterList(o);
				if (!sig.equals(own)) lines.putIfAbsent(sig, Fit.UNKNOWN);
			}
		}
		if (lines.isEmpty()) return null;
		// NB: Read the color now, so the tooltip follows Look & Feel changes.
		final String dim = "<font color=\"" + dimColor() + "\">";
		final StringBuilder sb = new StringBuilder();
		for (final Map.Entry<String, Fit> line : lines.entrySet()) {
			// NB: A rule between overloads (and after RSTA's own line).
			sb.append("<hr>");
			final String sig = escapeHTML(line.getKey());
			switch (line.getValue()) {
				case CONVERSION:
					sb.append(dim).append(sig).append("</font>");
					break;
				case MISMATCH:
					sb.append(dim).append("<s>").append(sig).append("</s></font>");
					break;
				default:
					sb.append(sig);
			}
		}
		return sb.toString();
	}

	/**
	 * True iff the help is about calls to {@code c}, rather than, e.g., a call
	 * nested in its arguments.
	 */
	private static boolean isFor(final SignatureHelp help,
		final org.scijava.code.api.Completion c)
	{
		if (help.isEmpty()) return false;
		final String name = simpleName(c);
		for (final SignatureHelp.Signature s : help.signatures()) {
			if (!simpleName(s.callable()).equals(name)) return false;
		}
		return true;
	}

	private static String simpleName(final org.scijava.code.api.Completion c) {
		final String name = stripParens(c.insertionText());
		return name.substring(name.lastIndexOf('.') + 1);
	}

	/**
	 * Gets the completer's signature help at the caret (see
	 * {@link CodeCompleter#signatureHelp}), waiting briefly for a better
	 * answer, if one is announced. Remembered per text and caret.
	 */
	SignatureHelp signatureHelp(final JTextComponent comp) {
		final String text;
		try {
			text = comp.getText(0, comp.getDocument().getLength());
		}
		catch (final BadLocationException exc) {
			return SignatureHelp.NONE;
		}
		final int caret = comp.getCaretPosition();
		if (caret == helpCaret && text.equals(helpText) && help != null) {
			return help;
		}
		SignatureHelp h;
		try {
			final File f = file == null ? null : file.get();
			h = completer.signatureHelp(new CompletionRequest(text, caret, language,
				engine, engine == null ? null : engine.getContext(), f == null ? null
					: f.getPath()));
			if (h == null) h = SignatureHelp.NONE;
			if (h.update() != null) {
				try {
					final SignatureHelp better = h.update().get(HELP_BUDGET,
						TimeUnit.MILLISECONDS);
					if (better != null) h = better;
				}
				catch (final TimeoutException | ExecutionException exc) {
					// NB: Not in time: use what there is, and keep the better help
					// for when RSTA asks again (e.g. at the next parameter).
					final SignatureHelp first = h;
					h.update().thenAccept(better -> {
						if (better != null && help == first) help = better;
					});
				}
				catch (final InterruptedException exc) {
					Thread.currentThread().interrupt();
				}
			}
		}
		catch (final Exception | LinkageError exc) {
			// NB: Never let a misbehaving completer break the editor.
			if (log != null) log.debug("Signature help failed", exc);
			h = SignatureHelp.NONE;
		}
		helpText = text;
		helpCaret = caret;
		help = h;
		return h;
	}

	/**
	 * The Look & Feel's color for disabled text, as an HTML color such as
	 * {@code #8c8c8c}. RSTA's parameter tooltip takes its other colors from the
	 * Look & Feel too.
	 */
	static String dimColor() {
		Color c = UIManager.getColor("Label.disabledForeground");
		if (c == null) c = UIManager.getColor("textInactiveText");
		if (c == null) c = Color.GRAY;
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c
			.getBlue());
	}

	/**
	 * A short parameter list such as {@code long[] pos, int d}, or {@code ()} if
	 * there are no parameters.
	 */
	private static String parameterList(
		final org.scijava.code.api.Completion c)
	{
		final StringBuilder sb = new StringBuilder();
		final List<org.scijava.code.api.Completion.Parameter> params = c
			.parameters();
		for (int i = 0; i < params.size(); i++) {
			if (i > 0) sb.append(", ");
			final String type = params.get(i).type();
			if (type != null) sb.append(type.substring(type.lastIndexOf('.') + 1));
			if (params.get(i).name() != null) {
				if (type != null) sb.append(' ');
				sb.append(params.get(i).name());
			}
		}
		return sb.length() == 0 ? "()" : sb.toString();
	}

	private static String escapeHTML(final String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String stripParens(final String name) {
		return name.endsWith("()") ? name.substring(0, name.length() - 2) : name;
	}

	/**
	 * Converts {@code c}'s neutral parameters to RSTA ones. RSTA's parameter
	 * tooltip shows the current parameter's description, as HTML, below the
	 * signature: there, we list {@code c}'s other overloads.
	 */
	private List<ParameterizedCompletion.Parameter> toRSTAParams(
		final JTextComponent comp, final org.scijava.code.api.Completion c,
		final List<org.scijava.code.api.Completion> overloads)
	{
		final List<org.scijava.code.api.Completion.Parameter> params = c
			.parameters();
		final List<ParameterizedCompletion.Parameter> out =
			new ArrayList<>(params.size());
		for (final org.scijava.code.api.Completion.Parameter p : params) {
			// Pass the type as the parameter's "type object" so the choices
			// provider can dispatch on it.
			out.add(new ParameterizedCompletion.Parameter(p.type(), p.name()) {

				/**
				 * Computed when RSTA asks, so the list reflects the arguments typed
				 * so far. RSTA asks when the tooltip opens (before the argument list
				 * is inserted) and whenever the caret moves to another parameter.
				 */
				@Override
				public String getDescription() {
					return otherOverloads(c, overloads, signatureHelp(comp));
				}
			});
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
