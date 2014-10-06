package net.imagej.ui.swing.script.interpreter;

import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_UP;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.PrintStream;

import javax.script.ScriptException;
import javax.swing.JTextArea;

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
					down();
					break;
				case VK_UP:
					up();
					break;
				}
			}

		});
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
