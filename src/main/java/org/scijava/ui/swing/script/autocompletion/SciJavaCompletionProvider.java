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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionCellRenderer;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ParameterizedCompletion;
import org.scijava.code.lsp.Positions;
import org.scijava.code.lsp.RatedSignatureInformation.Fit;
import org.scijava.code.lsp.RatedSignatureInformation;
import org.scijava.code.lsp.ScriptSession;
import org.scijava.code.lsp.UpdatingCompletionList;
import org.scijava.log.Logger;

/**
 * The bridge between a language server (usually one standing for all of a
 * script language's, see {@code org.scijava.code.lsp.LanguageServerService})
 * and RSyntaxTextArea's completion machinery.
 * <p>
 * Completions with a snippet of parameters (e.g. {@code max(${1:a},
 * ${2:b})}) become function completions, for parameter assistance; their
 * parameters' types come from the label details (e.g. {@code (double a,
 * double b)}). The parameter tooltip lists the other overloads, as signature
 * help rates them for the arguments typed (see
 * {@link RatedSignatureInformation}). Documentation is resolved when shown.
 * Slower completions (see {@link UpdatingCompletionList}) replace the first
 * ones as they arrive.
 * </p>
 *
 * @author Curtis Rueden
 * @author Gabriel Selzer
 */
public class SciJavaCompletionProvider extends DefaultCompletionProvider {

	/** How long to wait for completions, in milliseconds. */
	private static final long REQUEST_TIMEOUT = 2000;

	/** How long the parameter tooltip waits for signature help, in ms. */
	private static final long HELP_BUDGET = 150;

	/** How long to wait for documentation, in milliseconds. */
	private static final long DESCRIBE_TIMEOUT = 1000;

	/** How many choices to offer for a parameter, at most. */
	private static final int CHOICES = 10;

	/** A snippet placeholder: ${1:name}, ${1} or $1. */
	private static final Pattern PLACEHOLDER = Pattern.compile(
		"\\$\\{(\\d+)(?::((?:[^}\\\\]|\\\\.)*))?\\}|\\$(\\d+)");

	private final ScriptSession session;

	/** Optional logger, for reporting server failures. */
	private Logger log;

	// Cache so getAlreadyEnteredText() and getCompletionsImpl(), which RSTA
	// calls in quick succession for the same caret, stay consistent.
	private int cachedCaret = -1;
	private String cachedText;
	private CompletionList cachedList;
	private int cachedStart;

	// The signature help for the current text and caret.
	private String helpText;
	private int helpCaret = -1;
	private volatile SignatureHelp help;

	/** Notified when better completions arrive for the current request. */
	private volatile Consumer<Boolean> updateListener;

	/** @param session Where to ask about the script being edited. */
	public SciJavaCompletionProvider(final ScriptSession session) {
		this.session = session;
		setParameterizedCompletionParams('(', ", ", ')');
		// Show each callable's parameters and return type in the popup list.
		setListCellRenderer(new CompletionCellRenderer());
		// Auto-activate after a letter, digit, '.' or '_'.
		setAutoActivationRules(true, ".");
		setParameterChoicesProvider(this::parameterChoices);
	}

	/** Where this asks about the script being edited. */
	public ScriptSession session() {
		return session;
	}

	/** Sets a logger with which to report server failures. */
	public void setLogger(final Logger log) {
		this.log = log;
	}

	@Override
	public boolean isValidChar(final char c) {
		return Character.isLetterOrDigit(c) || c == '.' || c == '_';
	}

	@Override
	public String getAlreadyEnteredText(final JTextComponent comp) {
		compute(comp);
		final int caret = comp.getCaretPosition();
		final int start = Math.min(Math.max(cachedStart, 0), caret);
		try {
			return comp.getText(start, caret - start);
		}
		catch (final BadLocationException exc) {
			return "";
		}
	}

	@Override
	public List<Completion> getCompletionsImpl(final JTextComponent comp) {
		compute(comp);
		return convert(comp, cachedList, cachedText, cachedCaret, cachedStart);
	}

	/**
	 * Sets a listener to notify when better completions have arrived for the
	 * current request (see {@link UpdatingCompletionList}), e.g. to refresh the
	 * completion popup. Called on the event dispatch thread.
	 *
	 * @param listener Accepts whether the previous completions were none (so
	 *          that no popup may be showing for them).
	 */
	public void setUpdateListener(final Consumer<Boolean> listener) {
		this.updateListener = listener;
	}

