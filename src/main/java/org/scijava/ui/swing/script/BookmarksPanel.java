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
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;

/**
 * A "Bookmarks" side panel listing the bookmarks of all open editor tabs.
 * Double-clicking (or pressing Enter on) an entry jumps to it. Bookmarks are
 * toggled in the editor gutter, so the list is kept up to date by a cheap check
 * that only runs while the panel is visible.
 */
class BookmarksPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int REFRESH_DELAY_MS = 1500;
	private static final int TEXT_MAX_CHARS = 100;

	private final Supplier<List<Bookmark>> source;
	private final Consumer<Bookmark> jumper;
	private final DefaultListModel<Bookmark> model = new DefaultListModel<>();
	private final List<String> signature = new ArrayList<>();
	private final JList<Bookmark> list;

	/**
	 * @param source supplies the current bookmarks of all tabs (in display order)
	 * @param jumper moves the editor to the given bookmark
	 */
	BookmarksPanel(final Supplier<List<Bookmark>> source,
		final Consumer<Bookmark> jumper)
	{
		super(new BorderLayout());
		this.source = source;
		this.jumper = jumper;

		list = new JList<>(model) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(final Graphics g) {
                super.paintComponent(g);
                if (model.isEmpty()) GuiUtils.paintEmptyHint(g, this, "No bookmarks",
                        "Click next to a line number to bookmark it");
            }
        };
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new Renderer());
		final JScrollPane pane = new JScrollPane(list);
		GuiUtils.flattenScrollBars(pane);
		add(pane, BorderLayout.CENTER);

		list.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(final MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
					final int idx = list.locationToIndex(e.getPoint());
					if (idx >= 0 && list.getCellBounds(idx, idx).contains(e.getPoint()))
						jumper.accept(model.get(idx));
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
				final Bookmark sel = list.getSelectedValue();
				if (sel == null) return;
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					jumper.accept(sel);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
					remove(sel);
					e.consume();
				}
			}
		});

		refresh();
		final Timer poll = new Timer(REFRESH_DELAY_MS, e -> {
			if (isShowing()) refresh();
		});
		poll.start();
	}

	/** Brings the list up to date (cheap if nothing changed). */
	void refresh() {
		final List<Bookmark> current = source.get();
		final List<String> sig = new ArrayList<>(current.size());
		for (final Bookmark bk : current)
			sig.add(System.identityHashCode(bk.tab) + ":" + bk.getLineNumber() + ":" +
				lineText(bk));
		if (sig.equals(signature)) return;
		signature.clear();
		signature.addAll(sig);
		final int selectedLine = list.getSelectedValue() == null ? -1 : list
			.getSelectedIndex();
		model.clear();
		for (final Bookmark bk : current)
			model.addElement(bk);
		if (selectedLine >= 0 && selectedLine < model.size()) list
			.setSelectedIndex(selectedLine);
	}

	private void remove(final Bookmark bk) {
		bk.tab.editorPane.toggleBookmark(bk.getLineNumber());
		refresh();
	}

	private void clearAll() {
		for (final Bookmark bk : new ArrayList<>(source.get()))
			bk.tab.editorPane.toggleBookmark(bk.getLineNumber());
		refresh();
	}

	private void maybePopup(final MouseEvent e) {
		if (!e.isPopupTrigger()) return;
		final int idx = list.locationToIndex(e.getPoint());
		if (idx >= 0 && list.getCellBounds(idx, idx).contains(e.getPoint()))
			list.setSelectedIndex(idx);
		final Bookmark sel = list.getSelectedValue();
		final JPopupMenu menu = new JPopupMenu();
		JMenuItem mi = new JMenuItem("Go to Bookmark");
		mi.setEnabled(sel != null);
		mi.addActionListener(a -> jumper.accept(sel));
		menu.add(mi);
		mi = new JMenuItem("Remove Bookmark");
		mi.setEnabled(sel != null);
		mi.addActionListener(a -> remove(sel));
		menu.add(mi);
		menu.addSeparator();
		mi = new JMenuItem("Clear All Bookmarks");
		mi.setEnabled(!model.isEmpty());
		mi.addActionListener(a -> clearAll());
		menu.add(mi);
		menu.show(list, e.getX(), e.getY());
	}

	/** The (trimmed) text of the bookmarked line, or an empty string. */
	private static String lineText(final Bookmark bk) {
		try {
			final int line = bk.getLineNumber();
			if (line < 0) return "";
			final int start = bk.tab.editorPane.getLineStartOffset(line);
			final int end = bk.tab.editorPane.getLineEndOffset(line);
			String s = bk.tab.editorPane.getText(start, end - start).trim()
				.replaceAll("\\s+", " ");
			if (s.length() > TEXT_MAX_CHARS) s = s.substring(0, TEXT_MAX_CHARS) +
				"...";
			return s;
		}
		catch (final BadLocationException | RuntimeException ignored) {
			return "";
		}
	}

	private static class Renderer extends DefaultListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(final JList<?> l,
			final Object value, final int index, final boolean selected,
			final boolean focus)
		{
			final Bookmark bk = (Bookmark) value;
			final String name = bk.tab.editorPane.getFileName();
			final String text = lineText(bk);
			super.getListCellRendererComponent(l, (name == null ? "Untitled"
				: name) + ":" + (bk.getLineNumber() + 1) + (text.isEmpty() ? ""
					: "   " + text), index, selected, focus);
			setBorder(new EmptyBorder(2, 6, 2, 6));
			setToolTipText(text.isEmpty() ? null : text);
			return this;
		}
	}
}
