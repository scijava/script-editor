/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2023 SciJava developers.
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

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;

/**
 * TODO
 *
 * @author Johannes Schindelin
 */
public class FindAndReplaceDialog extends JDialog implements ActionListener {

	TextEditor textEditor;
	JTextField searchField, replaceField;
	JLabel replaceLabel;
	JCheckBox matchCase, wholeWord, markAll, regex, forward;
	JButton findNext, replace, replaceAll, cancel;
	private boolean restrictToConsole;

	public FindAndReplaceDialog(final TextEditor editor) {
		super(editor);
		textEditor = editor;

		final Container root = getContentPane();
		root.setLayout(new GridBagLayout());

		final JPanel text = new JPanel(new GridBagLayout());
		final GridBagConstraints c = new GridBagConstraints();

		c.gridx = c.gridy = 0;
		c.gridwidth = c.gridheight = 1;
		c.weightx = c.weighty = 1;
		c.ipadx = c.ipady = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.LINE_START;
		searchField = createField("Find: ", text, c, null);
		replaceField = createField("Replace with: ", text, c, this);

		c.gridwidth = 4;
		c.gridheight = c.gridy;
		c.gridx = c.gridy = 0;
		c.weightx = c.weighty = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.LINE_START;
		root.add(text, c);

		c.gridy = c.gridheight;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.weightx = 0.001;
		matchCase = createCheckBox("Match case", root, c);
		regex = createCheckBox("Regex", root, c);
		forward = createCheckBox("Search forward", root, c);
		forward.setSelected(true);
		c.gridx = 0;
		c.gridy++;
		markAll = createCheckBox("Mark all", root, c);
		wholeWord = createCheckBox("Whole word", root, c);

		c.gridx = 4;
		c.gridy = 0;
		findNext = createButton("Find Next", root, c);
		replace = createButton("Replace", root, c);
		replaceAll = createButton("Replace All", root, c);
		cancel = createButton("Cancel", root, c);
		setResizable(true);
		pack();

		getRootPane().setDefaultButton(findNext);

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		final KeyAdapter listener = new KeyAdapter() {

			@Override
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) dispose();
			}
		};
		// TODO: handle via actionmap
		for (final Component component : getContentPane().getComponents())
			component.addKeyListener(listener);
		searchField.addKeyListener(listener);
		replaceField.addKeyListener(listener);
	}

	private JTextArea getSearchArea() {
		if (restrictToConsole) {
			if (textEditor.getTab().showingErrors)
				return textEditor.getErrorScreen();
			else
				return textEditor.getTab().getScreen();
		}
		return getTextArea();
	}

	final private EditorPane getTextAreaAsEditorPane() {
		return textEditor.getEditorPane();
	}

	final public RSyntaxTextArea getTextArea() {
		return getTextAreaAsEditorPane();
	}

	@SuppressWarnings("deprecation")
	@Override
	public void show(final boolean replace) {
		if (replace && restrictToConsole)
			throw new IllegalArgumentException("replace is not compatible with restrictToConsole");
		updateTitle();
		replaceLabel.setEnabled(replace);
		replaceField.setEnabled(replace);
		replaceField.setBackground(replace ? searchField.getBackground()
			: getRootPane().getBackground());
		this.replace.setEnabled(replace);
		replaceAll.setEnabled(replace);
		markAll.setEnabled(!restrictToConsole);
		searchField.selectAll();
		replaceField.selectAll();
		getRootPane().setDefaultButton(findNext);
		show(); // cannot call setVisible
	}

	private JTextField createField(final String name, final Container container,
		final GridBagConstraints c, final FindAndReplaceDialog replaceDialog)
	{
		c.weightx = 0.001;
		final JLabel label = new JLabel(name);
		if (replaceDialog != null) replaceDialog.replaceLabel = label;
		container.add(label, c);
		c.gridx++;
		c.weightx = 1;
		final JTextField field = new JTextField();
		container.add(field, c);
		c.gridx--;
		c.gridy++;
		return field;
	}

	private JCheckBox createCheckBox(final String name, final Container panel,
		final GridBagConstraints c)
	{
		final JCheckBox checkBox = new JCheckBox(name);
		checkBox.addActionListener(this);
		panel.add(checkBox, c);
		c.gridx++;
		return checkBox;
	}

	private JButton createButton(final String name, final Container panel,
		final GridBagConstraints c)
	{
		final JButton button = new JButton(name);
		button.addActionListener(this);
		panel.add(button, c);
		c.gridy++;
		return button;
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final Object source = e.getSource();
		if (source == cancel) {
			dispose();
			return;
		}
		final String text = searchField.getText();
		if (text.length() == 0) return;
		if (source == findNext) searchOrReplace(false);
		else if (source == replace) searchOrReplace(true);
		else if (source == replaceAll) {
			if (getTextAreaAsEditorPane().isLocked()) {
				JOptionPane.showMessageDialog(this, "File is currently locked.");
				return;
			}
			final int replace =
				SearchEngine.replaceAll(getTextArea(), getSearchContext(true))
					.getCount();
			JOptionPane.showMessageDialog(this, replace + " replacements made!");
		}
	}

	public boolean searchOrReplace(final boolean replace) {
		return searchOrReplace(replace, forward.isSelected());
	}

	public void setRestrictToConsole(final boolean restrict) {
		restrictToConsole = restrict;
		markAll.setEnabled(!restrict);
		updateTitle();
	}

	private void updateTitle() {
		String title = "Find";
		if (isReplace())
			title +="/Replace";
		if (restrictToConsole)
			title += " in Console";
		setTitle(title);
	}

	public boolean searchOrReplace(final boolean replace, final boolean forward) {
		if (searchOrReplaceFromHere(replace, forward)) return true;
		final JTextArea textArea = getSearchArea();
		final int caret = textArea.getCaretPosition();
		textArea.setCaretPosition(forward ? 0 : textArea.getDocument().getLength());
		if (searchOrReplaceFromHere(replace, forward)) return true;
		JOptionPane.showMessageDialog(this, "No match found!");
		textArea.setCaretPosition(caret);
		return false;
	}

	protected boolean searchOrReplaceFromHere(final boolean replace) {
		return searchOrReplaceFromHere(forward.isSelected());
	}

	protected SearchContext getSearchContext(final boolean forward) {
		final SearchContext context = new SearchContext();
		context.setSearchFor(searchField.getText());
		context.setReplaceWith(replaceField.getText());
		context.setSearchForward(forward);
		context.setMatchCase(matchCase.isSelected());
		context.setWholeWord(wholeWord.isSelected());
		context.setRegularExpression(regex.isSelected());
		context.setMarkAll(markAll.isSelected() && !restrictToConsole);
		return context;
	}

	protected boolean searchOrReplaceFromHere(final boolean replace,
		final boolean forward)
	{
		final SearchContext context = getSearchContext(forward);
		if (replace) {
			return SearchEngine.replace(getTextArea(), context).wasFound();
		}
		return SearchEngine.find(getSearchArea(), context).wasFound();
	}

	public boolean isReplace() {
		return (restrictToConsole) ? false : replace.isEnabled();
	}

	/**
	 * Sets the content of the search field.
	 *
	 * @param pattern The new content of the search field.
	 */
	public void setSearchPattern(final String pattern) {
		searchField.setText(pattern);
	}
}
