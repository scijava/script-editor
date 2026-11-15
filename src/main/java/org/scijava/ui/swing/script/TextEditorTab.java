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

package org.scijava.ui.swing.script;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.geom.RoundRectangle2D;
import java.awt.FlowLayout;
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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatFileChooserDetailsViewIcon;
import com.formdev.flatlaf.icons.FlatHelpButtonIcon;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.Token;
import org.scijava.ui.swing.script.TextEditor.Executer;

/**
 * Tab in a {@link TextEditor} containing an {@link EditorPane}.
 *
 * @author Jonathan Hale
 */
public class TextEditorTab extends JSplitPane {

	/** Which console stream(s) to display. */
	private enum ConsoleView {
			BOTH("Output+Errors"), OUTPUT("Output"), ERRORS("Errors");

			private final String label;

			ConsoleView(final String label) {
				this.label = label;
			}

			@Override
			public String toString() {
				return label;
			}
	}

	protected final EditorPane editorPane;
	protected final JTextArea screen = new ConsoleArea(ConsoleArea.Content.OUTPUT);
	/** Output and errors interleaved, in the order they were written. */
	private final ConsoleArea combinedScreen = new ConsoleArea(ConsoleArea.Content.MIXED);
	protected final JTextArea prompt = new JTextArea();
	/** Status line for vim mode: the mode, partial command or message. */
	private final JLabel vimStatus = new JLabel(" ");
	protected final JToggleButton updownarrows = new JToggleButton();
	protected final JScrollPane scroll;
	/** True iff the console is showing errors only. */
	protected boolean showingErrors;
	private ConsoleView view = ConsoleView.BOTH;
	private boolean updatingViewChooser;
	private volatile boolean skipErrorMirroring;
	private Executer executer;
	private final JButton runit, batchit, killit;
	private final JComboBox<ConsoleView> viewChooser;
	private boolean replOn; // the REPL is on (prompt shown)
	private JPanel promptPanel;
	private int promptDividerSize; // thickness of the divider above the prompt (hidden while the REPL is off)
	private final JSplitPane screenAndPromptSplit;
	private int screenAndPromptSplitDividerLocation;
	private int bottomLastLocation = -1; // divider location while the bottom part was last shown

	private final TextEditor textEditor;
	private DropTarget dropTarget;
	private DropTargetListener dropTargetListener;

