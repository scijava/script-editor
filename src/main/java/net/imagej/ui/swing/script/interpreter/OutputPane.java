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
	private OutputStream out;
	private Writer writer;

	public OutputPane(final LogService log) {
		this.log = log;
		Font font = new Font("Courier", Font.PLAIN, 12);
		setFont(font);
		setEditable(false);
		setFocusable(true);
		setLineWrap(true);
	}

	public synchronized OutputStream getOutputStream() {
		if (out == null) out = new JTextAreaOutputStream(this);
		return out;
	}

	public synchronized Writer getOutputWriter() {
		if (writer == null) writer = new JTextAreaWriter(this, log);
		return writer;
	}

	public void close() {
		try {
			if (out != null) {
				out.close();
				out = null;
			}
			if (writer != null) {
				writer.close();
				writer = null;
			}
		} catch (final Exception e) {
			log.error(e);
		}
	}
}
