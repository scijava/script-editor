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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics2D;

import com.formdev.flatlaf.ui.FlatUIUtils;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;

import com.formdev.flatlaf.util.UIScale;

/**
 * An {@link ErrorStrip} that follows the UI scale factor. The stock strip uses
 * hard-coded pixel sizes (width, marker size and caret line thickness)
 */
class ScalableErrorStrip extends ErrorStrip {

	// Sizes of the stock strip, in unscaled pixels
	private static final int WIDTH = 14, MARKER_WIDTH = 10, MARKER_HEIGHT = 5, CARET_THICKNESS = 2;

	ScalableErrorStrip(final RSyntaxTextArea textArea) {
		super(textArea);
		// The stock strip repaints a region sized for unscaled markers
		textArea.addCaretListener(e -> {
			if (UIScale.getUserScaleFactor() > 1f) repaint();
		});
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(UIScale.scale(WIDTH), super.getPreferredSize().height);
	}

	@Override
	protected void addImpl(final Component comp, final Object constraints, final int index) {
		super.addImpl(comp, constraints, index);
		comp.setSize(UIScale.scale(MARKER_WIDTH), UIScale.scale(MARKER_HEIGHT)); // layout is null
	}

	@Override
	protected void paintCaretMarker(final Graphics2D g, final int width, final int height) {
		final int thickness = UIScale.scale(CARET_THICKNESS);
		final int center = height / 2 + (height & 1) + 1;
		g.fillRect(0, center - thickness / 2, width, thickness);
	}

	// Flat rounded markers without the stock beveled border
	@Override
	protected void paintParserNoticeMarker(final Graphics2D g, final ParserNotice notice, final int width,
			final int height) {
		final Color c = notice.getColor();
		FlatUIUtils.setRenderingHints(g);
		g.setColor(c == null ? Color.GRAY : c);
		g.fillRect(0, 0, width, height);
	}
}
