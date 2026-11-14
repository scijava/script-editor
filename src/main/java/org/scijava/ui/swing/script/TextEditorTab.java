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
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
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
import java.io.File;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatAbstractIcon;
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
		screen.setLineWrap(false);
		screen.setFont(getEditorPane().getFont());
		combinedScreen.setEditable(false);
		combinedScreen.setLineWrap(false);
		combinedScreen.setFont(getEditorPane().getFont());
		textEditor.applyConsolePopupMenu(screen);
		textEditor.applyConsolePopupMenu(combinedScreen);
		mirrorInto(combinedScreen, screen, false);
		mirrorInto(combinedScreen, textEditor.getErrorScreen(), true);

		final JPanel bottom = new JPanel(new BorderLayout());
		final JToolBar bar = new JToolBar();
		bar.setFloatable(false);
		bar.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));

		runit = new JButton("Run", new PlayIcon(false));
		runit.setDisabledIcon(new PlayIcon(true));
		runit.setToolTipText("Control+R, F5, or F11");
		textEditor.cmdPalette.register(runit, "Interpreter");
		runit.addActionListener(ae -> textEditor.runText());
		bar.add(runit);

		batchit = new JButton("Batch", new FastForwardIcon(false));
		batchit.putClientProperty("JButton.buttonType", "borderless");

		batchit.setDisabledIcon(new FastForwardIcon(true));
		batchit.setToolTipText("Requires at least one @File SciJava parameter to be declared");
		batchit.addActionListener(e -> textEditor.runBatch());
		bar.add(batchit);

		killit = new JButton("Stop", new StopIcon(false));
		killit.setDisabledIcon(new StopIcon(true));
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
		viewPanel.add(new JLabel("Show:"));
		viewPanel.add(viewChooser);
		viewPanel.setMaximumSize(viewPanel.getPreferredSize()); // do not stretch
		bar.add(viewPanel);
		bar.add(Box.createHorizontalStrut(4));

		final JButton clear = new JButton("Clear", new TrashIcon(false));
		clear.setDisabledIcon(new TrashIcon(true));
		clear.addActionListener(ae -> {
			screen.setText("");
			textEditor.getErrorScreen().setText("");
			combinedScreen.setText("");
			if (view != ConsoleView.OUTPUT) editorPane.getErrorHighlighter().reset();
		});
		bar.add(clear);
		textEditor.cmdPalette.register(clear, "Console");

		scroll = new JScrollPane(screen);
		TextEditor.GuiUtils.flattenScrollBars(scroll);
		bottom.add(bar, BorderLayout.PAGE_START);
		bottom.add(scroll, BorderLayout.CENTER);

		prompt.setEnabled(false);
		prompt.setFont(getEditorPane().getFont());
		prompt.setTabSize(editorPane.getTabSize());

		final JPanel prompt_panel = new JPanel();
		prompt_panel.setMinimumSize(new Dimension(0, 0));
		prompt_panel.setVisible(false);
		promptPanel = prompt_panel;
		prompt_panel.setLayout(new BorderLayout());

		final JToolBar promptBar = new JToolBar();
		promptBar.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
		promptBar.setBorder(null);
		promptBar.setMargin(null);
		prompt_panel.add(promptBar, BorderLayout.PAGE_START);
		// Same scroll pane (and thus border) as the console
		final JScrollPane promptScroll = new JScrollPane(prompt);
		TextEditor.GuiUtils.flattenScrollBars(promptScroll);
		prompt_panel.add(promptScroll, BorderLayout.CENTER);
		final JLabel label = new JLabel();
		label.setForeground(TextEditor.GuiUtils.secondaryColor());
		label.setIcon(new TextEditor.ReplIcon());
		promptBar.add(label);
		promptBar.add(Box.createHorizontalGlue());
		updownarrows.setText("Arrow Keys");
		updownarrows.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
		promptBar.add(Box.createHorizontalGlue());
		promptBar.add(updownarrows);
		final FlatHelpButtonIcon helpIcon = new FlatHelpButtonIcon();
		helpIcon.setScale(.8f); // matches the font size set by the "small" style
		helpIcon.applyStyleProperty("questionMarkColor", TextEditor.GuiUtils.secondaryColor());
		helpIcon.applyStyleProperty("background", promptBar.getBackground());
		final JButton statusHelp = new JButton(helpIcon);
		statusHelp.addActionListener(a -> {
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

		promptBar.add(statusHelp);

		// Compact bar: drop the vertical padding FlatLaf adds to toolbar buttons (margin 3 + spacing 1)
		final String tight = "toolbar.margin: 0,3,0,3; toolbar.spacingInsets: 0,0,0,0";
		updownarrows.putClientProperty(FlatClientProperties.STYLE, tight);
		updownarrows.setForeground(TextEditor.GuiUtils.secondaryColor()); // Must come after the style properties: applying them resets UIResource foregrounds
		statusHelp.putClientProperty(FlatClientProperties.STYLE, tight);
		screenAndPromptSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bottom, prompt_panel);
		// No divider: the prompt bar is the handle. Keep promptDividerSize at 0 so toggling the REPL
		// never brings the divider back
		TextEditor.GuiUtils.installSplitHandle(promptBar, screenAndPromptSplit);
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
		TextEditor.GuiUtils.installSplitHandle(bar, this); // no divider in the "below" layout: the toolbar is the handle
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

	/**
	 * Icon painted from SVG path data (absolute M, L, C and Z commands, 640x640 view box). Glyphs by
	 * Font Awesome Free (https://fontawesome.com), licensed CC BY 4.0. Colors adapt to light/dark themes
	 */
	private static class GlyphIcon extends FlatAbstractIcon {

		private final Path2D path;
		private final boolean disabled;
		private final Color light, dark;

		GlyphIcon(final String svgPath, final Color light, final Color dark, final boolean disabled) {
			super(16, 16, null);
			this.path = parse(svgPath);
			this.light = light;
			this.dark = dark;
			this.disabled = disabled;
		}

		@Override
		protected void paintIcon(final Component c, final Graphics2D g) {
			Color color = com.formdev.flatlaf.FlatLaf.isLafDark() ? dark : light;
			if (disabled) {
				final Color d = UIManager.getColor("Button.disabledText");
				color = (d != null) ? d : Color.GRAY;
			}
			g.setColor(color);
			g.fill(path);
		}

		/** Parses the path, scaling it from the 640 unit view box to the 16 unit icon */
		private static Path2D parse(final String d) {
			final double k = 16d / 640d;
			final java.util.regex.Matcher m = java.util.regex.Pattern.compile("[MLCZz]|-?\\d*\\.?\\d+").matcher(d);
			final List<String> tokens = new java.util.ArrayList<>();
			while (m.find())
				tokens.add(m.group());
			final Path2D.Double p = new Path2D.Double();
			char cmd = 'M';
			int i = 0;
			while (i < tokens.size()) {
				final String t = tokens.get(i);
				if (Character.isLetter(t.charAt(0))) {
					cmd = t.charAt(0);
					i++;
					if (cmd == 'Z' || cmd == 'z') {
						p.closePath();
						continue;
					}
				}
				switch (cmd) {
				case 'M':
					p.moveTo(k * Double.parseDouble(tokens.get(i)), k * Double.parseDouble(tokens.get(i + 1)));
					i += 2;
					cmd = 'L'; // further pairs are line segments
					break;
				case 'L':
					p.lineTo(k * Double.parseDouble(tokens.get(i)), k * Double.parseDouble(tokens.get(i + 1)));
					i += 2;
					break;
				case 'C':
					p.curveTo(k * Double.parseDouble(tokens.get(i)), k * Double.parseDouble(tokens.get(i + 1)),
							k * Double.parseDouble(tokens.get(i + 2)), k * Double.parseDouble(tokens.get(i + 3)),
							k * Double.parseDouble(tokens.get(i + 4)), k * Double.parseDouble(tokens.get(i + 5)));
					i += 6;
					break;
				default:
					throw new IllegalArgumentException("Unsupported path command: " + cmd);
				}
			}
			return p;
		}
	}

	private static final Color GREEN = new Color(0x44774E), GREEN_DARK = new Color(0x446E4B);
	private static final Color RED = new Color(0xDF5663), RED_DARK = new Color(0xB45355);

	/** Green "play" triangle (Font Awesome) */
	private static final class PlayIcon extends GlyphIcon {

		PlayIcon(final boolean disabled) {
			super("M187.2 100.9C174.8 94.1 159.8 94.4 147.6 101.6C135.4 108.8 128 121.9 128 136L128 504C128 518.1 135.5 531.2 147.6 538.4C159.7 545.6 174.8 545.9 187.2 539.1L523.2 355.1C536 348.1 544 334.6 544 320C544 305.4 536 291.9 523.2 284.9L187.2 100.9z",
					GREEN_DARK, GREEN, disabled);
		}
	}

	/** Green "fast forward" (double play) icon (Font Awesome), for batch runs */
	private static final class FastForwardIcon extends GlyphIcon {

		FastForwardIcon(final boolean disabled) {
			super("M403.7 107.1C392.1 96 375 92.9 360.3 99.2C345.6 105.5 336 120 336 136L336 272.3L163.7 107.2C152.1 96 135 92.9 120.3 99.2C105.6 105.5 96 120 96 136L96 504C96 520 105.6 534.5 120.3 540.8C135 547.1 152.1 544 163.7 532.9L336 367.7L336 504C336 520 345.6 534.5 360.3 540.8C375 547.1 392.1 544 403.7 532.9L595.7 348.9C603.6 341.4 608 330.9 608 320C608 309.1 603.5 298.7 595.7 291.1L403.7 107.1z",
					GREEN, GREEN_DARK, disabled);
		}
	}

	/** Red "stop" rounded square (Font Awesome) */
	private static final class StopIcon extends GlyphIcon {

		StopIcon(final boolean disabled) {
			super("M160 96L480 96C515.3 96 544 124.7 544 160L544 480C544 515.3 515.3 544 480 544L160 544C124.7 544 96 515.3 96 480L96 160C96 124.7 124.7 96 160 96z",
					RED, RED_DARK, disabled);
		}
	}

	/** Neutral "trash can" icon (Font Awesome), for the Clear button */
	private static final class TrashIcon extends GlyphIcon {

		TrashIcon(final boolean disabled) {
			super("M232.7 69.9C237.1 56.8 249.3 48 263.1 48L377 48C390.8 48 403 56.8 407.4 69.9L416 96L512 96C529.7 96 544 110.3 544 128C544 145.7 529.7 160 512 160L128 160C110.3 160 96 145.7 96 128C96 110.3 110.3 96 128 96L224 96L232.7 69.9zM128 208L512 208L512 512C512 547.3 483.3 576 448 576L192 576C156.7 576 128 547.3 128 512L128 208zM216 272C202.7 272 192 282.7 192 296L192 488C192 501.3 202.7 512 216 512C229.3 512 240 501.3 240 488L240 296C240 282.7 229.3 272 216 272zM320 272C306.7 272 296 282.7 296 296L296 488C296 501.3 306.7 512 320 512C333.3 512 344 501.3 344 488L344 296C344 282.7 333.3 272 320 272zM424 272C410.7 272 400 282.7 400 296L400 488C400 501.3 410.7 512 424 512C437.3 512 448 501.3 448 488L448 296C448 282.7 437.3 272 424 272z",
					new Color(0x6E6E6E), new Color(0xA9A9A9), disabled);
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
