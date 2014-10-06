package net.imagej.ui.swing.script.interpreter;

import java.awt.Font;
import java.io.OutputStream;
import java.io.Writer;

import javax.swing.JTextArea;

import net.imagej.ui.swing.script.JTextAreaOutputStream;
import net.imagej.ui.swing.script.JTextAreaWriter;

import org.scijava.log.LogService;

/**
 * An output area for the scripting user interfaces.
 * 
 * @author Johannes Schindelin
 */
public class OutputPane extends JTextArea {

	private final LogService log;

	public OutputPane(final LogService log) {
		this.log = log;
		Font font = new Font("Courier", Font.PLAIN, 12);
		setFont(font);
		setEditable(false);
		setFocusable(false);
		setLineWrap(true);
	}

	public OutputStream getOutputStream() {
		return new JTextAreaOutputStream(this);
	}

	public Writer getOutputWriter() {
		return new JTextAreaWriter(this, log);
	}
}
