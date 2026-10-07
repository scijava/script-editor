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

package org.scijava.ui.swing.script;

import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_TAB;
import static java.awt.event.KeyEvent.VK_UP;

import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.scijava.Context;
import org.scijava.script.ScriptInterpreter;
import org.scijava.script.ScriptLanguage;
import org.scijava.script.ScriptREPL;
import org.scijava.code.lsp.BindingsLanguageServer;
import org.scijava.code.lsp.LanguageServerService;
import org.scijava.code.lsp.Positions;
import org.scijava.code.lsp.UpdatingCompletionList;
import org.scijava.thread.ThreadService;
import org.scijava.util.ClassUtils;
import org.scijava.util.Types;
import org.scijava.widget.UIComponent;

/**
 * The prompt for the script REPL.
 *
 * @author Johannes Schindelin
 * @author Curtis Rueden
 */
public abstract class PromptPane implements UIComponent<JTextArea> {

	/** The prompt's text, as the servers know it. */
	private static final String PROMPT_URI = "untitled:/prompt";

	/** How long Tab waits for completions, in milliseconds. */
	private static final long COMPLETION_WAIT = 2000;


	private final ScriptREPL repl;
	private final VarsPane vars;
	private final TextArea textArea;
	private final OutputPane output;

	private boolean executing;

