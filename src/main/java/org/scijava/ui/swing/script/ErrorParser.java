/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2022 SciJava developers.
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
import java.awt.Rectangle;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.Parser;
import org.scijava.script.ScriptLanguage;

public class ErrorParser {

	/* Color for ErrorStrip marks + line highlights. May not work for all themes */
	private static final Color COLOR = new Color(255, 99, 71, 128);
	/* 0-base indices of Editor's lines that have errored */
	private TreeSet<Integer> errorLines;
	/* When running selected code errored lines map to Editor through this offset */
	private int lineOffset;
	private boolean enabled;
	private final EditorPane editorPane;
	private JTextAreaWriter writer;
	private int lengthOfJTextAreaWriter;
	private ErrorStripNotifyingParser notifyingParser;

	public ErrorParser(final EditorPane editorPane) {
		this.editorPane = editorPane;
		lineOffset = 0;
	}

	public void gotoPreviousError() {
		if (!isCaretMovable()) {
			try {
				final int caretLine = editorPane.getCaretLineNumber();
				gotoLine(errorLines.lower(caretLine));
			} catch (final NullPointerException ignored) {
				gotoLine(errorLines.last());
			}
		}
	}

	public void gotoNextError() {
		if (isCaretMovable()) {
			try {
				final int caretLine = editorPane.getCaretLineNumber();
				gotoLine(errorLines.higher(caretLine));
			} catch (final NullPointerException ignored) {
				gotoLine(errorLines.first());
			}
		}
	}

	public void reset() {
		if (notifyingParser != null) {
			editorPane.removeParser(notifyingParser);
			if (notifyingParser.highlightAbnoxiously)
				editorPane.removeAllLineHighlights();
		}
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
	}

	public void setLineOffset(final int zeroBasedlineOffset) {
		this.lineOffset = zeroBasedlineOffset;
	}

	public void setSelectedCodeExecution(final boolean selectedExecution) {
		if (selectedExecution) {
			final int p0 = Math.min(editorPane.getCaret().getDot(), editorPane.getCaret().getMark());
			final int p1 = Math.max(editorPane.getCaret().getDot(), editorPane.getCaret().getMark());
			if (p0 != p1) {
				try {
					lineOffset = editorPane.getLineOfOffset(p0);
				} catch (final BadLocationException ignored) {
					lineOffset = -1;
				}
			} else {
				lineOffset = -1;
			}
		} else {
			lineOffset = 0;
		}
	}

	public void setWriter(final JTextAreaWriter writer) {
		this.writer = writer;
		lengthOfJTextAreaWriter = writer.textArea.getText().length();
	}

	public void parse() {
		if (writer == null)
			throw new IllegalArgumentException("Writer is null");
		parse(writer.textArea.getText().substring(lengthOfJTextAreaWriter));
	}

	public void parse(final Throwable t) {
		if (!enabled)
			return;
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		parse(sw.toString());
	}

	private boolean isCaretMovable() {
		if (errorLines == null || errorLines.isEmpty() || !editorPane.isEditable() || !editorPane.isEnabled()) {
			UIManager.getLookAndFeel().provideErrorFeedback(editorPane);
			return false;
		}
		return true;
	}

	private void gotoLine(final int lineNumber) {
		try {
			editorPane.setCaretPosition(editorPane.getLineStartOffset(lineNumber));
			// ensure line is visible. Probably not needed!?
			final Rectangle rect = editorPane.modelToView(editorPane.getCaretPosition());
			editorPane.scrollRectToVisible(rect);
		} catch (final BadLocationException ignored) {
			// do nothing
		} finally {
			editorPane.requestFocusInWindow();
		}
	}

