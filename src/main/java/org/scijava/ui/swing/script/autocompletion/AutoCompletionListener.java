package org.scijava.ui.swing.script.autocompletion;

import java.util.List;

import org.fife.ui.autocomplete.Completion;

public interface AutoCompletionListener {

	public List<Completion> completionsFor(final String text);
}
