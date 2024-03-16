/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2024 SciJava developers.
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

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Segment;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RecordableTextAction;


public class EditorPaneActions extends RSyntaxTextAreaEditorKit {

	private static final long serialVersionUID = 1L;
	// action ids starting with rta: see RTextAreaEditorKit + RTADefaultInputMap
	// action ids starting with rsta: see RSyntaxTextAreaEditorKit + RSyntaxTextAreaDefaultInputMap
	// action ids starting with epa: this class
	public static final String epaCamelCaseAction = "RTA.CamelCaseAction";
	public static final String epaLowerCaseUndAction = "RTA.LowerCaseUnderscoreSep.Action";
	public static final String epaIncreaseIndentAction = "RSTA.IncreaseIndentAction";
	public static final String epaTitleCaseAction = "RTA.TitleCaseAction";
	public static final String epaToggleCommentAltAction = "RSTA.ToggleCommentAltAction";
	private final EditorPane editorPane;

	public EditorPaneActions(final EditorPane editorPane) {
		super();
		this.editorPane = editorPane;

		final int defaultMod = RTextArea.getDefaultModifier();
		final int shift = InputEvent.SHIFT_DOWN_MASK;
		final InputMap map = editorPane.getInputMap();

		/*
		 * To override existing keybindings:
		 * 1. Find the ID of the action in either org.fife.ui.rtextarea.RTADefaultInputMap
		 *    or org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaDefaultInputMap
		 * 2. Put below the new keystroke and the action ID
		 */

		// toggle comments
		if (RSyntaxUtilities.OS_LINUX == RSyntaxUtilities.getOS()) {
			// See note on RSyntaxTextAreaDefaultInputMap: Ctrl+/ types '/' on linux!
			map.put(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, defaultMod + shift), rstaToggleCommentAction);
		}
		// ES/DE/PT layout hack: https://forum.image.sc/t/shiny-new-script-editor/64160/11
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_7, defaultMod), epaToggleCommentAltAction);

		// indentation: default alt+tab/shift+alt+tab combos collide w/ OS shortcuts (at least in linux)
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, defaultMod), epaIncreaseIndentAction);
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, defaultMod + shift), rstaDecreaseIndentAction);

		// editing: override defaults for undo/redo/copy/cut/paste for consistency with menubar
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, defaultMod), copyAction);
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, defaultMod), pasteAction);
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, defaultMod), cutAction);
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, defaultMod), rtaUndoAction);
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, defaultMod), rtaRedoAction); // this should be ctrl+shift+z!?

		/*
		 * see RSyntaxTextAreaDefaultInputMap and RTADefaultInputMap for other bindings.
		 * Note that some of those bindings must be overridden in map.getParent()
		 */

		installCustomActions();
	}

	private void installCustomActions() {
		final ActionMap map = editorPane.getActionMap();

		// actions with alternative shortcuts
		map.put(epaToggleCommentAltAction, new ToggleCommentAltAction());

		// case-related actions
		map.put(epaCamelCaseAction, new CamelCaseAction());
		map.put(epaLowerCaseUndAction, new LowerCaseUnderscoreAction());
		map.put(epaTitleCaseAction, new TitleCaseAction());
		if (map.get(rtaInvertSelectionCaseAction) != null)
			map.put(rtaInvertSelectionCaseAction, new InvertSelectionCaseAction());

		// indent-related actions
		map.put(epaIncreaseIndentAction, new IncreaseIndentAction());
		if (map.get(rstaDecreaseIndentAction) != null)
			map.put(rstaDecreaseIndentAction, new DecreaseIndentAction());

		// line-related actions
		if (map.get(rtaLineUpAction) != null)
			map.put(rtaLineUpAction, new LineMoveAction(rtaLineUpAction, -1));
		if (map.get(rtaLineDownAction) != null)
			map.put(rtaLineDownAction, new LineMoveAction(rtaLineUpAction, 1));

		// actions that are not registered by default
		map.put(rtaTimeDateAction, new TimeDateAction());
		map.put(clipboardHistoryAction, new ClipboardHistoryActionImpl());
		// NB: This action is present in rsyntaxtextarea 3.1.1, but not 3.1.6.
		// So we disable it for the time being.
		//if (map.get(rstaCopyAsStyledTextAction) != null)
		//	map.put(rstaCopyAsStyledTextAction, new CopyAsStyledTextAction());
		if (map.get(rstaGoToMatchingBracketAction) != null)
			map.put(rstaGoToMatchingBracketAction, new GoToMatchingBracketAction());

	}

	public KeyStroke getAccelerator(final String actionID) {
		final Action action = editorPane.getActionMap().get(actionID);
		if (action == null) return null;
		// Pass 1: Current map, this should take precedence
		for (final KeyStroke key: editorPane.getInputMap().keys()) {
			if (actionID.equals(editorPane.getInputMap().get(key))) {
				return key;
			}
		}
		// Pass 2: All mappings, including parent map
		for (final KeyStroke key: editorPane.getInputMap().allKeys()) {
			if (actionID.equals(editorPane.getInputMap().get(key))) {
				return key;
			}
		}
		final KeyStroke[] keyStrokes = editorPane.getKeymap().getKeyStrokesForAction(action);
		if (keyStrokes != null && keyStrokes.length > 0) {
			return keyStrokes[0];
		}
		return (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
	}

	public String getAcceleratorLabel(final String actionID) {
		final KeyStroke ks = getAccelerator(actionID);
		return (ks == null) ? "" : ks.toString().replace(" pressed ", " ").replace(" ", "+").toUpperCase();
	}

	/* dummy copy of ToggleCommentAction to allow for dual inputMap registration */
	static class ToggleCommentAltAction extends ToggleCommentAction {
		private static final long serialVersionUID = 1L;

		ToggleCommentAltAction() {
			super();
			setName(epaToggleCommentAltAction);
		}
	}


	/* Variant of original action that does not allow pasting if textArea is locked */
	static class ClipboardHistoryActionImpl extends ClipboardHistoryAction {

		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformedImpl(ActionEvent e, RTextArea textArea) {
			final boolean editingPossible = textArea.isEditable() && textArea.isEnabled();
			if (!editingPossible) {
				UIManager.getLookAndFeel().provideErrorFeedback(textArea);
				return;
			}
			super.actionPerformedImpl(e, textArea);
		}
	}

	static class CamelCaseAction extends RecordableTextAction {
		private static final long serialVersionUID = 1L;

		CamelCaseAction() {
			super(epaCamelCaseAction);
		}

		@Override
		public void actionPerformedImpl(final ActionEvent e, final RTextArea textArea) {
			if (!textArea.isEditable() || !textArea.isEnabled()) {
				UIManager.getLookAndFeel().provideErrorFeedback(textArea);
				return;
			}
			final String selection = textArea.getSelectedText();
			if (selection != null) {
				final String[] words = selection.split("[\\W_]+");
				final StringBuilder buffer = new StringBuilder();
				for (int i = 0; i < words.length; i++) {
					String word = words[i];
					if (i == 0) {
						word = word.isEmpty() ? word : word.toLowerCase();
					} else {
						word = word.isEmpty() ? word
								: Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
					}
					buffer.append(word);
				}
				textArea.replaceSelection(buffer.toString());
			}
			textArea.requestFocusInWindow();
		}

		@Override
		public String getMacroID() {
			return epaCamelCaseAction;
		}

	}

	static class TitleCaseAction extends RecordableTextAction {
		private static final long serialVersionUID = 1L;

		TitleCaseAction() {
			super(epaTitleCaseAction);
		}

		@Override
		public void actionPerformedImpl(final ActionEvent e, final RTextArea textArea) {
			if (!textArea.isEditable() || !textArea.isEnabled()) {
				UIManager.getLookAndFeel().provideErrorFeedback(textArea);
				return;
			}
			final String selection = textArea.getSelectedText();
			if (selection != null) {
				final String[] words = selection.split("[\\W_]+");
				final StringBuilder buffer = new StringBuilder();
				for (int i = 0; i < words.length; i++) {
					String word = words[i];
					word = word.isEmpty() ? word
							: Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
					buffer.append(word);
					if (i < words.length - 1)
						buffer.append(" ");
				}
				textArea.replaceSelection(buffer.toString());
			}
			textArea.requestFocusInWindow();
		}

		@Override
		public String getMacroID() {
			return epaTitleCaseAction;
		}

	}

	static class LowerCaseUnderscoreAction extends RecordableTextAction {
		private static final long serialVersionUID = 1L;

		LowerCaseUnderscoreAction() {
			super(epaLowerCaseUndAction);
		}

		@Override
		public void actionPerformedImpl(final ActionEvent e, final RTextArea textArea) {
			if (!textArea.isEditable() || !textArea.isEnabled()) {
				UIManager.getLookAndFeel().provideErrorFeedback(textArea);
				return;
			}
			final String selection = textArea.getSelectedText();
			if (selection != null)
				textArea.replaceSelection(selection.trim().replaceAll("\\s", "_").toLowerCase());
			textArea.requestFocusInWindow();
		}

		@Override
		public String getMacroID() {
			return epaLowerCaseUndAction;
		}

	}

	/* Modified from DecreaseIndentAction */
	static class IncreaseIndentAction extends RecordableTextAction {

		private static final long serialVersionUID = 1L;

		private final Segment s;

		public IncreaseIndentAction() {
			super(epaIncreaseIndentAction);
			s = new Segment();
		}

		@Override
		public void actionPerformedImpl(final ActionEvent e, final RTextArea textArea) {

			if (!textArea.isEditable() || !textArea.isEnabled()) {
				UIManager.getLookAndFeel().provideErrorFeedback(textArea);
				return;
			}

			final Document document = textArea.getDocument();
			final Element map = document.getDefaultRootElement();
			final Caret c = textArea.getCaret();
			int dot = c.getDot();
			int mark = c.getMark();
			int line1 = map.getElementIndex(dot);
			final int tabSize = textArea.getTabSize();
			final StringBuilder sb = new StringBuilder();
			if (textArea.getTabsEmulated()) {
				while (sb.length() < tabSize) {
					sb.append(' ');
				}
			} else {
				sb.append('\t');
			}
			final String paddingString = sb.toString();

			// If there is a selection, indent all lines in the selection.
			// Otherwise, indent the line the caret is on.
			if (dot != mark) {
				final int line2 = map.getElementIndex(mark);
				dot = Math.min(line1, line2);
				mark = Math.max(line1, line2);
				Element elem;
				textArea.beginAtomicEdit();
				try {
					for (line1 = dot; line1 < mark; line1++) {
						elem = map.getElement(line1);
						handleIncreaseIndent(elem, document, paddingString);
					}
					// Don't do the last line if the caret is at its
					// beginning. We must call getDot() again and not just
					// use 'dot' as the caret's position may have changed
					// due to the insertion of the tabs above.
					elem = map.getElement(mark);
					final int start = elem.getStartOffset();
					if (Math.max(c.getDot(), c.getMark()) != start) {
						handleIncreaseIndent(elem, document, paddingString);
					}
				} catch (final BadLocationException ble) {
					ble.printStackTrace();
					UIManager.getLookAndFeel().provideErrorFeedback(textArea);
				} finally {
					textArea.endAtomicEdit();
				}
			} else {
				final Element elem = map.getElement(line1);
				try {
					handleIncreaseIndent(elem, document, paddingString);
				} catch (final BadLocationException ble) {
					ble.printStackTrace();
					UIManager.getLookAndFeel().provideErrorFeedback(textArea);
				}
			}

		}

		@Override
		public final String getMacroID() {
			return epaIncreaseIndentAction;
		}

		private void handleIncreaseIndent(final Element elem, final Document doc, final String pad)
				throws BadLocationException {
			final int start = elem.getStartOffset();
			int end = elem.getEndOffset() - 1; // Why always true??
			doc.getText(start, end - start, s);
			final int i = s.offset;
			end = i + s.count;
			if (end > i || (end == i && i == 0)) {
				doc.insertString(start, pad, null);
			}
		}

	}

}
