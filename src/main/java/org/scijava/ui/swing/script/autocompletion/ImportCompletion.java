package org.scijava.ui.swing.script.autocompletion;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;

public class ImportCompletion extends BasicCompletion
{
	protected final String importStatement,
						   className;
	
	public ImportCompletion(final CompletionProvider provider, final String replacementText, final String className, final String importStatement) {
		super(provider, replacementText);
		this.className = className;
		this.importStatement = importStatement;
	}

	@Override
	public String getSummary() {
		return importStatement;
	}
}
