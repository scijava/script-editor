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

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Rectangle;
import java.awt.Window;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptException;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.Parser;
import org.scijava.script.ScriptLanguage;

/**
 * Detects the lines of the editor that caused a script error and flags them
 * (line highlight and error strip). Lines are taken from the exception when
 * possible (see {@link #parse(Throwable)}), and otherwise scraped from the
 * error console (see {@link #parse()}).
 */
public class ErrorParser {

	/* Color for ErrorStrip marks and fallback taint for line highlights */
	private static final Color COLOR = Color.RED;
	private static final int UNKNOWN = -1;
	/* Stack frames of the runtimes themselves, never script lines */
	private static final Pattern IGNORED = Pattern.compile(
		":classloader:|\\.org\\.python\\.|\\.codehaus\\.groovy\\.|\\.tools\\.nsc\\.|at bsh\\.|\\$Recompilation\\$");
	/* "line 51", "Line: 51", "line number 51" */
	private static final Pattern LINE_WORD = Pattern.compile(
		"\\bline(?:\\s+number)?\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

	/**
	 * An errored line: its position tracks edits in the document, and its text
	 * at the time of the run tells whether the error is still about that line
	 */
	private static class FlaggedLine {

		private final Position position;
		private final String text;
		private final Object highlight;

		FlaggedLine(final Position position, final String text, final Object highlight) {
			this.position = position;
			this.text = text;
			this.highlight = highlight;
		}

		Position position() {
			return position;
		}

		String text() {
			return text;
		}

		Object highlight() {
			return highlight;
		}
	}

	private final List<FlaggedLine> errors = new CopyOnWriteArrayList<>();
	private final AtomicBoolean prunePending = new AtomicBoolean();
	private final DocumentListener editListener = new DocumentListener() {

		@Override
		public void insertUpdate(final DocumentEvent e) {
			schedulePrune();
		}

		@Override
		public void removeUpdate(final DocumentEvent e) {
			schedulePrune();
		}

		@Override
		public void changedUpdate(final DocumentEvent e) {
			// attributes only
		}
	};
	private Document listenedDocument;
	private final ErrorStripNotifyingParser notifyingParser = new ErrorStripNotifyingParser();
	private final EditorPane editorPane;
	private boolean parserRegistered;
	/* 0-base editor line of the executed selection (0: whole script) */
	private volatile int lineOffset;
	/* Prompt commands do not map to editor lines */
	private volatile boolean promptRun;
	private volatile boolean enabled;
	private JTextAreaWriter writer;
	private int lengthOfJTextAreaWriter;
	private volatile boolean parsingSucceeded;
	/* Whether the console was told why errors are not flagged in this run */
	private boolean noticeShown;

	public ErrorParser(final EditorPane editorPane) {
		this.editorPane = editorPane;
	}

	public void gotoPreviousError() {
		gotoError(false);
	}

	public void gotoNextError() {
		gotoError(true);
	}

	private void gotoError(final boolean forward) {
		final TreeSet<Integer> lines = currentLines();
		if (lines.isEmpty()) {
			UIManager.getLookAndFeel().provideErrorFeedback(editorPane);
			return;
		}
		final int caretLine = editorPane.getCaretLineNumber();
		Integer target = forward ? lines.higher(caretLine) : lines.lower(caretLine);
		if (target == null) target = forward ? lines.first() : lines.last(); // wrap around
		gotoLine(target);
	}

	/** Removes the flagged lines of the previous run */
	public void reset() {
		final List<FlaggedLine> flagged = List.copyOf(errors);
		errors.clear();
		parsingSucceeded = false;
		noticeShown = false;
		runOnEdt(() -> {
			if (parserRegistered) {
				editorPane.removeParser(notifyingParser);
				parserRegistered = false;
			}
			flagged.forEach(e -> editorPane.removeLineHighlight(e.highlight()));
		});
	}

	private void schedulePrune() {
		if (!errors.isEmpty() && prunePending.compareAndSet(false, true)) {
			SwingUtilities.invokeLater(() -> {
				prunePending.set(false);
				prune();
			});
		}
	}

	/** Drops the errors whose line is no longer the one that failed. On the EDT */
	private void prune() {
		boolean dropped = false;
		for (final FlaggedLine e : errors) {
			if (Objects.equals(lineText(lineOf(e.position())), e.text())) continue;
			errors.remove(e);
			editorPane.removeLineHighlight(e.highlight());
			dropped = true;
		}
		if (!dropped) return;
		if (errors.isEmpty() && parserRegistered) {
			editorPane.removeParser(notifyingParser);
			parserRegistered = false;
		} else {
			editorPane.forceReparsing(notifyingParser);
		}
	}

	private int lineOf(final Position p) {
		try {
			return editorPane.getLineOfOffset(p.getOffset());
		} catch (final BadLocationException ignored) {
			return -1;
		}
	}

	private String lineText(final int line) {
		try {
			final int start = editorPane.getLineStartOffset(line);
			return editorPane.getText(start, editorPane.getLineEndOffset(line) - start).stripTrailing();
		} catch (final BadLocationException ignored) {
			return null;
		}
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
	}

	public void setLineOffset(final int zeroBasedlineOffset) {
		this.lineOffset = zeroBasedlineOffset;
	}

	/**
	 * Sets whether the next run executes the current selection, in which case
	 * errored lines map to the editor through the line at which it starts.
	 * Must be called for every run, since the offset is kept until changed.
	 */
	public void setSelectedCodeExecution(final boolean selectedExecution) {
		promptRun = false;
		lineOffset = 0;
		if (!selectedExecution) return;
		final int p0 = Math.min(editorPane.getCaret().getDot(), editorPane.getCaret().getMark());
		final int p1 = Math.max(editorPane.getCaret().getDot(), editorPane.getCaret().getMark());
		lineOffset = UNKNOWN;
		if (p0 != p1) {
			try {
				lineOffset = editorPane.getLineOfOffset(p0);
			} catch (final BadLocationException ignored) {
				// unknown
			}
		}
	}

	/** Declares that the next run is a prompt command, with no lines in the editor */
	public void setPromptExecution() {
		promptRun = true;
	}

	public void setWriter(final JTextAreaWriter writer) {
		this.writer = writer;
		lengthOfJTextAreaWriter = writer.textArea.getText().length();
		noticeShown = false;
	}

	/** Flags the lines reported in the error console since {@link #setWriter} */
	public void parse() {
		if (writer == null)
			throw new IllegalArgumentException("Writer is null");
		final String text = writer.textArea.getText();
		// The console may have been cleared since the run started
		final int start = (text.length() < lengthOfJTextAreaWriter) ? 0 : lengthOfJTextAreaWriter;
		parse(text.substring(start), null);
	}

	/**
	 * Flags the lines reported by an exception: the line numbers of script
	 * exceptions and the stack frames of the script. If it holds none, its stack
	 * trace is scraped like the console.
	 */
	public void parse(final Throwable t) {
		if (!enabled)
			return;
		final StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		parse(sw.toString(), t);
	}

	private boolean isImageJMacro() {
		final ScriptLanguage lang = editorPane.getCurrentLanguage();
		if (lang == null) return false;
		final String name = lang.getLanguageName();
		return name.equals("ImageJ Macro") || name.equals("IJ1 Macro");
	}

	/** Whether lines were found in the log, i.e., it has details of the error */
	public boolean isLogDetailed() {
		if (!isImageJMacro()) return parsingSucceeded;
		for (final Window win : Window.getWindows()) {
			if (win != null && win instanceof Dialog && "Macro Error".equals(((Dialog)win).getTitle())) {
				return true; // hopefully there is something in the console
			}
		}
		return parsingSucceeded;
	}

	/** The 0-based editor lines currently flagged, following any edits */
	private TreeSet<Integer> currentLines() {
		final TreeSet<Integer> lines = new TreeSet<>();
		for (final FlaggedLine e : errors) {
			final int line = lineOf(e.position());
			if (line >= 0) lines.add(line);
		}
		return lines;
	}

	private void gotoLine(final int lineNumber) {
		try {
			editorPane.setCaretPosition(editorPane.getLineStartOffset(lineNumber));
			// ensure line is visible. Probably not needed!?
			final Rectangle rect = editorPane.modelToView2D(editorPane.getCaretPosition()).getBounds();
			editorPane.scrollRectToVisible(rect);
		} catch (final BadLocationException ignored) {
			// do nothing
		} finally {
			editorPane.requestFocusInWindow();
		}
	}

	private static void runOnEdt(final Runnable r) {
		if (SwingUtilities.isEventDispatchThread()) r.run();
		else SwingUtilities.invokeLater(r);
	}

	private void parse(final String errorLog, final Throwable t) {

		final ScriptLanguage lang = editorPane.getCurrentLanguage();
		if (lang == null || promptRun) {
			abort();
			return;
		}
		if (isImageJMacro()) {
			abort("Execution errors handled by the Macro Interpreter. Use the Interpreter's Debug option for error tracking", false);
			return;
		}
		// Reported lines of these runtimes do not map reliably to the editor
		final String name = lang.getLanguageName();
		if ("Scala".equals(name) || "R".equals(name)) {
			abort("Execution errors are not highlighted for " + name, false);
			return;
		}
		// Do nothing if disabled, or if only selected text was evaluated in the
		// script but we don't know where in the document such selection occurred
		if (!enabled) {
			abort("Execution errors are not highlighted when auto-imports are active", true);
			return;
		}
		if (lineOffset == UNKNOWN) {
			abort("Code selection unknown: Errors are not highlighted in the Editor", true);
			return;
		}

		final String fileName = editorPane.getFileName();
		final TreeSet<Integer> reported = new TreeSet<>(); // 1-based, as reported
		if (t != null) fromThrowable(t, fileName, reported);
		if (reported.isEmpty()) {
			reported.addAll(scrape(errorLog, fileName, lang.getExtensions(),
				"Java".equals(lang.getLanguageName())));
		}
		if (reported.isEmpty()) return;
		parsingSucceeded = true;

		final int shift = lineOffset;
		runOnEdt(() -> flag(reported, shift));
	}

	/** Flags the (1-based) reported lines, shifted to editor lines. On the EDT */
	private void flag(final Collection<Integer> reported, final int shift) {
		final TreeSet<Integer> flagged = currentLines();
		final Color c = highlightColor();
		for (final int number : reported) {
			final int line = number - 1 + shift;
			if (line < 0 || line >= editorPane.getLineCount() || !flagged.add(line)) continue;
			try {
				errors.add(new FlaggedLine(editorPane.getDocument().createPosition(editorPane.getLineStartOffset(line)),
					lineText(line), editorPane.addLineHighlight(line, c)));
			} catch (final BadLocationException ignored) {
				// do nothing
			}
		}
		if (errors.isEmpty()) return;
		if (listenedDocument != editorPane.getDocument()) {
			listenedDocument = editorPane.getDocument();
			listenedDocument.addDocumentListener(editListener);
		}
		if (!parserRegistered) {
			editorPane.addParser(notifyingParser);
			parserRegistered = true;
		}
		editorPane.forceReparsing(notifyingParser);
		final TreeSet<Integer> now = currentLines();
		if (!now.isEmpty()) gotoLine(now.first());
	}

	/** Collects the 1-based lines of the script in an exception and its causes */
	static void fromThrowable(final Throwable t, final String fileName, final Collection<Integer> lines) {
		int depth = 0;
		for (Throwable c = t; c != null && depth++ < 20; c = c.getCause()) {
			if (c instanceof ScriptException) {
				final ScriptException se = (ScriptException) c;
				if (se.getLineNumber() > 0 && sameFile(se.getFileName(), fileName, true))
					lines.add(se.getLineNumber());
			}
			for (final StackTraceElement e : c.getStackTrace()) {
				if (e.getLineNumber() > 0 && sameFile(e.getFileName(), fileName, false))
					lines.add(e.getLineNumber());
			}
		}
	}

	private static boolean sameFile(final String reported, final String fileName, final boolean unknownIsSame) {
		if (reported == null || reported.isEmpty()) return unknownIsSame;
		final String base = reported.substring(Math.max(reported.lastIndexOf('/'), reported.lastIndexOf('\\')) + 1);
		return base.equalsIgnoreCase(fileName);
	}

	/**
	 * Scrapes the 1-based lines of the script from a log: frames such as
	 * {@code Script.groovy:51} or {@code Script.java:51:}, and statements such as
	 * {@code line 51}.
	 *
	 * @param extensions the extensions of the script language
	 * @param fileName the name of the script, used for Java sources
	 */
	static TreeSet<Integer> scrape(final String log, final String fileName, final Collection<String> extensions,
		final boolean isJava)
	{
		final Pattern frame;
		if (isJava) {
			// Compiler messages (Foo.java:12: error) and runtime frames (Foo.java:12)
			frame = Pattern.compile("(?<![\\w.])" + Pattern.quote(fileName) + ":(\\d+)[:)]");
		} else if (extensions.isEmpty()) {
			frame = null;
		} else {
			final StringBuilder sb = new StringBuilder("\\.(?:");
			for (final String ext : extensions)
				sb.append(Pattern.quote(ext)).append('|');
			sb.setLength(sb.length() - 1);
			frame = Pattern.compile(sb + "):(\\d+)", Pattern.CASE_INSENSITIVE);
		}
		// Frames of other files (e.g., imported scripts) are only used if none
		// mentions this script. Names in frames may differ from the editor's
		final TreeSet<Integer> own = new TreeSet<>(), other = new TreeSet<>(), words = new TreeSet<>();
		for (final String text : log.split("\\R")) {
			if (IGNORED.matcher(text).find()) continue;
			Matcher m = (frame == null) ? null : frame.matcher(text);
			TreeSet<Integer> target;
			if (m != null && m.find()) {
				target = text.contains(fileName) ? own : other;
			} else if (!isJava && (m = LINE_WORD.matcher(text)).find()) {
				target = words;
			} else {
				continue;
			}
			try {
				final int number = Integer.parseInt(m.group(1));
				if (number > 0) target.add(number);
			} catch (final NumberFormatException ignored) {
				// too large to be a line
			}
		}
		final TreeSet<Integer> lines = new TreeSet<>(own.isEmpty() ? other : own);
		lines.addAll(words);
		return lines;
	}

	private void abort() {
		parsingSucceeded = false;
	}

	private void abort(final String msg, final boolean offsetNotice) {
		abort();
		// A run is parsed from its exception and from its console: tell once
		if (writer != null && !noticeShown) {
			noticeShown = true;
			String finalMsg = "[INFO] " + msg + "\n";
			if (offsetNotice)
				finalMsg += "[INFO] Reported error line(s) may not match line numbers in the editor\n";
			final int at = Math.min(lengthOfJTextAreaWriter, writer.textArea.getDocument().getLength());
			writer.textArea.insert(finalMsg, at);
		}
	}

	/**
	 * This is just so that we can register the errors in the Editor's
	 * {@link org.fife.ui.rsyntaxtextarea.ErrorStrip}
	 */
	class ErrorStripNotifyingParser extends AbstractParser {

		private final DefaultParseResult result;

		public ErrorStripNotifyingParser() {
			result = new DefaultParseResult(this);
		}

		@Override
		public boolean isEnabled() {
			return enabled && !errors.isEmpty();
		}

		@Override
		public ParseResult parse(final RSyntaxDocument doc, final String style) {
			final Element root = doc.getDefaultRootElement();
			final int lineCount = root.getElementCount();
			result.clearNotices();
			result.setParsedLines(0, lineCount - 1);
			if (isEnabled() && !SyntaxConstants.SYNTAX_STYLE_NONE.equals(style)) {
				for (final FlaggedLine e : errors) {
					// The document may be edited while parsing
					final int index = root.getElementIndex(e.position().getOffset());
					final Element line = root.getElement(index);
					if (line != null) result.addNotice(new ErrorNotice(this, index, line));
				}
			}
			return result;
		}

	}

	private Color highlightColor() {
		// https://stackoverflow.com/a/29576746
		final Color c1 = editorPane.getCurrentLineHighlightColor();
		final Color c2 = (editorPane.getBackground() == null) ? COLOR : editorPane.getBackground();
		return averageColors(c1, c2);
	}

	protected static Color averageColors(final Color c1, final Color c2) {
		// https://stackoverflow.com/a/29576746
		final int r = (int) Math.sqrt( (Math.pow(c1.getRed(), 2) + Math.pow(c2.getRed(), 2)) / 2);
		final int g = (int) Math.sqrt( (Math.pow(c1.getGreen(), 2) + Math.pow(c2.getGreen(), 2)) / 2);
		final int b = (int) Math.sqrt( (Math.pow(c1.getBlue(), 2) + Math.pow(c2.getBlue(), 2)) / 2);
		return new Color(r, g, b, c1.getAlpha());
	}

	class ErrorNotice extends DefaultParserNotice {
		/* Notice lines are 1-based. The offset and length make the error strip
		 * mark, the editor underline and the click action agree on the line */
		public ErrorNotice(final Parser parser, final int line, final Element lineElement) {
			super(parser, "Run Error: Line " + (line + 1), line + 1, lineElement.getStartOffset(),
				Math.max(1, lineElement.getEndOffset() - 1 - lineElement.getStartOffset()));
			setColor(COLOR);
			setLevel(Level.ERROR);
			setShowInEditor(true);
		}

	}
}
