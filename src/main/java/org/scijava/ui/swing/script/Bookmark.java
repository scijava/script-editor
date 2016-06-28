/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
 * Wisconsin-Madison, Max Planck Institute of Molecular Cell Biology and Genetics,
 * and others.
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

import javax.swing.text.BadLocationException;

import org.fife.ui.rtextarea.GutterIconInfo;

/**
 * A "bookmark" is a stored location (line, file) in the text editor.
 *
 * @author Johannes Schindelin
 * @author Jonathan Hale
 */
public class Bookmark {

	TextEditorTab tab;
	GutterIconInfo info;

	public Bookmark(final TextEditorTab tab, final GutterIconInfo info) {
		this.tab = tab;
		this.info = info;
	}

	public int getLineNumber() {
		try {
			return tab.editorPane.getLineOfOffset(info.getMarkedOffset());
		}
		catch (final BadLocationException e) {
			return -1;
		}
	}

	public void setCaret() {
		tab.requestFocus();
		tab.editorPane.setCaretPosition(info.getMarkedOffset());
	}

	@Override
	public String toString() {
		return "Line " + (getLineNumber() + 1) + " (" +
			tab.editorPane.getFileName() + ")";
	}
}
