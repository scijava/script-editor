package org.scijava.ui.swing.script.autocompletion;

import java.util.List;

import org.fife.ui.autocomplete.Completion;

public interface AutoCompletionListener {

	/**
	 * 
	 * @param text The whole line up to the caret where autocompletion was invoked.
	 * @return
	 */
	public List<Completion> completionsFor(final String text);
}
