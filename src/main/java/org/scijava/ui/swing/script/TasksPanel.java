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
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * A "Tasks" side panel listing the TODO, FIXME and HACK comments of all open editor tabs that are detected by the
 * editor's task tag parser. Double-clicking (or pressing Enter on) an entry jumps to it. Tasks are discovered
 * while typing, so the list is kept up to date by a check that only runs while the panel is visible.
 */
class TasksPanel extends JPanel {

	/** A task comment: its tab, 1-based line, document offset and text */
	record Task(TextEditorTab tab, int line, int offset, String text) {}

	private static final long serialVersionUID = 1L;
	private static final int REFRESH_DELAY_MS = 1500;
	private static final int TEXT_MAX_CHARS = 100;
	private static final String[] TAGS = { "TODO", "FIXME", "HACK" };
	private static final Color[] TAG_COLORS = { new Color(48, 150, 252), new Color(237, 137, 54), new Color(155, 112, 225) };

	private final Supplier<List<Task>> source;
	private final DefaultListModel<Task> model = new DefaultListModel<>();
	private final List<String> signature = new ArrayList<>();
	private final JList<Task> list;

	/**
	 * @param source supplies the current tasks of all tabs (in display order)
	 * @param jumper moves the editor to the given task
	 */
	TasksPanel(final Supplier<List<Task>> source, final Consumer<Task> jumper) {
		super(new BorderLayout());
		this.source = source;

		list = new JList<>(model) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void paintComponent(final Graphics g) {
				super.paintComponent(g);
				if (model.isEmpty()) GuiUtils.paintEmptyHint(g, this, "No tasks",
					"Comments with TODO, FIXME or HACK show up here");
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
		});
		list.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(final KeyEvent e) {
				final Task sel = list.getSelectedValue();
				if (sel != null && e.getKeyCode() == KeyEvent.VK_ENTER) {
					jumper.accept(sel);
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

	/** The task keyword a comment starts with, or null */
	static String tagOf(final String text) {
		for (final String tag : TAGS)
			if (text.startsWith(tag)) return tag;
		return null;
	}

	/** The color of the keyword a task comment starts with, or null */
	static Color tagColor(final String text) {
		for (int i = 0; i < TAGS.length; i++)
			if (text.startsWith(TAGS[i])) return TAG_COLORS[i];
		return null;
	}

	private static String escape(final String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Brings the list up to date (cheap if nothing changed). */
	void refresh() {
		final List<Task> current = source.get();
		final List<String> sig = new ArrayList<>(current.size());
		for (final Task t : current)
			sig.add(System.identityHashCode(t.tab()) + ":" + t.line() + ":" + t
				.text());
		if (sig.equals(signature)) return;
		signature.clear();
		signature.addAll(sig);
		final int selected = list.getSelectedIndex();
		model.clear();
		for (final Task t : current)
			model.addElement(t);
		if (selected >= 0 && selected < model.size()) list.setSelectedIndex(
			selected);
	}

	private static class Renderer extends DefaultListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(final JList<?> l,
			final Object value, final int index, final boolean selected,
			final boolean focus)
		{
			final Task t = (Task) value;
			final String name = t.tab().editorPane.getFileName();
			String text = t.text();
			if (text.length() > TEXT_MAX_CHARS) text = text.substring(0,
				TEXT_MAX_CHARS) + "...";
			final String where = (name == null ? "Untitled" : name) + ":" + t.line();
			final String tag = tagOf(text);
			// Colored keyword, unless selected (the selection color needs contrast)
			final String label = (tag == null || selected) ? where + "   " + text
				: "<html>" + escape(where) + "&nbsp;&nbsp;&nbsp;<b><font color='" + String.format("#%06x",
					tagColor(text).getRGB() & 0xFFFFFF) + "'>" + tag + "</font></b>" + escape(text
						.substring(tag.length())) + "</html>";
			super.getListCellRendererComponent(l, label, index, selected, focus);
			setBorder(new EmptyBorder(2, 6, 2, 6));
			setToolTipText(t.text());
			return this;
		}
	}
}
