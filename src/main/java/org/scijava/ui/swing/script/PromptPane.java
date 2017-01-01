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

import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_UP;

import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.PrintStream;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

import org.scijava.script.ScriptREPL;
import org.scijava.widget.UIComponent;

/**
 * The prompt for the script REPL.
 *
 * @author Johannes Schindelin
 * @author Curtis Rueden
 */
public abstract class PromptPane implements UIComponent<JTextArea> {

	private final ScriptREPL repl;
	private final TextArea textArea;
	private final OutputPane output;

	public PromptPane(final ScriptREPL repl, final VarsPane vars,
		final OutputPane output)
	{
		textArea = new TextArea(3, 2);
		textArea.setLineWrap(true);
		this.repl = repl;
		this.output = output;
		textArea.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(final KeyEvent event) {
				final int code = event.getKeyCode();
				switch (code) {
					case VK_ENTER:
						if (event.isShiftDown()) {
							// multi-line input
							textArea.insert("\n", textArea.getCaretPosition());
						}
						else {
							final boolean result = execute();
							event.consume();
							if (!result) quit();
							vars.update();
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

	private synchronized boolean execute() {
		final String text = textArea.getText();
		output.append(">>> " + text + "\n");
		final boolean result = repl.evaluate(text);
		textArea.setText("");
		return result;
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