	/**
	 * Gets choices for a parameter being filled in: what the server completes
	 * there, variables first (the server ranks those fitting best first).
	 */
	List<Completion> parameterChoices(final JTextComponent comp,
		final ParameterizedCompletion.Parameter param)
	{
		final String text = comp.getText();
		final CompletionList list = request(text, comp.getCaretPosition());
		final List<Completion> out = new ArrayList<>();
		for (final CompletionItem item : sorted(list)) {
			final CompletionItemKind k = item.getKind();
			if (k != null && k != CompletionItemKind.Variable &&
				k != CompletionItemKind.Field && k != CompletionItemKind.Constant &&
				k != CompletionItemKind.Value) continue;
			final String name = plain(newText(item));
			out.add(new BasicCompletion(this, name, item.getDetail()));
			if (out.size() >= CHOICES) break;
		}
		return out;
	}

	/**
	 * Gets signature help at the caret, waiting briefly. Remembered per text
	 * and caret; an answer arriving late is used when asked again.
	 */
	SignatureHelp signatureHelp(final JTextComponent comp) {
		final String text;
		try {
			text = comp.getText(0, comp.getDocument().getLength());
		}
		catch (final BadLocationException exc) {
			return null;
		}
		final int caret = comp.getCaretPosition();
		if (caret == helpCaret && text.equals(helpText) && help != null) {
			return help;
		}
		SignatureHelp h = null;
		try {
			final CompletableFuture<SignatureHelp> answer = session.signatureHelp(
				text, caret);
			try {
				h = answer.get(HELP_BUDGET, TimeUnit.MILLISECONDS);
			}
			catch (final TimeoutException exc) {
				// NB: Not in time: keep it for when RSTA asks again.
				answer.thenAccept(late -> {
					if (late != null && text.equals(helpText) && caret == helpCaret) {
						help = late;
					}
				});
			}
		}
		catch (final Exception | LinkageError exc) {
			// NB: Never let a misbehaving server break the editor.
			if (log != null) log.debug("Signature help failed", exc);
		}
		helpText = text;
		helpCaret = caret;
		help = h;
		return h;
	}

	/**
	 * Lists the parameters of a callable's other overloads as HTML, one
	 * overload per line, for RSTA's parameter tooltip; or returns null if there
	 * are none.
	 * <p>
	 * If the signature help is for this call, its signatures are listed in its
	 * order, styled by how well they fit the arguments typed so far: matches
	 * plain, those needing a conversion greyed, mismatches struck out.
	 * Otherwise, {@code overloads} are listed, unstyled.
	 * </p>
	 *
	 * @param name The callable's name.
	 * @param own Its parameters (e.g. {@code double a}).
	 * @param overloads The parameters of its overloads in the completions.
	 * @param help The signature help at the caret, or null.
	 */
	static String otherOverloads(final String name, final List<String> own,
		final List<List<String>> overloads, final SignatureHelp help)
	{
		final String ownList = parameterList(own);
		// NB: A map, since reflection can report the same signature twice.
		final Map<String, Fit> lines = new LinkedHashMap<>();
		if (help != null && isFor(help, name)) {
			for (final SignatureInformation s : help.getSignatures()) {
				final String sig = parameterList(SignatureLabel.of(s).parameters);
				if (!sig.equals(ownList)) lines.putIfAbsent(sig, RatedSignatureInformation
					.fitOf(s));
			}
		}
		else if (overloads != null) {
			for (final List<String> o : overloads) {
				final String sig = parameterList(o);
				if (!sig.equals(ownList)) lines.putIfAbsent(sig, Fit.UNKNOWN);
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

	/** Converts documentation to HTML: Markdown as is (it allows HTML). */
	static String html(final Either<String, MarkupContent> doc) {
		if (doc == null) return null;
		final String text;
		if (doc.isLeft()) text = "<pre>" + escapeHTML(doc.getLeft()) + "</pre>";
		else if (MarkupKind.MARKDOWN.equals(doc.getRight().getKind())) {
			text = doc.getRight().getValue();
		}
		else text = "<pre>" + escapeHTML(doc.getRight().getValue()) + "</pre>";
		return text == null || text.isEmpty() || text.equals("<pre></pre>") ? null
			: text;
	}

	// -- Helper methods --

	private void compute(final JTextComponent comp) {
		final int caret = comp.getCaretPosition();
		final String text;
		try {
			text = comp.getText(0, comp.getDocument().getLength());
		}
		catch (final BadLocationException exc) {
			return;
		}
		if (caret == cachedCaret && text.equals(cachedText) && cachedList != null) {
			return;
		}
		final CompletionList list = request(text, caret);
		cachedCaret = caret;
		cachedText = text;
		adopt(list, comp);
	}

	/** Asks the server for completions; none if it fails. */
	private CompletionList request(final String text, final int caret) {
		try {
			return session.completion(text, caret).get(REQUEST_TIMEOUT,
				TimeUnit.MILLISECONDS);
		}
		catch (final Exception | LinkageError exc) {
			// NB: Never let a misbehaving server break the editor.
			if (log != null) log.debug("Code completion failed", exc);
			return new CompletionList();
		}
	}

	/** Makes the given completions current, and awaits their update, if any. */
	private void adopt(final CompletionList list, final JTextComponent comp) {
		cachedList = list;
		cachedStart = replaceStart(list, cachedText, cachedCaret);
		if (!(list instanceof UpdatingCompletionList)) return;
		final CompletableFuture<CompletionList> update =
			((UpdatingCompletionList) list).update();
		if (update == null) return;
		final String text = cachedText;
		final int caret = cachedCaret;
		update.whenComplete((better, error) -> {
			if (better == null) return;
			SwingUtilities.invokeLater(() -> {
				// NB: Only if nothing has changed since the request.
				if (cachedList != list || caret != comp.getCaretPosition() || !text
					.equals(comp.getText())) return;
				final boolean wasEmpty = list.getItems() == null || list.getItems()
					.isEmpty();
				adopt(better, comp);
				final Consumer<Boolean> listener = updateListener;
				if (listener != null) listener.accept(wasEmpty);
			});
		});
	}

	/**
	 * Where the completions' replacement starts: the earliest of their edits'
	 * starts (completions without an edit replace the word before the caret).
	 */
	private static int replaceStart(final CompletionList list, final String text,
		final int caret)
	{
		int start = caret;
		for (final CompletionItem item : items(list)) {
			start = Math.min(start, itemStart(item, text, caret));
		}
		return start;
	}

	private static int itemStart(final CompletionItem item, final String text,
		final int caret)
	{
		if (item.getTextEdit() != null) {
			final Range r = item.getTextEdit().isLeft() ? item.getTextEdit()
				.getLeft().getRange() : item.getTextEdit().getRight().getInsert();
			return Math.min(caret, Positions.offset(text, r.getStart()));
		}
		int start = Math.min(caret, text.length());
		while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start -
			1))) start--;
		return start;
	}

