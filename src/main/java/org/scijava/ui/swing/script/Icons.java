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

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.icons.FlatAbstractIcon;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factory of the hand-made icons of the script editor. Glyph icons are
 * painted from SVG path data of Font Awesome Free (<a href="https://fontawesome.com">...</a>),
 * licensed CC BY 4.0. Remaining icons are painted with the foreground of the
 * component they are assigned to.
 */
final class Icons {

    private static final Color GREEN = new Color(0x629B70), GREEN_DARK = new Color(0x44774E);
	private static final Color RED = new Color(0xD46D71), RED_DARK = new Color(0xDF5663);

	private Icons() {
	}

	/**
	 * Green "play" triangle
	 */
	static Icon play(final boolean disabled) {
		return new GlyphIcon(
				"M187.2 100.9C174.8 94.1 159.8 94.4 147.6 101.6C135.4 108.8 128 121.9 128 136L128 504C128 518.1 135.5 531.2 147.6 538.4C159.7 545.6 174.8 545.9 187.2 539.1L523.2 355.1C536 348.1 544 334.6 544 320C544 305.4 536 291.9 523.2 284.9L187.2 100.9z",
				GREEN, GREEN_DARK, disabled);
	}

	/**
	 * Green "fast-forward" (double play) icon, for batch runs
	 */
	static Icon fastForward(final boolean disabled) {
		return new GlyphIcon(
				"M403.7 107.1C392.1 96 375 92.9 360.3 99.2C345.6 105.5 336 120 336 136L336 272.3L163.7 107.2C152.1 96 135 92.9 120.3 99.2C105.6 105.5 96 120 96 136L96 504C96 520 105.6 534.5 120.3 540.8C135 547.1 152.1 544 163.7 532.9L336 367.7L336 504C336 520 345.6 534.5 360.3 540.8C375 547.1 392.1 544 403.7 532.9L595.7 348.9C603.6 341.4 608 330.9 608 320C608 309.1 603.5 298.7 595.7 291.1L403.7 107.1z",
				GREEN, GREEN_DARK, disabled);
	}

	/**
	 * Red "stop" rounded square
	 */
	static Icon stop(final boolean disabled) {
		return new GlyphIcon(
				"M160 96L480 96C515.3 96 544 124.7 544 160L544 480C544 515.3 515.3 544 480 544L160 544C124.7 544 96 515.3 96 480L96 160C96 124.7 124.7 96 160 96z",
				RED, RED_DARK, disabled);
	}

	/**
	 * Neutral "trash can" icon, for the Clear button
	 */
	static Icon trash(final boolean disabled) {
		return new GlyphIcon(
				"M232.7 69.9C237.1 56.8 249.3 48 263.1 48L377 48C390.8 48 403 56.8 407.4 69.9L416 96L512 96C529.7 96 544 110.3 544 128C544 145.7 529.7 160 512 160L128 160C110.3 160 96 145.7 96 128C96 110.3 110.3 96 128 96L224 96L232.7 69.9zM128 208L512 208L512 512C512 547.3 483.3 576 448 576L192 576C156.7 576 128 547.3 128 512L128 208zM216 272C202.7 272 192 282.7 192 296L192 488C192 501.3 202.7 512 216 512C229.3 512 240 501.3 240 488L240 296C240 282.7 229.3 272 216 272zM320 272C306.7 272 296 282.7 296 296L296 488C296 501.3 306.7 512 320 512C333.3 512 344 501.3 344 488L344 296C344 282.7 333.3 272 320 272zM424 272C410.7 272 400 282.7 400 296L400 488C400 501.3 410.7 512 424 512C437.3 512 448 501.3 448 488L448 296C448 282.7 437.3 272 424 272z",
				new Color(0x6E6E6E), new Color(0xA9A9A9), disabled);
	}

