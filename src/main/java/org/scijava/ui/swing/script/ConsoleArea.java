/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2025 SciJava developers.
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

import java.awt.Color;
import java.awt.Graphics;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JTextArea;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.PlainView;
import javax.swing.text.Segment;
import javax.swing.text.TabExpander;
import javax.swing.text.Utilities;
import javax.swing.text.View;
import javax.swing.text.WrappedPlainView;

/**
 * A {@link JTextArea} for script output and/or errors. Errors are drawn in red,
 * and text from previous runs (see {@link #startNewRun()}) is dimmed.
 */
public class ConsoleArea extends JTextArea {

	/** What kind of text a {@link ConsoleArea} contains. */
	public enum Content {
			/** Regular output only. */
			OUTPUT,
			/** Errors only: all text is drawn as an error. */
			ERRORS,
			/** Both; the kind of each chunk is given via {@link ConsoleArea#append(String, boolean)}. */
			MIXED
	}

	/** Offset into the document which does not move when text is inserted exactly at it. */
	private static final class Mark {

		volatile int offset;
		final boolean error;

		Mark(final int offset, final boolean error) {
			this.offset = offset;
			this.error = error;
		}
	}

	private final Content content;
	private final Mark runStart = new Mark(0, false);
	private final List<Mark> chunks = new CopyOnWriteArrayList<>();
	private final Object appendLock = new Object();

	public ConsoleArea(final Content content) {
		this.content = content;
		getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(final DocumentEvent e) {
				final int o = e.getOffset(), len = e.getLength();
				if (runStart.offset > o) runStart.offset += len;
				for (final Mark m : chunks)
					if (m.offset > o) m.offset += len;
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {
				final int o = e.getOffset(), len = e.getLength();
				runStart.offset = shrink(runStart.offset, o, len);
				for (final Mark m : chunks)
					m.offset = shrink(m.offset, o, len);
				// Chunks wholly before the start of the text are irrelevant.
				while (chunks.size() >= 2 && chunks.get(1).offset <= 0)
					chunks.remove(0);
			}

			@Override
			public void changedUpdate(final DocumentEvent e) {}
		});
	}

	private static int shrink(final int offset, final int o, final int len) {
		return offset > o ? Math.max(o, offset - len) : offset;
	}

	/** Marks everything currently in this area as belonging to a previous run. */
	public void startNewRun() {
		runStart.offset = getDocument().getLength();
	}

	/**
	 * Appends text, remembering whether it is an error (only meaningful for
	 * {@link Content#MIXED}).
	 */
	public void append(final String text, final boolean error) {
		if (text == null || text.isEmpty()) return;
		synchronized (appendLock) {
			chunks.add(new Mark(getDocument().getLength(), error));
			append(text);
		}
	}

	/** Sets foreground, background and caret colors together. */
	public void setColors(final Color fg, final Color bg) {
		setForeground(fg);
		setBackground(bg);
		setCaretColor(fg);
	}

	@Override
	public void updateUI() {
		setUI(new ConsoleAreaUI());
	}

	private boolean isError(final int pos) {
		switch (content) {
			case ERRORS:
				return true;
			case MIXED:
				boolean error = false;
				for (final Mark m : chunks) {
					if (m.offset > pos) break;
					error = m.error;
				}
				return error;
			default:
				return false;
		}
	}

	private Color colorAt(final int pos) {
		final Color fg = getForeground(), bg = getBackground();
		final boolean dark = TextEditor.GuiUtils.isDark(bg);
		final Color base = isError(pos) ? (dark ? new Color(255, 95, 95) : new Color(200, 0, 0)) : fg;
		return pos < runStart.offset ? blend(base, bg, 0.5) : base;
	}

	private static Color blend(final Color a, final Color b, final double weightOfB) {
		final double wa = 1 - weightOfB;
		return new Color((int) (a.getRed() * wa + b.getRed() * weightOfB),
			(int) (a.getGreen() * wa + b.getGreen() * weightOfB),
			(int) (a.getBlue() * wa + b.getBlue() * weightOfB));
	}

	/** Position after {@code pos} where the color might change, capped at {@code max}. */
	private int nextBoundary(final int pos, final int max) {
		int next = max;
		if (runStart.offset > pos) next = Math.min(next, runStart.offset);
		if (content == Content.MIXED) {
			for (final Mark m : chunks) {
				if (m.offset > pos) {
					next = Math.min(next, m.offset);
					break;
				}
			}
		}
		return next;
	}

	/** Draws text [p0, p1) starting at x, switching colors as needed. Returns the end x. */
	private int drawColored(final Graphics g, int x, final int y, final int p0,
		final int p1, final TabExpander expander) throws BadLocationException
	{
		final Document doc = getDocument();
		final Segment s = new Segment();
		int pos = p0;
		while (pos < p1) {
			final int end = nextBoundary(pos, p1);
			doc.getText(pos, end - pos, s);
			g.setColor(colorAt(pos));
			x = Utilities.drawTabbedText(s, x, y, g, expander, pos);
			pos = end;
		}
		return x;
	}

	private class ConsoleAreaUI extends BasicTextAreaUI {

		@Override
		public View create(final Element elem) {
			if (getLineWrap()) {
				return new WrappedPlainView(elem, getWrapStyleWord()) {

					@Override
					protected int drawUnselectedText(final Graphics g, final int x,
						final int y, final int p0, final int p1) throws BadLocationException
					{
						return drawColored(g, x, y, p0, p1, this);
					}
				};
			}
			return new PlainView(elem) {

				@Override
				protected int drawUnselectedText(final Graphics g, final int x,
					final int y, final int p0, final int p1) throws BadLocationException
				{
					return drawColored(g, x, y, p0, p1, this);
				}
			};
		}
	}
}
