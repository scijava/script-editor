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