	/**
	 * Light bulb icon.
	 *
	 * @param height the height in unscaled pixels
	 */
	static Icon bulb(final int height) {
		final Color c = GuiUtils.secondaryColor();
		return new GlyphIcon(
				"M424.5 355.1C449 329.2 464 294.4 464 256C464 176.5 399.5 112 320 112"
						+ "C240.5 112 176 176.5 176 256C176 294.4 191 329.2 215.5 355.1C236.8 377.5 260.4 409.1 268.8 448"
						+ "L371.2 448C379.6 409 403.2 377.5 424.5 355.1z"
						+ "M459.3 388.1C435.7 413 416 443.4 416 477.7L416 496C416 540.2 380.2 576 336 576L304 576"
						+ "C259.8 576 224 540.2 224 496L224 477.7C224 443.4 204.3 413 180.7 388.1C148 353.7 128 307.2 128 256"
						+ "C128 150 214 64 320 64C426 64 512 150 512 256C512 307.2 492 353.7 459.3 388.1z"
						+ "M272 248C272 261.3 261.3 272 248 272C234.7 272 224 261.3 224 248C224 199.4 263.4 160 312 160"
						+ "C325.3 160 336 170.7 336 184C336 197.3 325.3 208 312 208C289.9 208 272 225.9 272 248z",
				c, c, false, 128, 64, 384, 512, height);
	}

	/**
	 * REPL icon: a prompt (&gt;_) inside a window
	 */
	static Icon repl() {
		return new WindowIcon() {
			@Override
			void paintContent(final Graphics2D g, final boolean selected) {
				g.drawLine(4, 5, 6, 7); // chevron
				g.drawLine(6, 7, 4, 9);
				g.drawLine(8, 10, 11, 10); // cursor
			}
		};
	}

	/**
	 * Window with a pane on its right side, filled when the button is selected
	 */
	static Icon rightPane() {
		return new WindowIcon() {
			@Override
			void paintContent(final Graphics2D g, final boolean selected) {
				if (selected) g.fillRect(10, 3, 4, 10);
				else g.drawLine(9, 3, 9, 12);
			}
		};
	}

	/**
	 * Window with a pane below, filled when the button is selected
	 */
	static Icon bottomPane() {
		return new WindowIcon() {
			@Override
			void paintContent(final Graphics2D g, final boolean selected) {
				if (selected) g.fillRect(2, 9, 12, 4);
				else g.drawLine(2, 8, 13, 8);
			}
		};
	}

	/**
	 * Window with a left pane, filled when the button is selected
	 */
	static Icon sidebar() {
		return new WindowIcon() {
			@Override
			void paintContent(final Graphics2D g, final boolean selected) {
				if (selected) g.fillRect(2, 3, 4, 10);
				else g.drawLine(6, 3, 6, 12);
			}
		};
	}

	/**
	 * Chevron pointing right (collapsed) or down (expanded)
	 */
	static Icon chevron(final boolean down) {
		return new ChevronIcon(down);
	}

	/**
	 * Magnifier icon
	 */
	static Icon magnifier() {
		return new MagnifierIcon();
	}

	/**
	 * Icon painted from SVG path data (absolute M, L, C and Z commands)
	 */
	private static class GlyphIcon extends FlatAbstractIcon {

		private final Path2D path;
		private final boolean disabled;
		private final Color light, dark;

		/**
		 * Square 16 pixel icon from a 640x640 view box
		 */
		GlyphIcon(final String svgPath, final Color light, final Color dark, final boolean disabled) {
			this(svgPath, light, dark, disabled, 0, 0, 640, 640, 16);
		}

		/**
		 * Icon of the given height showing the (x, y, w, h) region of the view box
		 */
		GlyphIcon(final String svgPath, final Color light, final Color dark, final boolean disabled,
				  final double x, final double y, final double w, final double h, final int height) {
			super((int) Math.round(height * w / h), height, null);
			this.path = parse(svgPath, height / h, x, y);
			this.light = light;
			this.dark = dark;
			this.disabled = disabled;
		}

