/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2022 SciJava developers.
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

import java.awt.BorderLayout;
import java.awt.Dimension;
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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.scijava.ui.swing.script.TextEditor.Executer;

/**
 * Tab in a {@link TextEditor} containing an {@link EditorPane}.
 *
 * @author Jonathan Hale
 */
public class TextEditorTab extends JSplitPane {

	private static final String DOWN_ARROW = "\u25BC";
	private static final String RIGHT_ARROW = "\u25B6";

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
	private int screenAndPromptSplitDividerLocation;

	private final TextEditor textEditor;
	private DropTarget dropTarget;
	private DropTargetListener dropTargetListener;

	public TextEditorTab(final TextEditor textEditor) {
		super(JSplitPane.VERTICAL_SPLIT);
		super.setResizeWeight(350.0 / 430.0);
		setOneTouchExpandable(true);

		this.textEditor = textEditor;
		editorPane = new EditorPane();
		dropTargetListener = new DropTargetListener() {
			@Override
			public void dropActionChanged(final DropTargetDragEvent arg0) {}
			
			@Override
			public void drop(final DropTargetDropEvent e) {
				if (e.getDropAction() != DnDConstants.ACTION_COPY) {
					e.rejectDrop();
					return;
				}
				e.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE); // fix for InvalidDnDOperationException: No drop current
				final Transferable t = e.getTransferable();
				if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return;
				try {
					final Object o = t.getTransferData(DataFlavor.javaFileListFlavor);
					if (!(o instanceof List)) return;
					final List<?> list = (List<?>) o;
					if (list.isEmpty()) return;
					String path;
					final Object first = list.get(0);
					if (first instanceof String) path = (String) first;
					else if (first instanceof File) path = ((File) first).getAbsolutePath();
					else return;
					// If I knew how to get the current caret index under Point p, it could be inserted there with:
					// Point p = e.getLocation();
					// ... but it is more predictable (less surprising) to insert where the caret is:
					editorPane.getRSyntaxDocument().insertString(editorPane.getCaretPosition(), path, null);
				} catch (final Exception ex) {
					ex.printStackTrace();
				}
			}
			
			@Override
			public void dragOver(final DropTargetDragEvent e) {
				if (e.getDropAction() != DnDConstants.ACTION_COPY) e.rejectDrag();
			}
			
			@Override
			public void dragExit(final DropTargetEvent e) {}
			
			@Override
			public void dragEnter(final DropTargetDragEvent e) {
				if (e.getDropAction() != DnDConstants.ACTION_COPY) e.rejectDrag();
			}
		};
		dropTarget = new DropTarget(editorPane, DnDConstants.ACTION_COPY, dropTargetListener);

		// tweaks for console
		screen.setEditable(false);
		screen.setLineWrap(false);
		screen.setFont(getEditorPane().getFont());
		textEditor.applyConsolePopupMenu(screen);

		final JPanel bottom = new JPanel();
		bottom.setLayout(new GridBagLayout());
		final GridBagConstraints bc = new GridBagConstraints();

		bc.gridx = 0;
		bc.gridy = 0;
		bc.weightx = 0;
		bc.weighty = 0;
		bc.anchor = GridBagConstraints.CENTER;
		bc.fill = GridBagConstraints.HORIZONTAL;
		runit = new JButton("Run");
		runit.setToolTipText("Control+R, F5, or F11");
		runit.addActionListener(ae -> textEditor.runText());
		bottom.add(runit, bc);

		bc.gridx = 1;
		batchit = new JButton("Batch");
		batchit.setToolTipText("Requires at least one @File SciJava parameter to be declared");
		batchit.addActionListener(e -> textEditor.runBatch());
		bottom.add(batchit, bc);

		bc.gridx = 2;
		killit = new JButton("Kill");
		killit.setEnabled(false);
		killit.addActionListener(ae -> kill());
		bottom.add(killit, bc);

		bc.gridx = 3;
		incremental = new JCheckBox("REPL");
		incremental.setEnabled(true);
		incremental.setSelected(false);
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
		toggleErrors.addActionListener(e -> toggleErrors());
		bottom.add(toggleErrors, bc);

		bc.gridx = 6;
		bc.fill = GridBagConstraints.NONE;
		bc.weightx = 0;
		bc.anchor = GridBagConstraints.NORTHEAST;
		final JButton clear = new JButton("Clear");
		clear.addActionListener(ae -> {
			getScreen().setText("");
			if (showingErrors) editorPane.getErrorHighlighter().reset();
		});
		bottom.add(clear, bc);
		
