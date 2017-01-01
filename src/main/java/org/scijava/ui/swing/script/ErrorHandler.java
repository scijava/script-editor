/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
 * Wisconsin-Madison, Max Planck Institute of Molecular Cell Biology and
 * Genetics, and others.
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

import java.util.ArrayList;
import java.util.List;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Position;

import org.scijava.script.ScriptLanguage;

/**
 * TODO
 *
 * @author Johannes Schindelin
 */
public class ErrorHandler {

	protected List<Error> list = new ArrayList<>();
	protected int current = -1;
	protected JTextArea textArea;
	protected int currentOffset;
	protected Parser parser;

	public ErrorHandler(final JTextArea textArea) {
		this.textArea = textArea;
	}

	public ErrorHandler(final ScriptLanguage language, final JTextArea textArea,
		final int startOffset)
	{
		this(textArea);
		final String languageName =
			language == null ? "None" : language.getLanguageName();
		if (languageName.equals("Java")) parser = new JavacErrorParser();
		else return;

		currentOffset = startOffset;

		try {
			parseErrors();
		}
		catch (final BadLocationException e) {
			handleException(e);
		}
	}

	public int getErrorCount() {
		return list.size();
	}

	public boolean setCurrent(final int index) {
		if (index < 0 || index >= list.size()) return false;
		current = index;
		return true;
	}

	public boolean nextError(final boolean forward) {
		if (forward) {
			if (current + 1 >= list.size()) return false;
			current++;
		}
		else {
			if (current - 1 < 0) return false;
			current--;
		}
		return true;
	}

	public String getPath() {
		return list.get(current).path;
	}

	public int getLine() {
		return list.get(current).line;
	}

	public Position getPosition() {
		return list.get(current).position;
	}

	public void markLine() throws BadLocationException {
		final int offset = getPosition().getOffset();
		final int line = textArea.getLineOfOffset(offset);
		final int start = textArea.getLineStartOffset(line);
		final int end = textArea.getLineEndOffset(line);
		textArea.getHighlighter().removeAllHighlights();
		textArea.getHighlighter().addHighlight(start, end,
			DefaultHighlighter.DefaultPainter);
		scrollToVisible(start);
	}

	public void scrollToVisible(final int offset) {
		if (textArea == null) return;
		final JTextArea textArea = this.textArea;
		final Runnable task = new Runnable() {

			@Override
			public void run() {
				try {
					textArea.scrollRectToVisible(textArea.modelToView(textArea
						.getDocument().getLength()));
					textArea.scrollRectToVisible(textArea.modelToView(offset));
				}
				catch (final BadLocationException e) {
					// ignore
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
		}
		else {
			try {
				SwingUtilities.invokeAndWait(task);
			}
			catch (final Exception e) {
				// ignore
			}
		}
	}

	static class Error {

		String path;
		int line;
		Position position;

		public Error(final String path, final int line) {
			this.path = path;
			this.line = line;
		}
	}

	public void addError(final String path, final int line, String text) {
		try {
			final Document document = textArea.getDocument();
			final int offset = document.getLength();
			if (!text.endsWith("\n")) text += "\n";
			textArea.insert(text, offset);
			if (path == null || line < 0) return;
			final Error error = new Error(path, line);
			error.position = document.createPosition(offset + 1);
			list.add(error);
		}
		catch (final BadLocationException e) {
			handleException(e);
		}
	}

	interface Parser {

		Error getError(String line);
	}

	void parseErrors() throws BadLocationException {
		int line = textArea.getLineOfOffset(currentOffset);
		final int lineCount = textArea.getLineCount();
		for (;;) {
			if (++line >= lineCount) return;
			final int start = textArea.getLineStartOffset(line);
			final int end = textArea.getLineEndOffset(line);
			final String text = textArea.getText(start, end - start);
			final Error error = parser.getError(text);
			if (error != null) try {
				error.position = textArea.getDocument().createPosition(start);
				list.add(error);
			}
			catch (final BadLocationException e) {
				handleException(e);
			}
		}
	}

	class JavacErrorParser implements Parser {

		@Override
		public Error getError(final String line) {
			int colon = line.indexOf(".java:");
			if (colon <= 0) return null;
			colon += 5;
			final int next = line.indexOf(':', colon + 1);
			if (next < colon + 2) return null;
			int lineNumber;
			try {
				lineNumber = Integer.parseInt(line.substring(colon + 1, next));
			}
			catch (final NumberFormatException e) {
				return null;
			}
			final String fileName = line.substring(0, colon);
			return new Error(fileName, lineNumber);
		}
	}

	private void handleException(final Throwable e) {
		TextEditor.handleException(e, textArea);
	}
}
