/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2015 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
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
package net.imagej.ui.swing.script.interpreter;

import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_UP;

import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.PrintStream;

import javax.script.ScriptException;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

import org.scijava.script.ScriptInterpreter;

/**
 * The prompt for the script interpreter.
 * 
 * @author Johannes Schindelin
 */
public class PromptPane extends JTextArea {

	private final ScriptInterpreter interpreter;
	private final OutputPane output;

	public PromptPane(final ScriptInterpreter interpreter,
		final OutputPane output)
	{
		super(3, 2);
		setLineWrap(true);
		this.interpreter = interpreter;
		this.output = output;
		addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(final KeyEvent event) {
				final int code = event.getKeyCode();
				switch (code) {
				case VK_ENTER:
					if (event.isShiftDown()) {
						// multi-line input
						insert("\n", getCaretPosition());
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
				}
			}

		});
	}

	private boolean isInRow(final int row) {
		try {
			final int rowHeight = getRowHeight();
			final Rectangle rect = modelToView(getCaretPosition());
			int rowTop = row * rowHeight;
			if (rowTop < 0) {
				final Rectangle lastRect = modelToView(getDocument().getLength());
				rowTop += lastRect.y + lastRect.height;
			}
			return rect.y == rowTop;
		}
		catch (BadLocationException e) {
			e.printStackTrace(new PrintStream(output.getOutputStream()));
			return true;
		}
	}

	private void up() {
		setText(interpreter.walkHistory(getText(), false));
	}

	private void down() {
		setText(interpreter.walkHistory(getText(), true));
	}

	private synchronized void execute() {
		final String text = getText();
		output.append(">>> " + text + "\n");
		try {
			interpreter.eval(text);
		}
		catch (ScriptException e) {
			e.printStackTrace(new PrintStream(output.getOutputStream()));
		}
		finally {
			setText("");
		}
	}

}
