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
