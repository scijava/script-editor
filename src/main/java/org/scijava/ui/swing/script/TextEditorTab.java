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

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
	private final JLabel prompt_title = new JLabel();
	protected final JCheckBox updownarrows = new JCheckBox("Use arrow keys");
	protected final JScrollPane scroll;
	protected boolean showingErrors;
	private Executer executer;
	private final JButton runit, batchit, killit, toggleErrors, switchSplit;
	private final JCheckBox incremental;
	private final JSplitPane screenAndPromptSplit;

	private final TextEditor textEditor;
	private DropTarget dropTarget;
	private DropTargetListener dropTargetListener;

	public TextEditorTab(final TextEditor textEditor) {
		super(JSplitPane.HORIZONTAL_SPLIT);
		super.setResizeWeight(350.0 / 430.0);
		this.setOneTouchExpandable(true);

		this.textEditor = textEditor;
		editorPane = new EditorPane();
		dropTargetListener = new DropTargetListener() {
			@Override
			public void dropActionChanged(DropTargetDragEvent arg0) {}
			
			@Override
			public void drop(DropTargetDropEvent e) {
				if (e.getDropAction() != DnDConstants.ACTION_COPY) {
					e.rejectDrop();
					return;
				}
				Transferable t = e.getTransferable();
				if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return;
				try {
					final Object o = t.getTransferData(DataFlavor.javaFileListFlavor);
					if (!(o instanceof List)) return;
					final List list = (List)o;
					if (list.isEmpty()) return;
					String path;
					Object first = list.get(0);
					if (first instanceof String) path = (String) first;
					else if (first instanceof File) path = ((File) first).getAbsolutePath();
					else return;
					// If I knew how to get the current caret index under Point p, it could be inserted there with:
					// Point p = e.getLocation();
					// ... but it is more predictable (less surprising) to insert where the caret is:
					editorPane.getRSyntaxDocument().insertString(editorPane.getCaretPosition(), path, null);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			
			@Override
			public void dragOver(DropTargetDragEvent e) {
				if (e.getDropAction() != DnDConstants.ACTION_COPY) e.rejectDrag();
			}
			
			@Override
			public void dragExit(DropTargetEvent e) {}
			
			@Override
			public void dragEnter(DropTargetDragEvent e) {
				if (e.getDropAction() != DnDConstants.ACTION_COPY) e.rejectDrag();
			}
		};
		dropTarget = new DropTarget(editorPane, DnDConstants.ACTION_COPY, dropTargetListener);

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
		runit.setToolTipText("control + R");
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
				if (incremental.isSelected() && null == textEditor.getCurrentLanguage()) {
					incremental.setSelected(false);
					JOptionPane.showMessageDialog(TextEditorTab.this, "Select a language first!");
					return;
				}
				textEditor.setIncremental(incremental.isSelected());
				prompt_title.setText(incremental.isSelected() ? "REPL: " + textEditor.getCurrentLanguage().getLanguageName() : "");
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
		
		bc.gridx = 7;
		switchSplit = new JButton("▼");
		switchSplit.setToolTipText("Switch location");
		switchSplit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (switchSplit.getText() == "▼") {
					TextEditorTab.this.setOrientation(JSplitPane.VERTICAL_SPLIT);
					switchSplit.setText("▶");
				} else {
					TextEditorTab.this.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
					switchSplit.setText("▼");
				}
				// Keep prompt collapsed if not in use
				if (!incremental.isSelected()) {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							screenAndPromptSplit.setDividerLocation(1.0);
						}
					});
				}
			}
		});
		bottom.add(switchSplit, bc);

		bc.gridx = 0;
		bc.gridy = 1;
		bc.anchor = GridBagConstraints.NORTHWEST;
		bc.fill = GridBagConstraints.BOTH;
		bc.weightx = 1;
		bc.weighty = 1;
		bc.gridwidth = 8;
		screen.setEditable(false);
		screen.setLineWrap(true);
		final Font font = new Font("Courier", Font.PLAIN, 12);
		screen.setFont(font);
		scroll = new JScrollPane(screen);
		bottom.add(scroll, bc);
		
		prompt.setEnabled(false);
		
		final JPanel prompt_panel = new JPanel();
		prompt_panel.setLayout(new GridBagLayout());
		
		bc.gridx = 0;
		bc.gridy = 0;
		bc.anchor = GridBagConstraints.NORTHWEST;
		bc.fill = GridBagConstraints.NONE;
		bc.weightx = 0;
		bc.weighty = 0;
		bc.gridwidth = 1;
		bc.gridheight = 1;
		prompt_panel.add(prompt_title, bc);
		
		bc.gridx = 1;
		bc.fill = GridBagConstraints.HORIZONTAL;
		bc.weightx = 1;
		prompt_panel.add(new JPanel(), bc);
		
		bc.gridx = 2;
		bc.anchor = GridBagConstraints.NORTHEAST;
		bc.weightx = 0;
		bc.fill = GridBagConstraints.NONE;
		prompt_panel.add(updownarrows, bc);
		
		bc.gridx = 3;
		final JButton prompt_help = new JButton("?");
		prompt_help.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent a) {
				final String msg = "This REPL (read-evaluate-print-loop) parses " + textEditor.getCurrentLanguage().getLanguageName() + " code.\n\n"
						+ "Key bindings:\n"
						+ "* enter: evaluate code\n"
						+ "* shift+enter: add line break (also alt-enter and meta-enter)\n"
						+ "* page UP or ctrl+p: show previous entry in the history\n"
						+ "* page DOWN or ctrl+n: show next entry in the history\n"
						+ "\n"
						+ "If 'Use arrow keys' is checked, then up/down arrows work like page UP/DOWN,\n"
						+ "and shift+up/down arrow work like arrow keys before for caret movement\n"
						+ "within a multi-line prompt."
						;
				JOptionPane.showMessageDialog(textEditor, msg, "REPL help", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		prompt_panel.add(prompt_help, bc);
		
		bc.gridx = 0;
		bc.gridy = 1;
		bc.anchor = GridBagConstraints.NORTHWEST;
		bc.fill = GridBagConstraints.BOTH;
		bc.weightx = 1;
		bc.weighty = 1;
		bc.gridwidth = 4;
		prompt_panel.add(prompt, bc);
		
		screenAndPromptSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bottom, prompt_panel);
		
		super.setLeftComponent(editorPane.wrappedInScrollbars());
		super.setRightComponent(screenAndPromptSplit);
		screenAndPromptSplit.setDividerLocation(600);
		screenAndPromptSplit.setDividerLocation(1.0);
	}
	
	// Package-private
	JSplitPane getScreenAndPromptSplit() {
		return screenAndPromptSplit;
	}
	
	// Package private
	void destroy() {
		dropTarget.removeDropTargetListener(dropTargetListener);
		dropTarget = null;
		dropTargetListener = null;
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

	public JTextArea getScreenInstance() {
		return screen;
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