		@Override
		protected void paintIcon(final Component c, final Graphics2D g) {
			Color color = FlatLaf.isLafDark() ? dark : light;
			if (disabled) {
				final Color d = UIManager.getColor("Button.disabledText");
				color = (d != null) ? d : Color.GRAY;
			}
			g.setColor(color);
			g.fill(path);
		}

		private static Path2D parse(final String d, final double k, final double x0, final double y0) {
			final Matcher m = Pattern.compile("[MLCZz]|-?\\d*\\.?\\d+").matcher(d);
			final List<String> tokens = new ArrayList<>();
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
				final double[] v = new double[cmd == 'C' ? 6 : 2];
				for (int j = 0; j < v.length; j++)
					v[j] = k * (Double.parseDouble(tokens.get(i + j)) - ((j % 2 == 0) ? x0 : y0));
				i += v.length;
				switch (cmd) {
					case 'M' -> {
						p.moveTo(v[0], v[1]);
						cmd = 'L'; // further pairs are line segments
					}
					case 'L' -> p.lineTo(v[0], v[1]);
					case 'C' -> p.curveTo(v[0], v[1], v[2], v[3], v[4], v[5]);
					default -> throw new IllegalArgumentException("Unsupported path command: " + cmd);
				}
			}
			return p;
		}
	}

	/**
	 * Rounded window outline (16x16), painted with the foreground of its component
	 */
	private abstract static class WindowIcon extends FlatAbstractIcon {

		WindowIcon() {
			super(16, 16, null);
		}

		/**
		 * Paints the inner details. The component being selected means "pane shown"
		 */
		abstract void paintContent(Graphics2D g, boolean selected);

		@Override
		protected void paintIcon(final Component c, final Graphics2D g) {
			g.setColor(c.getForeground());
			g.drawRoundRect(1, 2, 13, 11, 3, 3);
			paintContent(g, c instanceof AbstractButton b && b.isSelected());
		}
	}

	private record ChevronIcon(boolean down) implements Icon {

		@Override
		public int getIconWidth() {
			return UIScale.scale(10);
		}

		@Override
		public int getIconHeight() {
			return UIScale.scale(10);
		}

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			final Graphics2D g2 = (Graphics2D) g.create();
			try {
				FlatUIUtils.setRenderingHints(g2);
				g2.setColor(c.getForeground());
				g2.setStroke(new BasicStroke(UIScale.scale(1.3f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				final float s = getIconWidth();
				final float a = s * .3f; // inset
				final Path2D.Float p = new Path2D.Float();
				if (down) {
					p.moveTo(x + a * .5f, y + s * .35f);
					p.lineTo(x + s / 2, y + s * .65f);
					p.lineTo(x + s - a * .5f, y + s * .35f);
				} else {
					p.moveTo(x + s * .35f, y + a * .5f);
					p.lineTo(x + s * .65f, y + s / 2);
					p.lineTo(x + s * .35f, y + s - a * .5f);
				}
				FlatUIUtils.drawShapePure(g2, p);
			} finally {
				g2.dispose();
			}
		}
	}

	private static final class MagnifierIcon implements Icon {

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			final Graphics2D g2 = (Graphics2D) g.create();
			try {
				FlatUIUtils.setRenderingHints(g2);
				g2.setColor(c.getForeground());
				g2.setStroke(new BasicStroke(UIScale.scale(1.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				final float s = getIconWidth();
				FlatUIUtils.drawShapePure(g2, new Ellipse2D.Float(x + .1f * s, y + .1f * s, .6f * s, .6f * s));
				FlatUIUtils.drawShapePure(g2, new Line2D.Float(x + .62f * s, y + .62f * s, x + .88f * s, y + .88f * s));
			} finally {
				g2.dispose();
			}
		}

		@Override
		public int getIconWidth() {
			return UIScale.scale(16);
		}

		@Override
		public int getIconHeight() {
			return UIScale.scale(16);
		}
	}
}