	public TextEditorTab(final TextEditor textEditor) {
		super(JSplitPane.VERTICAL_SPLIT);
		super.setResizeWeight(350.0 / 430.0);

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
		screen.setLineWrap(textEditor.isConsoleWrap());
		screen.setFont(getEditorPane().getFont());
		combinedScreen.setEditable(false);
		combinedScreen.setLineWrap(textEditor.isConsoleWrap());
		combinedScreen.setFont(getEditorPane().getFont());
		textEditor.applyConsolePopupMenu(screen);
		textEditor.applyConsolePopupMenu(combinedScreen);
		mirrorInto(combinedScreen, screen, false);
		mirrorInto(combinedScreen, textEditor.getErrorScreen(), true);

		final JPanel bottom = new JPanel(new BorderLayout());
		final JToolBar bar = new JToolBar();
		bar.setFloatable(false);
		bar.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));

		runit = new JButton("Run", Icons.play(false));
		runit.setDisabledIcon(Icons.play(true));
		runit.setToolTipText("Control+R, F5, or F11");
		textEditor.cmdPalette.register(runit, "Interpreter");
		runit.addActionListener(ae -> textEditor.runText());
		bar.add(runit);

		batchit = new JButton("Batch", Icons.fastForward(false));
		batchit.putClientProperty("JButton.buttonType", "borderless");

		batchit.setDisabledIcon(Icons.fastForward(true));
		batchit.setToolTipText("Requires at least one @File SciJava parameter to be declared");
		batchit.addActionListener(e -> textEditor.runBatch());
		bar.add(batchit);

		killit = new JButton("Stop", Icons.stop(false));
		killit.setDisabledIcon(Icons.stop(true));
		killit.setToolTipText("Stop the running script (interrupts its thread)");
		killit.setEnabled(false);
		killit.addActionListener(ae -> kill());
		bar.add(killit);

		bar.add(Box.createHorizontalGlue());

		viewChooser = new JComboBox<>(ConsoleView.values());
		viewChooser.setToolTipText("Which console streams to show");
		viewChooser.addActionListener(e -> {
			if (!updatingViewChooser) setView((ConsoleView) viewChooser.getSelectedItem());
		});
		final JPanel viewPanel = new JPanel();
		viewPanel.setOpaque(false);
		final JLabel showLabel = new JLabel("Show:");
		showLabel.setEnabled(false);
		viewPanel.add(showLabel);
		viewPanel.add(viewChooser);
		viewPanel.setMaximumSize(viewPanel.getPreferredSize()); // do not stretch
		bar.add(viewPanel);
		bar.add(Box.createHorizontalStrut(4));

		final JButton clear = new JButton("Clear", Icons.trash(false));
		clear.setDisabledIcon(Icons.trash(true));
		clear.addActionListener(ae -> {
			screen.setText("");
			textEditor.getErrorScreen().setText("");
			combinedScreen.setText("");
			if (view != ConsoleView.OUTPUT) editorPane.getErrorHighlighter().reset();
		});
		bar.add(clear);
		textEditor.cmdPalette.register(clear, "Console");

		scroll = new JScrollPane(screen);
		GuiUtils.flattenScrollBars(scroll);
		bottom.add(bar, BorderLayout.PAGE_START);
		bottom.add(scroll, BorderLayout.CENTER);

		prompt.setEnabled(false);
		prompt.setFont(getEditorPane().getFont());
		prompt.setTabSize(editorPane.getTabSize());
		prompt.setLineWrap(textEditor.isReplWrap());

		final JPanel prompt_panel = new JPanel();
		prompt_panel.setMinimumSize(new Dimension(0, 0));
		prompt_panel.setVisible(false);
		promptPanel = prompt_panel;
		prompt_panel.setLayout(new BorderLayout());

		final JToolBar promptBar = new JToolBar();
		promptBar.setFloatable(false);
		promptBar.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
		promptBar.setBorder(null);
		promptBar.setMargin(null);
		prompt_panel.add(promptBar, BorderLayout.PAGE_START);
		// Same scroll pane (and thus border) as the console
		final JScrollPane promptScroll = new JScrollPane(prompt);
		GuiUtils.flattenScrollBars(promptScroll);
		prompt_panel.add(promptScroll, BorderLayout.CENTER);
		final JLabel label = new JLabel();
		label.setForeground(GuiUtils.secondaryColor());
		label.setIcon(Icons.repl());
		promptBar.add(label);
		promptBar.add(Box.createHorizontalGlue());
		updownarrows.setText("Arrow Keys");
		updownarrows.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
		promptBar.add(Box.createHorizontalGlue());
		promptBar.add(updownarrows);
		final FlatHelpButtonIcon helpIcon = new FlatHelpButtonIcon();
		helpIcon.setScale(.8f); // matches the font size set by the "small" style
		helpIcon.applyStyleProperty("questionMarkColor", GuiUtils.secondaryColor());
		helpIcon.applyStyleProperty("background", promptBar.getBackground());
		final JButton statusHelp = new JButton(helpIcon);
		statusHelp.addActionListener(a -> {
			final int shift = InputEvent.SHIFT_DOWN_MASK;
			final int ctrl = InputEvent.CTRL_DOWN_MASK;
			final String html = """
					<html><body>
					<table cellpadding="3">
					<tr><td>%s</td><td>Evaluate code</td></tr>
					<tr><td>%s</td><td>Add line break (also with Ctrl, Alt or Meta)</td></tr>
					<tr><td>%s or %s</td><td>Previous entry in the history</td></tr>
					<tr><td>%s or %s</td><td>Next entry in the history</td></tr>
					</table>
					<p>With <i>Arrow Keys</i> on, %s and %s also navigate the history. Combined<br>
					with a modifier (e.g., %s) they move the caret within a multi-line prompt.</p>
					</body></html>"""
					.formatted(keyHtml(KeyEvent.VK_ENTER, 0), keyHtml(KeyEvent.VK_ENTER, shift),
							keyHtml(KeyEvent.VK_PAGE_UP, 0), keyHtml(KeyEvent.VK_P, ctrl),
							keyHtml(KeyEvent.VK_PAGE_DOWN, 0), keyHtml(KeyEvent.VK_N, ctrl),
							keyHtml(KeyEvent.VK_UP, 0), keyHtml(KeyEvent.VK_DOWN, 0), keyHtml(KeyEvent.VK_UP, shift));
			textEditor.showHTMLDialog("REPL (Read-Evaluate-Print-Loop) Key Bindings", html);
		});

		promptBar.add(statusHelp);

		// Compact bar: drop the vertical padding FlatLaf adds to toolbar buttons (margin 3 + spacing 1)
		final String tight = "toolbar.margin: 0,3,0,3; toolbar.spacingInsets: 0,0,0,0";
		updownarrows.putClientProperty(FlatClientProperties.STYLE, tight);
		updownarrows.setForeground(GuiUtils.secondaryColor()); // Must come after the style properties: applying them resets UIResource foregrounds
		statusHelp.putClientProperty(FlatClientProperties.STYLE, tight);
		screenAndPromptSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bottom, prompt_panel);
		// No divider: the prompt bar is the handle. Keep promptDividerSize at 0 so toggling the REPL
		// never brings the divider back
		GuiUtils.installSplitHandle(promptBar, screenAndPromptSplit);
		promptDividerSize = 0;

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
		holder.add(vimStatus, BorderLayout.PAGE_END);
		vimStatus.setFont(new Font(Font.MONOSPACED, Font.PLAIN, vimStatus.getFont().getSize()));
		vimStatus.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		vimStatus.setVisible(false);
		editorPane.getVimHandler().setStatusListener(status -> {
			vimStatus.setVisible(status != null);
			// Note: a blank keeps the line from collapsing.
			vimStatus.setText(status == null || status.isEmpty() ? " " : status);
		});
		editorPane.getVimHandler().setExHandler(textEditor::handleVimCommand);
		super.setLeftComponent(holder);
		super.setRightComponent(screenAndPromptSplit);
		GuiUtils.installSplitHandle(bar, this); // no divider in the "below" layout: the toolbar is the handle
		screenAndPromptSplit.setDividerLocation(1.0);

		setView(view); // sync the console and chooser with the initial view
		applyConsoleColors();

		// Persist Script Editor layout whenever split pane divider is adjusted.
		addPropertyChangeListener(evt -> {
			if ("dividerLocation".equals(evt.getPropertyName())) {
				if (isBottomVisible() && getDividerLocation() > 50) bottomLastLocation = getDividerLocation();
				textEditor.saveWindowSizeToPrefs();
				textEditor.syncStatusBar();
			}
		});
	}

	/** Whether the REPL is on */
	boolean isREPLEnabled() {
		return replOn;
	}

	/** Turns the REPL on/off (turning it on requires a language) */
	void setREPLEnabled(final boolean enable) {
		if (enable == replOn) return;
		if (enable && null == textEditor.getCurrentLanguage()) {
			textEditor.error("Select a language first!");
			return;
		}
		if (enable) setBottomVisible(true); // the prompt lives in the bottom part
		replOn = enable;
		screenAndPromptSplit.setDividerSize(enable ? promptDividerSize : 0);
		textEditor.setIncremental(enable);
		prompt.setEnabled(enable);
		promptPanel.setVisible(enable);
		textEditor.syncStatusBar();
	}

	/** Whether the bottom part (output and prompt) is showing, i.e., not collapsed */
	boolean isBottomVisible() {
		if (axisSize() <= 0) return true; // not laid out yet
		return getDividerLocation() < collapsedLocation() - 5;
	}

	/**
	 * Divider location at which the bottom part is fully collapsed. Unlike
	 * getMaximumDividerLocation() this ignores the minimum size of the bottom part: in a window
	 * too narrow (or short) to honor it, the part is shown truncated and would wrongly count as
	 * collapsed, so that it could not be collapsed again
	 */
	private int collapsedLocation() {
		final Insets in = getInsets();
		return axisSize() - getDividerSize() - (isLayoutBelow() ? in.top + in.bottom : in.left + in.right);
	}

	/** Collapses the bottom part (output and prompt), or restores it to its last height */
	void setBottomVisible(final boolean visible) {
		if (visible == isBottomVisible()) return;
		if (visible) {
			final int max = collapsedLocation();
			setDividerLocation(bottomLastLocation > 50 && bottomLastLocation < max - 5 ? bottomLastLocation
					: (int) (axisSize() * .7));
		} else {
			setDividerLocation(1d); // all the way down
		}
	}

	// Package-private
	JSplitPane getScreenAndPromptSplit() {
		return screenAndPromptSplit;
	}

	void setREPLVisible(final boolean visible) {
		SwingUtilities.invokeLater(() -> {
			screenAndPromptSplit.setDividerSize(visible ? promptDividerSize : 0); // before moving the divider
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
			replOn = visible;
			textEditor.syncStatusBar();
		});
	}

	@Override
	public void setOrientation(final int orientation) {
		super.setOrientation(orientation);
		bottomLastLocation = -1; // locations of the other layout do not apply
		if (textEditor != null) textEditor.syncStatusBar();
	}

	/** Whether the console is (or would be) below the editor, as opposed to on its right side */
	boolean isLayoutBelow() {
		return getOrientation() == JSplitPane.VERTICAL_SPLIT;
	}

	/** Size of the tab along the axis of the divider */
	private int axisSize() {
		return isLayoutBelow() ? getHeight() : getWidth();
	}

	/**
	 * Status bar behavior: clicking the layout that is active collapses/restores the console,
	 * clicking the other layout switches to it (showing the console)
	 */
	void toggleLayout(final boolean below) {
		if (isLayoutBelow() == below) {
			setBottomVisible(!isBottomVisible());
			return;
		}
		setOrientation(below ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT);
		if (!replOn) setREPLVisible(false); // keep the prompt collapsed if not in use
		setDividerLocation(.7d);
	}

	// Package private
	void destroy() {
		dropTarget.removeDropTargetListener(dropTargetListener);
		dropTarget = null;
		dropTargetListener = null;
	}

	/** Invoke in the context of the event dispatch thread. */
	public void prepare() {
		setBottomVisible(true); // a script that runs must be able to show its output
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
			if (replOn)
				prompt.requestFocusInWindow();
		});
	}

	public void toggleErrors() {
		setView(view == ConsoleView.ERRORS ? ConsoleView.OUTPUT : ConsoleView.ERRORS);
	}

	/** Shows errors, unless errors are already shown (alone or with output). */
	public void showErrors() {
		setBottomVisible(true);
		if (view == ConsoleView.OUTPUT) setView(ConsoleView.ERRORS);
		else if (scroll.getViewport().getView() == null) setView(view);
	}

	/** Shows output, unless output is already shown (alone or with errors). */
	public void showOutput() {
		if (view == ConsoleView.ERRORS) setView(ConsoleView.OUTPUT);
	}

	private void setView(final ConsoleView newView) {
		view = newView;
		showingErrors = newView == ConsoleView.ERRORS;
		updatingViewChooser = true;
		try {
			viewChooser.setSelectedItem(newView);
		}
		finally {
			updatingViewChooser = false;
		}
		scroll.setViewportView(getScreen());
	}

	/**
	 * Runs the given action, which writes to the errors area, without the
	 * combined view showing that text. Useful for text also written to output.
	 */
	void withoutMirroringErrors(final Runnable action) {
		skipErrorMirroring = true;
		try {
			action.run();
		}
		finally {
			skipErrorMirroring = false;
		}
	}

	/** Dims everything currently in the consoles, so the next run stands out. */
	void startNewRun() {
		((ConsoleArea) screen).startNewRun();
		combinedScreen.startNewRun();
		final JTextArea errors = textEditor.getErrorScreen();
		if (errors instanceof ConsoleArea) ((ConsoleArea) errors).startNewRun();
	}

	/** HTML for a key stroke, e.g., for help dialogs */
	private static String keyHtml(final int keyCode, final int modifiers) {
		return "<tt>" + GuiUtils.prettifiedKey(KeyStroke.getKeyStroke(keyCode, modifiers)).trim() + "</tt>";
	}

	/** Makes the consoles and REPL prompt use the editor pane's colors. */
	void applyConsoleColors() {
		// Note: themes set the text color via the syntax scheme, not the component foreground.
		final Style style = editorPane.getSyntaxScheme().getStyle(Token.IDENTIFIER);
		final Color fg = style != null && style.foreground != null ? //
			style.foreground : editorPane.getForeground();
		final Color bg = editorPane.getBackground();
		combinedScreen.setColors(fg, bg);
		((ConsoleArea) screen).setColors(fg, bg);
		final JTextArea errors = textEditor.getErrorScreen();
		if (errors instanceof ConsoleArea) ((ConsoleArea) errors).setColors(fg, bg);
		prompt.setForeground(fg);
		prompt.setBackground(bg);
		prompt.setCaretColor(fg);
	}

	/**
	 * Copies everything appended to {@code source} into {@code target}, so that
	 * {@code target} shows several streams in the order they were written.
	 */
	private void mirrorInto(final ConsoleArea target, final JTextArea source,
		final boolean error)
	{
		source.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(final DocumentEvent e) {
				if (error && skipErrorMirroring) return;
				try {
					target.append(e.getDocument().getText(e.getOffset(), e.getLength()), error);
					final int lines = target.getLineCount();
					// Eliminate the first 100 lines when reaching 1100 lines.
					if (lines > 1100) target.replaceRange("", 0, target.getLineEndOffset(lines - 1000));
				}
				catch (final BadLocationException ex) {
					// Note: the source changed underneath us; nothing sensible to mirror.
				}
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {}

			@Override
			public void changedUpdate(final DocumentEvent e) {}
		});
	}

	public JTextArea getScreen() {
		switch (view) {
			case ERRORS:
				return textEditor.getErrorScreen();
			case BOTH:
				return combinedScreen;
			default:
				return screen;
		}
	}

	void setConsoleWrap(final boolean wrap) {
		screen.setLineWrap(wrap);
		combinedScreen.setLineWrap(wrap);
	}

	void setPromptWrap(final boolean wrap) {
		prompt.setLineWrap(wrap);
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