	public PromptPane(final ScriptREPL repl, final VarsPane vars,
		final OutputPane output)
	{
		textArea = new TextArea(3, 2);
		textArea.setLineWrap(true);
		this.repl = repl;
		this.vars = vars;
		this.output = output;
		textArea.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(final KeyEvent event) {
				final int code = event.getKeyCode();
				switch (code) {
					case VK_ENTER:
						if (executing) {
							// ignore enter key while executing
							event.consume();
							return;
						}
						if (event.isShiftDown()) {
							// multi-line input
							textArea.insert("\n", textArea.getCaretPosition());
						}
						else {
							execute();
							event.consume();
						}
						break;
					case VK_DOWN:
						if (isInRow(-1)) {
							down();
							event.consume();
						}
						break;
					case VK_UP:
						if (isInRow(0)) {
							up();
							event.consume();
						}
						break;
					case VK_TAB:
						if (!executing) {
							complete();
							event.consume();
						}
						break;
				}
			}

		});
	}

	// -- PromptPane methods --

	/** A callback method which is invoked when the REPL quits. */
	public abstract void quit();

	// -- UIComponent methods --

	@Override
	public JTextArea getComponent() {
		return textArea;
	}

	@Override
	public Class<JTextArea> getComponentType() {
		return JTextArea.class;
	}

	// -- Helper methods --

	private boolean isInRow(final int row) {
		try {
			final int rowHeight = textArea.getRowHeight();
			final Rectangle rect = textArea.modelToView(textArea.getCaretPosition());
			int rowTop = row * rowHeight;
			if (rowTop < 0) {
				final Rectangle lastRect = textArea.modelToView(
					textArea.getDocument().getLength());
				rowTop += lastRect.y + lastRect.height;
			}
			return rect.y == rowTop;
		}
		catch (final BadLocationException e) {
			e.printStackTrace(new PrintStream(output.getOutputStream()));
			return true;
		}
	}

	private void up() {
		walk(false);
	}

	private void down() {
		walk(true);
	}

	private void walk(boolean forward) {
		textArea.setText(repl.getInterpreter().walkHistory(textArea.getText(),
			forward));
	}

	private void execute() {
		final String text = textArea.getText();

		output.append(">>> " + text + "\n");
		textArea.setText("");
		executing = true;

		threadService().run(() -> {
			final boolean result = repl.evaluate(text);
			threadService().queue(() -> {
				executing = false;
				if (!result) quit();
				vars.update();
			});
		});
	}

	private ThreadService threadService() {
		return context().service(ThreadService.class);
	}

	private Context context() {
		// HACK: Get the SciJava context from the REPL.
		// This can be fixed if/when the REPL offers a getter for it.
		return (Context) ClassUtils.getValue(//
			Types.field(repl.getClass(), "context"), repl);
	}

	/**
	 * Tab-completion for the REPL prompt: what the language's servers know (see
	 * {@link LanguageServerService}), with the interpreter's live variables; or,
	 * for languages without servers, its live variables alone (see
	 * {@link BindingsLanguageServer}). So every scripting language gets at
	 * least baseline completion here.
	 */
	private void complete() {
		final ScriptInterpreter interpreter = repl.getInterpreter();
		if (interpreter == null) return;
		final ScriptEngine engine = interpreter.getEngine();
		final ScriptLanguage language = interpreter.getLanguage();
		final ScriptContext live = engine == null ? null : engine.getContext();
		final int caret = textArea.getCaretPosition();
		final String text = textArea.getText();

		final List<CompletionItem> items;
		try {
			items = items(language, live, text, caret);
		}
		catch (final Exception exc) {
			return;
		}
		if (items.isEmpty()) {
			textArea.getToolkit().beep();
			return;
		}
		final int start = Math.min(Math.max(start(items.get(0), text, caret), 0),
			caret);

		if (items.size() == 1) {
			replaceRange(start, caret, insertion(items.get(0)));
			return;
		}

		// Multiple candidates: insert their longest common prefix (if it extends
		// what is already typed), and list the options in the output pane.
		final String lcp = longestCommonPrefix(items);
		final String current = text.substring(start, caret);
		if (lcp.length() > current.length()) {
			replaceRange(start, caret, lcp);
		}
		else {
			final StringBuilder sb = new StringBuilder();
			for (final CompletionItem item : items) {
				if (sb.length() > 0) sb.append("    ");
				sb.append(item.getLabel());
				if (item.getLabelDetails() != null && item.getLabelDetails()
					.getDetail() != null) sb.append(item.getLabelDetails().getDetail());
			}
			output.append(sb.append("\n").toString());
		}
	}

	/** Asks the language's servers (or the live variables) for completions. */
	private List<CompletionItem> items(final ScriptLanguage language,
		final ScriptContext live, final String text, final int caret)
		throws Exception
	{
		final LanguageServerService servers = context().service(
			LanguageServerService.class);
		final LanguageServer server;
		if (servers != null && language != null && servers.supports(language)) {
			server = servers.server(language, live);
		}
		else if (live != null) server = new BindingsLanguageServer(live);
		else return Collections.emptyList();

		final TextDocumentService docs = server.getTextDocumentService();
		docs.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(PROMPT_URI,
			language == null ? "" : language.getLanguageName(), 1, text)));
		try {
			CompletionList list = docs.completion(new CompletionParams(
				new TextDocumentIdentifier(PROMPT_URI), Positions.position(text,
					caret))).get(COMPLETION_WAIT, TimeUnit.MILLISECONDS).getRight();
			// NB: Tab asks once: wait a moment for slower servers.
			if (list instanceof UpdatingCompletionList) {
				try {
					final CompletionList better = ((UpdatingCompletionList) list)
						.update().get(COMPLETION_WAIT, TimeUnit.MILLISECONDS);
					if (better != null) list = better;
				}
				catch (final Exception exc) {
					// NB: Not in time: what there is.
				}
			}
			final List<CompletionItem> items = new ArrayList<>(list.getItems());
			items.sort(Comparator.comparing(item -> item.getSortText() != null ? item
				.getSortText() : item.getLabel()));
			return items;
		}
		finally {
			docs.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(
				PROMPT_URI)));
		}
	}

	/** Where a completion's text starts: what it replaces, up to the caret. */
	private static int start(final CompletionItem item, final String text,
		final int caret)
	{
		if (item.getTextEdit() == null) return caret;
		final Range range = item.getTextEdit().isLeft() ? item.getTextEdit()
			.getLeft().getRange() : item.getTextEdit().getRight().getInsert();
		return Positions.offset(text, range.getStart());
	}

	/**
	 * The text a completion inserts; for a callable (a snippet), up to its
	 * first parameter, e.g. {@code max(}.
	 */
	private static String insertion(final CompletionItem item) {
		String text = item.getTextEdit() == null ? null : item.getTextEdit()
			.isLeft() ? item.getTextEdit().getLeft().getNewText() : item
				.getTextEdit().getRight().getNewText();
		if (text == null) text = item.getInsertText();
		if (text == null) text = item.getLabel();
		if (item.getInsertTextFormat() == InsertTextFormat.Snippet) {
			final int stop = text.indexOf('$');
			if (stop >= 0) text = text.substring(0, stop);
			text = text.replace("\\$", "$").replace("\\}", "}").replace("\\\\",
				"\\");
		}
		return text;
	}

	private void replaceRange(final int start, final int end,
		final String replacement)
	{
		try {
			textArea.getDocument().remove(start, end - start);
			textArea.getDocument().insertString(start, replacement, null);
		}
		catch (final BadLocationException exc) {
			// ignore: the prompt changed underneath us
		}
	}

	private static String longestCommonPrefix(final List<CompletionItem> items) {
		String prefix = insertion(items.get(0));
		for (final CompletionItem item : items) {
			final String s = insertion(item);
			int i = 0;
			final int max = Math.min(prefix.length(), s.length());
			while (i < max && prefix.charAt(i) == s.charAt(i)) i++;
			prefix = prefix.substring(0, i);
			if (prefix.isEmpty()) break;
		}
		return prefix;
	}

	// -- Helper classes --

	/**
	 * Trivial extension of {@link JTextArea} to expose its {@code getRowHeight()}
	 * method.
	 */
	public class TextArea extends JTextArea {
		public TextArea(int rows, int columns) {
			super(rows, columns);
		}

		@Override
		public int getRowHeight() {
			return super.getRowHeight();
		}
	}
}