	private void parse(final String errorLog) {
		// Do nothing if disabled, or if only selected text was evaluated in the
		// script but we don't know where in the document such selection occurred
		if (!enabled) {
			abort("When Auto-imports are active errors are not tractable in the Editor");
			return;
		}
		if (lineOffset == -1) {
			abort("Code selection unknown: Erros are not tractable in the Editor");
			return;
		}
		final ScriptLanguage lang = editorPane.getCurrentLanguage();
		if (lang == null)
			return;
		final boolean isJava = lang.getLanguageName().equals("Java");
		final String fileName = editorPane.getFileName();
		if (isJava && fileName == null)
			return;
		errorLines = new TreeSet<>();
		final StringTokenizer tokenizer = new StringTokenizer(errorLog, "\n");
		if (isJava) {
			while (tokenizer.hasMoreTokens()) {
				parseJava(fileName, tokenizer.nextToken(), errorLines);
			}
		} else {
			while (tokenizer.hasMoreTokens()) {
				parseNonJava(tokenizer.nextToken(), errorLines);
			}
		}
		if (!errorLines.isEmpty()) {
			notifyingParser = new ErrorStripNotifyingParser();
			editorPane.addParser(notifyingParser);
			editorPane.forceReparsing(notifyingParser);
			gotoLine(errorLines.first());
		}
	}

	private void parseNonJava(final String lineText, final Collection<Integer> errorLines) {
		// Rationale: line errors of interpretative scripts will mention both the
		// extension
		// of the script and the term "line "
		final int tokenIndex = lineText.indexOf("line ");
		if (tokenIndex < 0) {
			return;
		}
//		System.out.println("Parsing candidate: " + lineText);
		for (final String extension : editorPane.getCurrentLanguage().getExtensions()) {
			final int dotIndex = lineText.indexOf("." + extension);
			if (dotIndex < 0)
				continue;
			final Pattern pattern = Pattern.compile("\\d+");
//			System.out.println("section being matched: " + lineText.substring(tokenIndex));
			final Matcher matcher = pattern.matcher(lineText.substring(tokenIndex));
			if (matcher.find()) {
				try {
					final int lineNumber = Integer.valueOf(matcher.group());
					if (lineNumber > 0)
						errorLines.add(lineNumber - 1 + lineOffset); // store 0-based indices
//					System.out.println("line No (zero-based): " + (lineNumber - 1));
				} catch (final NumberFormatException e) {
					// ignore
				}
			}
		}
	}

	private void parseJava(final String filename, final String line, final Collection<Integer> errorLines) {
		int colon = line.indexOf(filename);
		if (colon <= 0)
			return;
//		System.out.println("Parsing candidate: " + line);
		colon += filename.length();
		final int next = line.indexOf(':', colon + 1);
		if (next < colon + 2)
			return;
		try {
			final int lineNumber = Integer.parseInt(line.substring(colon + 1, next));
			if (lineNumber > 0)
				errorLines.add(lineNumber - 1 + lineOffset); // store 0-based indices
//			System.out.println("line Np: " + (lineNumber - 1));
		} catch (final NumberFormatException e) {
			// ignore
		}
	}

	private void abort(final String msg) {
		if (writer != null)
			writer.textArea.insert("[WARNING] " + msg + "\n", lengthOfJTextAreaWriter);
		errorLines = null;
	}

	/**
	 * This is just so that we can register errorLines in the Editor's
	 * {@link org.fife.ui.rsyntaxtextarea.ErrorStrip}
	 */
	class ErrorStripNotifyingParser extends AbstractParser {

		private final DefaultParseResult result;
		private final boolean highlightAbnoxiously = true;

		public ErrorStripNotifyingParser() {
			result = new DefaultParseResult(this);
		}

		@Override
		public boolean isEnabled() {
			return enabled && errorLines != null;
		}

		@Override
		public ParseResult parse(final RSyntaxDocument doc, final String style) {
			final Element root = doc.getDefaultRootElement();
			final int lineCount = root.getElementCount();
			result.clearNotices();
			result.setParsedLines(0, lineCount - 1);
			if (isEnabled() && !SyntaxConstants.SYNTAX_STYLE_NONE.equals(style)) {
				errorLines.forEach(line -> {
					result.addNotice(new ErrorNotice(this, line));
					if (highlightAbnoxiously) {
						try {
							editorPane.addLineHighlight(line, COLOR);
						} catch (final BadLocationException ignored) {
							// do nothing
						}
					}
				});
			}
			return result;

		}

	}

	class ErrorNotice extends DefaultParserNotice {
		public ErrorNotice(final Parser parser, final int line) {
			super(parser, "Run Error: Line " + (line + 1), line);
			setColor(COLOR);
			setLevel(Level.ERROR);
			setShowInEditor(true);
		}

	}

}