	/** Converts the server's completions to RSTA's. */
	private List<Completion> convert(final JTextComponent comp,
		final CompletionList list, final String text, final int caret,
		final int start)
	{
		final List<CompletionItem> items = sorted(list);
		// Group callables by name, so each can list its sibling overloads.
		final Map<String, List<List<String>>> overloads = new HashMap<>();
		final List<Object[]> converted = new ArrayList<>();
		for (final CompletionItem item : items) {
			final String prefix = text.substring(start, Math.max(start, itemStart(
				item, text, caret)));
			final String inserted = newText(item);
			final boolean callable = isSnippet(item) && inserted.indexOf('(') > 0;
			if (callable) {
				final String name = prefix + inserted.substring(0, inserted.indexOf(
					'('));
				final List<String> params = parameters(item, inserted);
				overloads.computeIfAbsent(name, k -> new ArrayList<>()).add(params);
				converted.add(new Object[] { item, name, params });
			}
			else converted.add(new Object[] { item, prefix + plain(inserted), null });
		}
		final int n = converted.size();
		final List<Completion> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			final CompletionItem item = (CompletionItem) converted.get(i)[0];
			final String name = (String) converted.get(i)[1];
			@SuppressWarnings("unchecked")
			final List<String> params = (List<String>) converted.get(i)[2];
			final Supplier<String> description = () -> describe(item);
			final List<AdditionalEdits.Edit> edits = edits(item,
				text);
			final Completion c;
			if (params != null) {
				final String returnType = item.getLabelDetails() == null ? null : item
					.getLabelDetails().getDescription();
				final SciJavaFunctionCompletion fc = new SciJavaFunctionCompletion(this,
					name, returnType == null ? "" : returnType, edits);
				fc.setReturnValueDescription(returnType == null ? "" : returnType);
				fc.setShortDescription(item.getDetail());
				fc.setDescription(description);
				fc.setParams(rstaParams(comp, name, params, overloads.get(name)));
				fc.setRelevance(n - i);
				c = fc;
			}
			else {
				final SciJavaCompletion sc = new SciJavaCompletion(this, name, item
					.getDetail(), description, edits);
				sc.setRelevance(n - i);
				c = sc;
			}
			out.add(c);
		}
		return out;
	}

	/**
	 * Converts parameters (e.g. {@code double a}) to RSTA's. RSTA's parameter
	 * tooltip shows the current parameter's description, as HTML, below the
	 * signature: there, we list the other overloads.
	 */
	private List<ParameterizedCompletion.Parameter> rstaParams(
		final JTextComponent comp, final String name, final List<String> params,
		final List<List<String>> overloads)
	{
		final List<ParameterizedCompletion.Parameter> out = new ArrayList<>();
		for (final String p : params) {
			final String[] typeAndName = SignatureLabel.typeAndName(p);
			out.add(new ParameterizedCompletion.Parameter(typeAndName[0],
				typeAndName[1])
			{

				/**
				 * Computed when RSTA asks, so the list reflects the arguments typed
				 * so far. RSTA asks when the tooltip opens (before the argument list
				 * is inserted) and whenever the caret moves to another parameter.
				 */
				@Override
				public String getDescription() {
					return otherOverloads(name, params, overloads, signatureHelp(comp));
				}
			});
		}
		return out;
	}

	/**
	 * A callable's parameters: from its label details (typed, e.g.
	 * {@code (double a, double b)}), if they agree with its snippet's
	 * placeholders; otherwise the placeholders' names.
	 */
	private static List<String> parameters(final CompletionItem item,
		final String snippet)
	{
		final List<String> names = new ArrayList<>();
		final Matcher m = PLACEHOLDER.matcher(snippet);
		while (m.find()) {
			final String name = m.group(2) != null ? m.group(2) : "arg" + names
				.size();
			names.add(unescape(name));
		}
		final String detail = item.getLabelDetails() == null ? null : item
			.getLabelDetails().getDetail();
		if (detail != null) {
			final List<String> typed = SignatureLabel.parameters(detail);
			if (typed.size() == names.size()) return typed;
		}
		return names;
	}

	/** Gets an item's documentation (resolving it first), as HTML. */
	private String describe(final CompletionItem item) {
		if (item.getDocumentation() != null) return html(item.getDocumentation());
		try {
			final CompletionItem resolved = session.resolve(item).get(
				DESCRIBE_TIMEOUT, TimeUnit.MILLISECONDS);
			return resolved == null ? null : html(resolved.getDocumentation());
		}
		catch (final Exception exc) {
			return null; // NB: Not in time, or failed.
		}
	}

	private static List<AdditionalEdits.Edit> edits(
		final CompletionItem item, final String text)
	{
		if (item.getAdditionalTextEdits() == null) return Collections.emptyList();
		final List<AdditionalEdits.Edit> out =
			new ArrayList<>();
		for (final TextEdit e : item.getAdditionalTextEdits()) {
			out.add(new AdditionalEdits.Edit(Positions.offset(text, e.getRange()
				.getStart()), Positions.offset(text, e.getRange().getEnd()), e
					.getNewText()));
		}
		return out;
	}

	private static List<CompletionItem> items(final CompletionList list) {
		return list == null || list.getItems() == null ? Collections.emptyList()
			: list.getItems();
	}

	/** The items in the server's order (by sortText, or else label). */
	private static List<CompletionItem> sorted(final CompletionList list) {
		final List<CompletionItem> items = new ArrayList<>(items(list));
		items.sort(Comparator.comparing(i -> i.getSortText() != null ? i
			.getSortText() : i.getLabel()));
		return items;
	}

	private static String newText(final CompletionItem item) {
		if (item.getTextEdit() != null) {
			return item.getTextEdit().isLeft() ? item.getTextEdit().getLeft()
				.getNewText() : item.getTextEdit().getRight().getNewText();
		}
		return item.getInsertText() != null ? item.getInsertText() : item
			.getLabel();
	}

	private static boolean isSnippet(final CompletionItem item) {
		return item.getInsertTextFormat() == InsertTextFormat.Snippet;
	}

	/** A snippet's text without its placeholders' markup. */
	private static String plain(final String snippet) {
		final Matcher m = PLACEHOLDER.matcher(snippet);
		final StringBuffer sb = new StringBuffer();
		while (m.find()) {
			m.appendReplacement(sb, Matcher.quoteReplacement(m.group(2) == null ? ""
				: unescape(m.group(2))));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static String unescape(final String s) {
		return s.replaceAll("\\\\(.)", "$1");
	}

	/**
	 * True iff the help is about calls to the named callable, rather than,
	 * e.g., a call nested in its arguments.
	 */
	private static boolean isFor(final SignatureHelp help, final String name) {
		if (help.getSignatures() == null || help.getSignatures().isEmpty()) {
			return false;
		}
		final String simple = name.substring(name.lastIndexOf('.') + 1);
		for (final SignatureInformation s : help.getSignatures()) {
			final String n = SignatureLabel.of(s).name;
			if (!n.substring(n.lastIndexOf('.') + 1).equals(simple)) return false;
		}
		return true;
	}

	/**
	 * A short parameter list such as {@code long[] pos, int d}, or {@code ()} if
	 * there are no parameters.
	 */
	private static String parameterList(final List<String> params) {
		if (params.isEmpty()) return "()";
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < params.size(); i++) {
			if (i > 0) sb.append(", ");
			sb.append(SignatureLabel.simple(params.get(i)));
		}
		return sb.toString();
	}

	private static String escapeHTML(final String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
