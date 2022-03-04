/*-
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.text.BadLocationException;

import org.fife.ui.rtextarea.FoldIndicator;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;

public class GutterUtils {

	private final Gutter gutter;

	GutterUtils(final Gutter gutter) {
		this.gutter = gutter;
		gutter.setSpacingBetweenLineNumbersAndFoldIndicator(0);
	}

	private void updateFoldIcons() {
		int size;
		try {
			size = (int) new FoldIndicator(null).getPreferredSize().getWidth();
		} catch (final Exception | Error ignored) {
			size = 12; // FoldIndicator#WIDTH
		}
		if (size < 8)
			size = 8;  // the default foldicon size in FoldIndicator
		final int fontSize = gutter.getLineNumberFont().getSize();
		if (size > fontSize)
			size = fontSize;
		gutter.setFoldIcons(new FoldIcon(true, size), new FoldIcon(false, size));
	}

	private ImageIcon getBookmarkIcon() {
		final int size = gutter.getLineNumberFont().getSize();
		final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = image.createGraphics();
		graphics.setColor(gutter.getLineNumberColor());
		graphics.fillRect(0, 0, size, size);
		graphics.setXORMode(gutter.getBorderColor());
		graphics.drawRect(0, 0, size - 1, size - 1);
		image.flush();
		return new ImageIcon(image);
	}

	private void updateBookmarkIcon() {
		// this will clear existing bookmarks, so we'll need restore existing ones
		final GutterIconInfo[] stash = gutter.getBookmarks();
		gutter.setBookmarkIcon(getBookmarkIcon());

		for (final GutterIconInfo info : stash) {
			try {
				gutter.toggleBookmark(info.getMarkedOffset());
			} catch (final BadLocationException ignored) {
				// do nothing
			}
		}
	}

	public static void updateIcons(final Gutter gutter) {
		final GutterUtils utils = new GutterUtils(gutter);
		utils.updateFoldIcons();
		utils.updateBookmarkIcon();
	}

	private class FoldIcon implements Icon {

		private final boolean collapsed;
		private final Color background;
		private final Color foreground;
		private final int size;

		FoldIcon(final boolean collapsed, final int size) {
			this.collapsed = collapsed;
			this.background = gutter.getBorderColor();
			this.foreground = gutter.getActiveLineRangeColor();
			this.size = size;
		}

		@Override
		public int getIconHeight() {
			return size;
		}

		@Override
		public int getIconWidth() {
			return size;
		}

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			g.setColor(background);
			g.fillRect(x, y, size, size);
			g.setColor(foreground);
			g.drawRect(x, y, size, size);
			g.drawLine(x + 2, y + size / 2, x + 2 + size / 2, y + size / 2);
			if (collapsed) {
				g.drawLine(x + size / 2, y + 2, x + size / 2, y + size / 2 + 2);
			}
		}
	}
}
