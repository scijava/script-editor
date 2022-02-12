package org.scijava.ui.swing.script.autocompletion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.fife.ui.autocomplete.AbstractCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;

public class CompletionText {

	private String replacementText;
	private String description;
	private String summary;

	public CompletionText(final String replacementText) {
		this(replacementText, (String)null, (String)null);
	}

	public CompletionText(final String replacementText, final String summary, final String description) {
		this.replacementText = replacementText;
		this.summary = summary;
		this.description = description;
	}

	public CompletionText(final String replacementText, final Class<?> c, final Field f) {
		this(replacementText, ClassUtil.getSummaryCompletion(f, c), null);
	}

	public CompletionText(final String replacementText, final Class<?> c, final Method m) {
		this(replacementText, ClassUtil.getSummaryCompletion(m, c), null);
	}

	public String getReplacementText() {
		return replacementText;
	}

	public String getDescription() {
		return description;
	}

	public String getSummary() {
		return summary;
	}

	public AbstractCompletion getCompletion(final CompletionProvider provider, final String replacementText) {
		return new BasicCompletion(provider, replacementText, description, summary);
	}

	public void setReplacementText(final String replacementText) {
		this.replacementText = replacementText;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public void setSummary(final String summary) {
		this.summary = summary;
	}

	@Override
	public String toString() {
		return replacementText + " | " + description + " | " + summary;
	}

}
