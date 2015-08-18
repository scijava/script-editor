
package net.imagej.ui.swing.script;

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
		return "Line " + (getLineNumber() + 1) + " (" + tab.editorPane.getFileName() + ")";
	}
}