		bc.gridx = 7;
		switchSplit = new JButton(RIGHT_ARROW);
		switchSplit.setToolTipText("Switch location");
		switchSplit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (DOWN_ARROW.equals(switchSplit.getText())) {
					TextEditorTab.this.setOrientation(JSplitPane.VERTICAL_SPLIT);
				} else {
					TextEditorTab.this.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
				}
				// Keep prompt collapsed if not in use
				if (!incremental.isSelected()) {
					setREPLVisible(false);
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
		scroll = new JScrollPane(screen);
		bottom.add(scroll, bc);

		prompt.setEnabled(false);
		prompt.setFont(getEditorPane().getFont());
		prompt.setTabSize(editorPane.getTabSize());

		final JPanel prompt_panel = new JPanel();
		prompt_panel.setMinimumSize(new Dimension(0, 0));
		prompt_panel.setVisible(false);
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
		prompt_help.addActionListener(a -> {
			final String msg = "This REPL (Read-Evaluate-Print-Loop) parses " + textEditor.getCurrentLanguage().getLanguageName() + " code.\n\n"
					+ "Key bindings:\n"
					+ "  [Enter]:   Evaluate code\n"
					+ "  [Shift+Enter]:   Add line break (also alt-enter and meta-enter)\n"
					+ "  [Page UP] or [Ctrl+P]:   Show previous entry in the history\n"
					+ "  [Page DOWN] or [Ctrl+N]:   Show next entry in the history\n"
					+ "\n"
					+ "If 'Use arrow keys' is checked, then up/down arrows work like\n"
					+ "Page UP/DOWN, and Shift+up/down arrows work like arrow\n"
					+ "keys before for caret movement within a multi-line prompt."
					;
			textEditor.info(msg, "REPL Help");
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

		incremental.addActionListener(ae -> {
			if (incremental.isSelected() && null == textEditor.getCurrentLanguage()) {
				incremental.setSelected(false);
				textEditor.error("Select a language first!");
				return;
			}
			textEditor.setIncremental(incremental.isSelected());
			prompt_title.setText(incremental.isSelected() ? //
				"REPL: " + textEditor.getCurrentLanguage().getLanguageName() : "");
			prompt.setEnabled(incremental.isSelected());
			prompt_panel.setVisible(incremental.isSelected());
		});

		screenAndPromptSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bottom, prompt_panel);

		// Enable ErrorSrip à la Eclipse. This will keep track of lines with 'Mark All'
		// occurrences as well as lines associated with ParserNotice.Level.WARNING and
		// ParserNotice.Level.ERROR. NB: As is, the end of the strip corresponds to the
		// last line of text in the text area: E.g., for a text area with just 3 lines,
		// line 2 will be marked at the strip's half height
		final ErrorStrip es = new ErrorStrip(editorPane);
		es.setShowMarkAll(true);
		es.setShowMarkedOccurrences(true);
		final JPanel holder = new JPanel(new BorderLayout());
		holder.add(editorPane.wrappedInScrollbars());
		holder.add(es, BorderLayout.LINE_END);
		super.setLeftComponent(holder);
		super.setRightComponent(screenAndPromptSplit);
		screenAndPromptSplit.setDividerLocation(1.0);

		// Persist Script Editor layout whenever split pane divider is adjusted.
		addPropertyChangeListener(evt -> {
			if ("dividerLocation".equals(evt.getPropertyName()))
				textEditor.saveWindowSizeToPrefs();
		});
	}

	// Package-private
	JSplitPane getScreenAndPromptSplit() {
		return screenAndPromptSplit;
	}

	void setREPLVisible(final boolean visible) {
		SwingUtilities.invokeLater(() -> {
			if (visible) {
				// If stashed location of divider is invalid, set divider to half of panel's height and re-stash
				if (screenAndPromptSplitDividerLocation <= 0
						|| screenAndPromptSplitDividerLocation <= getScreenAndPromptSplit().getMinimumDividerLocation()
						|| screenAndPromptSplitDividerLocation >= getScreenAndPromptSplit().getMaximumDividerLocation()) {
					getScreenAndPromptSplit().setDividerLocation(.5d);
					screenAndPromptSplitDividerLocation = getScreenAndPromptSplit().getDividerLocation();
				} else {
					getScreenAndPromptSplit().setDividerLocation(screenAndPromptSplitDividerLocation);
				}
			} else { // collapse to bottom
				screenAndPromptSplitDividerLocation = getScreenAndPromptSplit().getDividerLocation();
				getScreenAndPromptSplit().setDividerLocation(1f);
			}
			incremental.setSelected(visible);
		});
	}

	@Override
	public void setOrientation(final int orientation) {
		super.setOrientation(orientation);
		switchSplit.setText(orientation == JSplitPane.VERTICAL_SPLIT ? //
			RIGHT_ARROW : DOWN_ARROW);
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
		SwingUtilities.invokeLater(() -> {
			editorPane.setEditable(true);
			runit.setEnabled(true);
			killit.setEnabled(false);
			setExecutor(null);
			if(incremental.isSelected())
				prompt.requestFocusInWindow();
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
