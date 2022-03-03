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
