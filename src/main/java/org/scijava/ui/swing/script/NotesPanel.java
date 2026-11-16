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
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import com.formdev.flatlaf.FlatClientProperties;
import org.fife.ui.rtextarea.ClipboardHistory;
import org.scijava.prefs.PrefService;

/**
 * A "Notes" side panel: a persistent scratchpad (top) and a view of the
 * editor's clipboard history (bottom). Double-clicking (or pressing Enter on)
 * a clipboard entry inserts it at the caret of the active editor.
 * <p>
 * Clipboard history is the one maintained by RSyntaxTextArea, i.e., it records
 * what is copied or cut inside the editor. It is not persisted. The scratchpad
 * is saved to the preferences (in chunks, since preference values are size
 * limited).
 * </p>
 */
class NotesPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final String PREFS_COUNT = "script.editor.notes.chunks";
	private static final String PREFS_CHUNK = "script.editor.notes.chunk";
	private static final String PREFS_WRAP = "script.editor.notes.wrap";
	private static final int CHUNK_SIZE = 4000;
	private static final int MAX_CHARS = 200000;
	private static final int SAVE_DELAY_MS = 1000;
	private static final int REFRESH_DELAY_MS = 750;
	private static final int ROW_MAX_CHARS = 120;

	private final Supplier<? extends JTextComponent> activeEditor;
	private final PrefService prefs;
	private final Class<?> prefsOwner;
	private final JTextArea scratch;
	private final DefaultListModel<String> model = new DefaultListModel<>();
	private final JList<String> list = new JList<>(model) {

		private static final long serialVersionUID = 1L;

		@Override
		protected void paintComponent(final java.awt.Graphics g) {
			super.paintComponent(g);
			if (model.isEmpty()) GuiUtils.paintEmptyHint(g, this, "Paste history will appear here",
					"Double-click an entry to insert it");
		}
	};
	private final Timer saveTimer;
	private final Timer poll;
	private boolean loading;

	NotesPanel(final Supplier<? extends JTextComponent> activeEditor,
		final PrefService prefs, final Class<?> prefsOwner)
	{
		super(new BorderLayout());
		this.activeEditor = activeEditor;
		this.prefs = prefs;
		this.prefsOwner = prefsOwner;

		// Scratchpad
		scratch = new JTextArea() {

			private static final long serialVersionUID = 1L;

			@Override
			protected void paintComponent(final java.awt.Graphics g) {
				super.paintComponent(g);
				if (getDocument().getLength() == 0) GuiUtils.paintEmptyHint(g, this, "Jot down snippets",
						"They are saved between sessions");
			}
		};
		scratch.setLineWrap(prefs.getBoolean(prefsOwner, PREFS_WRAP, true));
		scratch.setWrapStyleWord(true);
		scratch.setComponentPopupMenu(scratchMenu());
		final JScrollPane scratchPane = new JScrollPane(scratch);
		GuiUtils.flattenScrollBars(scratchPane);

		// Scratchpad
		final JPanel pane1 = new JPanel(new BorderLayout());
		final JLabel scratchHeader = header("Scratchpad");
		pane1.add(scratchHeader, BorderLayout.NORTH);
		pane1.add(scratchPane, BorderLayout.CENTER);

		// Clipboard history
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new Renderer());
		final JScrollPane listPane = new JScrollPane(list);
		GuiUtils.flattenScrollBars(listPane);
		final JPanel pane2 = new JPanel(new BorderLayout());
		final JLabel clipboardHeader = header("Clipboard History");
		pane2.add(clipboardHeader, BorderLayout.NORTH);
		pane2.add(listPane, BorderLayout.CENTER);

		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pane1, pane2);
		split.setResizeWeight(.75);
		// No divider: the Scratchpad header (with a hairline on top) is the drag handle
		GuiUtils.installSplitHandle(clipboardHeader, split);
		add(split, BorderLayout.CENTER);

		// List interaction
		list.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(final MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
					final int idx = list.locationToIndex(e.getPoint());
					if (idx >= 0 && list.getCellBounds(idx, idx).contains(e.getPoint()))
						insert(model.get(idx));
				}
			}

			@Override
			public void mousePressed(final MouseEvent e) {
				maybePopup(e);
			}

			@Override
			public void mouseReleased(final MouseEvent e) {
				maybePopup(e);
			}
		});
		list.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(final KeyEvent e) {
				final String sel = list.getSelectedValue();
				if (sel == null) return;
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					insert(sel);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
					remove(sel);
					e.consume();
				}
			}
		});

		// Persistence of scratchpad
		load();
		saveTimer = new Timer(SAVE_DELAY_MS, e -> save());
		saveTimer.setRepeats(false);
		scratch.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(final DocumentEvent e) {
				changed();
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {
				changed();
			}

			@Override
			public void changedUpdate(final DocumentEvent e) {
				changed();
			}
		});

		// Cheap polling, only while visible. RSyntaxTextArea provides no listener
		refresh();
		poll = new Timer(REFRESH_DELAY_MS, e -> {
			if (isShowing()) refresh();
		});
	}

	@Override
	public void addNotify() {
		super.addNotify();
		if (!poll.isRunning()) poll.start();
	}

	@Override
	public void removeNotify() {
		poll.stop();
		super.removeNotify();
	}

	private JPopupMenu scratchMenu() {
		final JPopupMenu menu = new JPopupMenu();
		final JCheckBoxMenuItem wrap = new JCheckBoxMenuItem("Wrap Lines");
		wrap.addActionListener(e -> {
			scratch.setLineWrap(wrap.isSelected());
			prefs.put(prefsOwner, PREFS_WRAP, wrap.isSelected());
		});
		menu.add(wrap);
		menu.addSeparator();
		final JMenuItem discard = new JMenuItem("Discard");
		discard.addActionListener(e -> {
			if (GuiUtils.confirm(this, "Discard the contents of the scratchpad?", "Discard Scratchpad?",
				"Discard")) scratch.setText("");
		});
		menu.add(discard);
		menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {

			@Override
			public void popupMenuWillBecomeVisible(final javax.swing.event.PopupMenuEvent e) {
				wrap.setSelected(scratch.getLineWrap());
				discard.setEnabled(scratch.getDocument().getLength() > 0);
			}

			@Override
			public void popupMenuWillBecomeInvisible(final javax.swing.event.PopupMenuEvent e) {
				// nothing to do
			}

			@Override
			public void popupMenuCanceled(final javax.swing.event.PopupMenuEvent e) {
				// nothing to do
			}
		});
		return menu;
	}

	/** Flushes pending scratchpad changes to the preferences. */
	void flush() {
		if (saveTimer.isRunning()) {
			saveTimer.stop();
			save();
		}
	}

	/** Brings the clipboard list up to date (cheap if nothing changed). */
	void refresh() {
		final List<String> history = new ArrayList<>(ClipboardHistory.get()
			.getHistory());
		boolean same = history.size() == model.size();
		for (int i = 0; same && i < history.size(); i++)
			same = history.get(i).equals(model.get(i));
		if (same) return;
		final String selected = list.getSelectedValue();
		model.clear();
		for (final String s : history)
			model.addElement(s);
		if (selected != null && model.contains(selected))
			list.setSelectedValue(selected, true);
	}

	private JLabel header(final String text) {
		final JLabel label = new JLabel(text);
		label.setBorder(BorderFactory.createEmptyBorder(2, 2,0,0));
		label.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
        label.setForeground(GuiUtils.secondaryColor());
		return label;
	}

	private void maybePopup(final MouseEvent e) {
		if (!e.isPopupTrigger()) return;
		final int idx = list.locationToIndex(e.getPoint());
		if (idx >= 0 && list.getCellBounds(idx, idx).contains(e.getPoint()))
			list.setSelectedIndex(idx);
		final String sel = list.getSelectedValue();
		final JPopupMenu menu = new JPopupMenu();
		JMenuItem mi = new JMenuItem("Insert at Caret");
		mi.setEnabled(sel != null);
		mi.addActionListener(a -> insert(sel));
		menu.add(mi);
		mi = new JMenuItem("Copy");
		mi.setEnabled(sel != null);
		mi.addActionListener(a -> Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new StringSelection(sel), null));
		menu.add(mi);
		mi = new JMenuItem("Send to Scratchpad");
		mi.setEnabled(sel != null);
		mi.addActionListener(a -> appendToScratchpad(sel));
		menu.add(mi);
		menu.addSeparator();
		mi = new JMenuItem("Remove Entry");
		mi.setEnabled(sel != null);
		mi.addActionListener(a -> remove(sel));
		menu.add(mi);
		mi = new JMenuItem("Clear History");
		mi.setEnabled(!model.isEmpty());
		mi.addActionListener(a -> clearHistory());
		menu.add(mi);
		menu.show(list, e.getX(), e.getY());
	}

	private void insert(final String text) {
		final JTextComponent ta = activeEditor.get();
		if (ta == null || !ta.isEditable() || !ta.isEnabled()) {
			UIManager.getLookAndFeel().provideErrorFeedback(list);
			return;
		}
		ta.replaceSelection(text);
		ta.requestFocusInWindow();
	}

	private void appendToScratchpad(final String text) {
		final String current = scratch.getText();
		scratch.append((current.isEmpty() || current.endsWith("\n") ? "" : "\n") +
			text + "\n");
		scratch.setCaretPosition(scratch.getDocument().getLength());
	}

	private void remove(final String text) {
		final List<String> live = liveHistory();
		if (live != null) live.remove(text);
		refresh();
	}

	private void clearHistory() {
		final List<String> live = liveHistory();
		if (live != null) live.clear();
		refresh();
	}

	/**
	 * ClipboardHistory#getHistory() returns a copy, and the API offers no way
	 * to remove entries. Access the backing list (best effort).
	 */
	@SuppressWarnings("unchecked")
	private static List<String> liveHistory() {
		try {
			final java.lang.reflect.Field f = ClipboardHistory.class
				.getDeclaredField("history");
			f.setAccessible(true);
			return (List<String>) f.get(ClipboardHistory.get());
		}
		catch (final Exception | LinkageError ignored) {
			return null;
		}
	}

	private void changed() {
		if (!loading) saveTimer.restart();
	}

	private void load() {
		loading = true;
		try {
			final int n = prefs.getInt(prefsOwner, PREFS_COUNT, 0);
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < n; i++)
				sb.append(prefs.get(prefsOwner, PREFS_CHUNK + i, ""));
			scratch.setText(sb.toString());
			scratch.setCaretPosition(0);
		}
		catch (final RuntimeException ignored) {
			// preferences are best effort
		}
		finally {
			loading = false;
		}
	}

	private void save() {
		try {
			String text = scratch.getText();
			if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
			final int oldCount = prefs.getInt(prefsOwner, PREFS_COUNT, 0);
			final int n = (text.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
			for (int i = 0; i < n; i++)
				prefs.put(prefsOwner, PREFS_CHUNK + i, text.substring(i * CHUNK_SIZE,
					Math.min(text.length(), (i + 1) * CHUNK_SIZE)));
			for (int i = n; i < oldCount; i++)
				prefs.remove(prefsOwner, PREFS_CHUNK + i);
			prefs.put(prefsOwner, PREFS_COUNT, n);
		}
		catch (final RuntimeException ignored) {
			// preferences are best effort
		}
	}

	private static String escape(final String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static class Renderer extends DefaultListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(final JList<?> l,
			final Object value, final int index, final boolean selected,
			final boolean focus)
		{
			final String s = String.valueOf(value);
			String oneLine = s.trim().replaceAll("\\s+", " ");
			if (oneLine.length() > ROW_MAX_CHARS) oneLine = oneLine.substring(0,
				ROW_MAX_CHARS) + "...";
			super.getListCellRendererComponent(l, oneLine, index, selected, focus);
			setBorder(new EmptyBorder(2, 6, 2, 6));
			String tip = s.length() > 1500 ? s.substring(0, 1500) + "\n..." : s;
			setToolTipText("<html><pre>" + escape(tip) + "</pre></html>");
			return this;
		}
	}
}
