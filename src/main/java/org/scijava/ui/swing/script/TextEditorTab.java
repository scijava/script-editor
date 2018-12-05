/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2018 Board of Regents of the University of
 * Wisconsin-Madison, Max Planck Institute of Molecular Cell Biology and
 * Genetics, and others.
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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.scijava.ui.swing.script.TextEditor.Executer;

/**
 * Tab in a {@link TextEditor} containing an {@link EditorPane}.
 *
 * @author Jonathan Hale
 */
@SuppressWarnings("serial")
public class TextEditorTab extends JSplitPane {

	protected final EditorPane editorPane;
	protected final JTextArea screen = new JTextArea();
	protected final JTextArea prompt = new JTextArea();
	protected final JScrollPane scroll;
	protected boolean showingErrors;
	private Executer executer;
	private final JButton runit, batchit, killit, toggleErrors;
	private final JCheckBox incremental;
	private final JSplitPane screenAndPromptSplit;

	private final TextEditor textEditor;

	public TextEditorTab(final TextEditor textEditor) {
		super(JSplitPane.VERTICAL_SPLIT);
		super.setResizeWeight(350.0 / 430.0);

		this.textEditor = textEditor;
		editorPane = new EditorPane();

		screen.setEditable(false);
		screen.setLineWrap(true);
		screen.setFont(new Font("Courier", Font.PLAIN, 12));

		final JPanel bottom = new JPanel();
		bottom.setLayout(new GridBagLayout());
		final GridBagConstraints bc = new GridBagConstraints();

		bc.gridx = 0;
		bc.gridy = 0;
		bc.weightx = 0;
		bc.weighty = 0;
		bc.anchor = GridBagConstraints.NORTHWEST;
		bc.fill = GridBagConstraints.NONE;
		runit = new JButton("Run");
		runit.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent ae) {
				textEditor.runText();
			}
		});
		bottom.add(runit, bc);

		bc.gridx = 1;
		batchit = new JButton("Batch");
		batchit.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent ae) {
				textEditor.runBatch();
			}
		});
		bottom.add(batchit, bc);

		bc.gridx = 2;
		killit = new JButton("Kill");
		killit.setEnabled(false);
		killit.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent ae) {
				kill();
			}
		});
		bottom.add(killit, bc);
		
		bc.gridx = 3;
		incremental = new JCheckBox("persistent");
		incremental.setEnabled(true);
		incremental.setSelected(false);
		incremental.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent ae) {
				textEditor.setIncremental(incremental.isSelected());
				prompt.setEnabled(incremental.isSelected());
			}
		});
		bottom.add(incremental, bc);

		bc.gridx = 4;
		bc.fill = GridBagConstraints.HORIZONTAL;
		bc.weightx = 1;
		bottom.add(new JPanel(), bc);

		bc.gridx = 5;
		bc.fill = GridBagConstraints.NONE;
		bc.weightx = 0;
		bc.anchor = GridBagConstraints.NORTHEAST;
		toggleErrors = new JButton("Show Errors");
		toggleErrors.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				toggleErrors();
			}
		});
		bottom.add(toggleErrors, bc);

		bc.gridx = 6;
		bc.fill = GridBagConstraints.NONE;
		bc.weightx = 0;
		bc.anchor = GridBagConstraints.NORTHEAST;
		final JButton clear = new JButton("Clear");
		clear.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent ae) {
				getScreen().setText("");
			}
		});
		bottom.add(clear, bc);

		bc.gridx = 0;
		bc.gridy = 1;
		bc.anchor = GridBagConstraints.NORTHWEST;
		bc.fill = GridBagConstraints.BOTH;
		bc.weightx = 1;
		bc.weighty = 1;
		bc.gridwidth = 7;
		screen.setEditable(false);
		screen.setLineWrap(true);
		final Font font = new Font("Courier", Font.PLAIN, 12);
		screen.setFont(font);
		scroll = new JScrollPane(screen);
		scroll.setPreferredSize(new Dimension(600, 80));
		bottom.add(scroll, bc);
		
		prompt.setEnabled(false);
		
		screenAndPromptSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, prompt, bottom);
		screenAndPromptSplit.setDividerLocation(0.0); // prompt collapsed by default
		
		super.setTopComponent(editorPane.wrappedInScrollbars());
		super.setBottomComponent(screenAndPromptSplit);
	}
	
	// Package-private
	JSplitPane getScreenAndPromptSplit() {
		return screenAndPromptSplit;
	}

	/** Invoke in the context of the event dispatch thread. */
	public void prepare() {
		editorPane.setEditable(false);
		runit.setEnabled(false);
		killit.setEnabled(true);
	}

	public void restore() {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				editorPane.setEditable(true);
				runit.setEnabled(true);
				killit.setEnabled(false);
				setExecutor(null);
			}
		});
	}

	public void toggleErrors() {
		showingErrors = !showingErrors;
		if (showingErrors) {
			toggleErrors.setText("Show Output");
			scroll.setViewportView(textEditor.getErrorScreen());
		}
		else {
			toggleErrors.setText("Show Errors");
			scroll.setViewportView(screen);
		}
	}

	public void showErrors() {
		if (!showingErrors) toggleErrors();
		else if (scroll.getViewport().getView() == null) {
			scroll.setViewportView(textEditor.getErrorScreen());
		}
	}

	public void showOutput() {
		if (showingErrors) toggleErrors();
	}

	public JTextArea getScreen() {
		return showingErrors ? textEditor.getErrorScreen() : screen;
	}
	
	public JTextArea getPrompt() {
		return prompt;
	}

	boolean isExecuting() {
		return null != getExecuter();
	}

	public final String getTitle() {
		return (editorPane.fileChanged() ? "*" : "") + editorPane.getFileName() +
			(isExecuting() ? " (Running)" : "");
	}

	protected void kill() {
		if (null == getExecuter()) return;
		// Graceful attempt:
		getExecuter().interrupt();
		// Give it 3 seconds. Then, stop it.
		final long now = System.currentTimeMillis();
		new Thread() {

			{
				setPriority(Thread.NORM_PRIORITY);
			}

			@Override
			public void run() {
				while (System.currentTimeMillis() - now < 3000)
					try {
						Thread.sleep(100);
					}
					catch (final InterruptedException e) {
						/* ignore */
					}
				if (null != getExecuter()) getExecuter().obliterate();
				restore();

			}
		}.start();
	}

	public Executer getExecuter() {
		return executer;
	}

	public void setExecutor(final Executer executer) {
		this.executer = executer;
	}

	public JTextComponent getEditorPane() {
		return editorPane;
	}
}
