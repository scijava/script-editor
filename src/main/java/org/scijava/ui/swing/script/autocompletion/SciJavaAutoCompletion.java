/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2026 SciJava developers.
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

package org.scijava.ui.swing.script.autocompletion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.rtextarea.RTextArea;
import org.scijava.ui.swing.script.autocompletion.AdditionalEdits.Edit;

/**
 * An {@link org.fife.ui.autocomplete.AutoCompletion} that, in addition to
 * inserting the chosen completion, applies any additional edits the
 * completion carries (see {@link SciJavaCompletion}). This is how
 * language-neutral auto-imports are realized in the Swing editor.
 *
 * @author Curtis Rueden
 */
public class SciJavaAutoCompletion extends
	org.fife.ui.autocomplete.AutoCompletion
{

	public SciJavaAutoCompletion(final CompletionProvider provider) {
		super(provider);
		// Expand threshold to describe other overloads
		// (2000 is just a magic number)
		setParameterDescriptionTruncateThreshold(2000);
		if (provider instanceof SciJavaCompletionProvider) {
			((SciJavaCompletionProvider) provider).setUpdateListener(
				this::refreshPopup);
		}
	}

	/**
	 * Shows the current (just improved) completions: in the popup, if it is
	 * showing, or in a new one, if the previous result had nothing to show.
	 */
	private void refreshPopup(final boolean previousWasEmpty) {
		final JTextComponent comp = getTextComponent();
		if (comp == null || !comp.isFocusOwner()) return;
		if (!isPopupVisible() && !previousWasEmpty) return; // NB: User closed it.
		// NB: Never insert a lone completion the user has not chosen.
		final boolean single = getAutoCompleteSingleChoices();
		setAutoCompleteSingleChoices(false);
		try {
			doCompletion();
		}
		finally {
			setAutoCompleteSingleChoices(single);
		}
	}

	@Override
	protected void insertCompletion(final Completion c,
		final boolean typedParamListStartChar)
	{
		if (!(c instanceof AdditionalEdits)) {
			super.insertCompletion(c, typedParamListStartChar);
			return;
		}
		final List<Edit> edits = ((AdditionalEdits) c).getAdditionalEdits();
		if (edits.isEmpty()) {
			super.insertCompletion(c, typedParamListStartChar);
			return;
		}
		final JTextComponent comp = getTextComponent();
		final RTextArea area = comp instanceof RTextArea ? (RTextArea) comp : null;

		if (area != null) area.beginAtomicEdit();
		try {
			// Insert the primary completion first (at the completion point).
			super.insertCompletion(c, typedParamListStartChar);

			// Then apply additional edits. They lie before the completion point,
			// so applying them from highest to lowest offset keeps each offset
			// valid, and the caret (tracked by the document) shifts accordingly.
			final List<Edit> sorted = new ArrayList<>(edits);
			sorted.sort(Comparator.comparingInt(Edit::start).reversed());
			for (final Edit edit : sorted) {
				applyEdit(comp, edit);
			}
		}
		finally {
			if (area != null) area.endAtomicEdit();
		}
	}

	private void applyEdit(final JTextComponent comp, final Edit edit) {
		try {
			final int len = edit.end() - edit.start();
			if (len > 0) comp.getDocument().remove(edit.start(), len);
			if (edit.newText() != null && !edit.newText().isEmpty()) {
				comp.getDocument().insertString(edit.start(), edit.newText(), null);
			}
		}
		catch (final BadLocationException exc) {
			// Document changed underneath us; skip this edit rather than fail.
		}
	}
}
