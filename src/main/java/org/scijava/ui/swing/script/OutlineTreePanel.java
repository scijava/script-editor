/*
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
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.TreeNode;

import org.fife.rsta.ac.AbstractSourceTree;
import org.fife.rsta.ac.java.tree.JavaOutlineTree;
import org.fife.rsta.ac.js.tree.JavaScriptOutlineTree;
import org.fife.rsta.ac.xml.tree.XmlOutlineTree;
import org.scijava.script.ScriptLanguage;

/**
 * Convenience class for displaying a {@link AbstractSourceTree}
 *
 * @author Tiago Ferreira
 */
class OutlineTreePanel extends JScrollPane {

	private static final long serialVersionUID = -710040159139542578L;
	private AbstractSourceTree sourceTree;
	private final Color placeholdColor;
	private float fontSize;

	OutlineTreePanel(final TextEditor editor) {
		super();
		fontSize = getFont().getSize();
		setViewportView(new UnsupportedLangTree());
		placeholdColor = TextEditor.getDisabledComponentColor();

	}

	protected void refreshSourceTree(final EditorPane pane) {
		if (!isVisible())
			return;
		if (sourceTree != null) {
			sourceTree.uninstall();
		}
		final ScriptLanguage sLanguage = pane.getCurrentLanguage();
		final String language = (sLanguage == null) ? "" : sLanguage.getLanguageName();
		switch (language) {
		case "Java":
			sourceTree = new JavaOutlineTree();
			break;
		case "JavaScript":
			sourceTree = new JavaScriptOutlineTree();
			break;
		default:
			if (EditorPane.SYNTAX_STYLE_XML.equals(pane.getSyntaxEditingStyle())) {
				sourceTree = new XmlOutlineTree();
			} else
				sourceTree = null;
			break;
		}
		fontSize = pane.getFontSize();
		if (sourceTree == null) {
			setViewportView(new UnsupportedLangTree(pane));
		} else {
			sourceTree.setFont(sourceTree.getFont().deriveFont(fontSize));
			sourceTree.listenTo(pane);
			setViewportView(sourceTree);
			setPopupMenu(sourceTree, pane);
		}
		revalidate();
	}

	private void setPopupMenu(final JTree tree, final EditorPane pane) {
		final JPopupMenu popup = new JPopupMenu();
		final JMenuItem jmi = new JMenuItem("Refresh");
		jmi.addActionListener(e -> refreshSourceTree(pane));
		popup.add(jmi);
		setComponentPopupMenu(popup);
	}

	private class UnsupportedLangTree extends JTree {

		private static final long serialVersionUID = 1L;
		private static final String HOLDER = "Outline not available... "
				+ "(Currently, only Java, JS, & XML are supported)";

		public UnsupportedLangTree() {
			super((TreeNode) null);
		}

		public UnsupportedLangTree(EditorPane pane) {
			this();
			setPopupMenu(this, pane);
		}

		@Override
		protected void paintComponent(final java.awt.Graphics g) {
			super.paintComponent(g);
			final Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(placeholdColor);
			final Insets i = getInsets();
			float yTop = i.top;
			final int w = getWidth() - i.left - i.right;
			final int lastIndex = HOLDER.length();
			final AttributedString ac = new AttributedString(HOLDER);
			ac.addAttribute(TextAttribute.SIZE, fontSize, 0, lastIndex);
			ac.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, 0, lastIndex);
			final AttributedCharacterIterator aci = ac.getIterator();
			final FontRenderContext frc = g2.getFontRenderContext();
			final LineBreakMeasurer lbm = new LineBreakMeasurer(aci, frc);
			while (lbm.getPosition() < aci.getEndIndex()) {
				// see https://stackoverflow.com/a/41118280s
				final TextLayout tl = lbm.nextLayout(w);
				final float xPos = (float) (i.left + ((getWidth() - tl.getBounds().getWidth()) / 2));
				final float yPos = (float) (yTop + ((getHeight() - tl.getBounds().getHeight()) / 2));
				tl.draw(g2, xPos, yPos + tl.getAscent());
				yTop += tl.getDescent() + tl.getLeading() + tl.getAscent();
			}
			g2.dispose();
		}

	}
}
