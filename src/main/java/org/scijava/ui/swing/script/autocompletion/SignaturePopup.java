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

package org.scijava.ui.swing.script.autocompletion;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import org.scijava.code.api.Completion;
import org.scijava.code.api.SignatureHelp;

/**
 * Shows the signatures of the call being typed (see
 * {@link org.scijava.code.api.CodeCompleter#signatureHelp}), e.g. all
 * overloads of {@code max} once {@code Math.max(} is typed: best fits first,
 * the parameter being typed in bold. It follows the caret through the call,
 * and closes once the caret leaves it, or on Escape.
 * <p>
 * (Accepting a completion shows RSyntaxTextArea's own parameter assistance
 * instead; this popup is for calls typed by hand.)
 * </p>
 *
 * @author Gabriel Selzer
 */
public class SignaturePopup {

	/** How long to wait before asking again for slow help, in milliseconds. */
	private static final int RETRY_DELAY = 400;

	private final JTextComponent textArea;
	private final Function<JTextComponent, SignatureHelp> help;
	private final KeyListener keys;
	private final CaretListener caret;
	private final FocusListener focus;
	private final Timer retry;
	private JWindow window;
	private JLabel label;

	/**
	 * @param textArea Where calls are typed.
	 * @param help Gets the signature help at the caret.
	 */
	public SignaturePopup(final JTextComponent textArea,
		final Function<JTextComponent, SignatureHelp> help)
	{
		this.textArea = textArea;
		this.help = help;
		retry = new Timer(RETRY_DELAY, e -> refresh(false));
		retry.setRepeats(false);
		keys = new KeyAdapter() {

			@Override
			public void keyTyped(final KeyEvent e) {
				final char c = e.getKeyChar();
				// NB: After the character is inserted.
				if (c == '(') SwingUtilities.invokeLater(() -> refresh(true));
			}

			@Override
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE && isShowing()) hide();
			}
		};
		caret = e -> {
			if (isShowing()) SwingUtilities.invokeLater(() -> refresh(false));
		};
		focus = new FocusAdapter() {

			@Override
			public void focusLost(final FocusEvent e) {
				hide();
			}
		};
	}

	/** Starts following the typing. */
	public void install() {
		textArea.addKeyListener(keys);
		textArea.addCaretListener(caret);
		textArea.addFocusListener(focus);
	}

	/** Stops following the typing. */
	public void uninstall() {
		textArea.removeKeyListener(keys);
		textArea.removeCaretListener(caret);
		textArea.removeFocusListener(focus);
		retry.stop();
		hide();
		if (window != null) window.dispose();
		window = null;
	}

	public boolean isShowing() {
		return window != null && window.isVisible();
	}

	public void hide() {
		if (window != null) window.setVisible(false);
	}

	/**
	 * Shows the help at the caret, or hides the popup if there is none.
	 *
	 * @param opening Whether a call was just opened (so that slow help is
	 *          asked for again, shortly).
	 */
	void refresh(final boolean opening) {
		if (!textArea.isShowing()) return;
		final SignatureHelp h = help.apply(textArea);
		if (h == null || h.isEmpty()) {
			hide();
			if (opening) retry.restart();
			return;
		}
		show(html(h), h.callStart());
	}

	/** Renders signatures as HTML, one per line, best fits first. */
	static String html(final SignatureHelp help) {
		final String dim = "<font color=\"" + SciJavaCompletionProvider
			.dimColor() + "\">";
		final StringBuilder sb = new StringBuilder("<html>");
		boolean first = true;
		for (final SignatureHelp.Signature s : help.signatures()) {
			if (!first) sb.append("<br>");
			first = false;
			final String sig = signature(s.callable(), help.activeParameter());
			switch (s.fit()) {
				case CONVERSION:
					sb.append(dim).append(sig).append("</font>");
					break;
				case MISMATCH:
					sb.append(dim).append("<s>").append(sig).append("</s></font>");
					break;
				default:
					sb.append(sig);
			}
		}
		return sb.append("</html>").toString();
	}

	/** Renders one signature, e.g. {@code max(double a, <b>double b</b>)}. */
	private static String signature(final Completion c, final int active) {
		String name = c.insertionText();
		if (name.endsWith("()")) name = name.substring(0, name.length() - 2);
		final StringBuilder sb = new StringBuilder(escape(name)).append('(');
		final List<Completion.Parameter> params = c.parameters();
		for (int i = 0; i < params.size(); i++) {
			if (i > 0) sb.append(", ");
			final Completion.Parameter p = params.get(i);
			final String type = p.type();
			final StringBuilder param = new StringBuilder();
			if (type != null) param.append(type.substring(type.lastIndexOf('.') +
				1));
			if (p.name() != null) {
				if (type != null) param.append(' ');
				param.append(p.name());
			}
			final String text = escape(param.toString());
			sb.append(i == active ? "<b>" + text + "</b>" : text);
		}
		sb.append(')');
		if (c.returnType() != null) sb.append(" → ").append(escape(c
			.returnType()));
		return sb.toString();
	}

	private void show(final String html, final int callStart) {
		final Window owner = SwingUtilities.getWindowAncestor(textArea);
		if (owner == null) return;
		if (window == null || window.getOwner() != owner) {
			if (window != null) window.dispose();
			window = new JWindow(owner);
			window.setFocusableWindowState(false);
			label = new JLabel();
			label.setOpaque(true);
			final Color bg = UIManager.getColor("ToolTip.background");
			final Color fg = UIManager.getColor("ToolTip.foreground");
			if (bg != null) label.setBackground(bg);
			if (fg != null) label.setForeground(fg);
			label.setFont(textArea.getFont());
			label.setBorder(BorderFactory.createCompoundBorder(UIManager.getBorder(
				"ToolTip.border") != null ? UIManager.getBorder("ToolTip.border")
					: BorderFactory.createLineBorder(Color.GRAY), BorderFactory
						.createEmptyBorder(2, 4, 2, 4)));
			window.getContentPane().add(label);
		}
		label.setText(html);
		window.pack();
		try {
			// Above the call's line, starting at its opening parenthesis.
			final Rectangle2D r = textArea.modelToView2D(Math.max(0, Math.min(
				callStart, textArea.getDocument().getLength())));
			final Point p = new Point((int) r.getX(), (int) r.getY());
			SwingUtilities.convertPointToScreen(p, textArea);
			int y = p.y - window.getHeight() - 2;
			if (y < 0) y = p.y + (int) r.getHeight() + 2;
			window.setLocation(p.x, y);
		}
		catch (final BadLocationException exc) {
			return;
		}
		final Rectangle visible = textArea.getVisibleRect();
		if (visible.isEmpty()) return;
		window.setVisible(true);
	}

	private static String escape(final String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
