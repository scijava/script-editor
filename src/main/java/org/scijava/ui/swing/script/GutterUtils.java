/*-
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

import java.awt.Color;

import javax.swing.text.BadLocationException;

import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.RTextArea;

import com.formdev.flatlaf.util.UIScale;

public class GutterUtils {

	private static final String CACHE_KEY = "scijava.bookmarkIcon";
	private final Gutter gutter;
	private final RTextArea textArea;

	GutterUtils(final Gutter gutter, final RTextArea textArea) {
		this.gutter = gutter;
		this.textArea = textArea;
		//gutter.setFoldIndicatorStyle(org.fife.ui.rtextarea.FoldIndicatorStyle.MODERN); // DEFAULT
	}

	private void updateBookmarkIcon() {
		final int height = gutter.getLineNumberFont().getSize();
		final Color color = GuiUtils.accentColor();
		// The icon only depends on these: skip the rebuild (and the bookmark restore) if unchanged
		final String key = height + ":" + color.getRGB();
		if (key.equals(gutter.getClientProperty(CACHE_KEY)) && gutter.getBookmarkIcon() != null) return;
		gutter.putClientProperty(CACHE_KEY, key);

		// this will clear existing bookmarks, so we'll need restore existing ones
		final GutterIconInfo[] stash = gutter.getBookmarks();
		gutter.setBookmarkIcon(Icons.bookmark(color, UIScale.unscale(height)));

		for (final GutterIconInfo info : stash) {
			try {
				gutter.toggleBookmark(textArea.getLineOfOffset(info.getMarkedOffset()));
			} catch (final BadLocationException ignored) {
				// do nothing
			}
		}
	}

	public static void updateIcons(final Gutter gutter, final RTextArea textArea) {
		final GutterUtils utils = new GutterUtils(gutter, textArea);
		utils.updateBookmarkIcon();
	}
}
