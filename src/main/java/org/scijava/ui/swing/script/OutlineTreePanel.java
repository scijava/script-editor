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
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.swing.JCheckBoxMenuItem;
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
	private boolean sorted;
	private boolean major;

	OutlineTreePanel() {
		super();
		fontSize = getFont().getSize();
		setViewportView(new UnsupportedLangTree());
		placeholdColor = TextEditor.GuiUtils.getDisabledComponentColor();
	}

	protected void rebuildSourceTree(final EditorPane pane) {
		if (!isVisible())
			return;
		if (sourceTree != null) {
			sourceTree.uninstall();
		}
		final ScriptLanguage sLanguage = pane.getCurrentLanguage();
		final String language = (sLanguage == null) ? "" : sLanguage.getLanguageName();
		switch (language) {
		case "Java":
		//case "BeanShell":
			sourceTree = new JavaOutlineTree(sorted);
			break;
		case "JavaScript":
			sourceTree = new JavaScriptOutlineTree(sorted);
			break;
		default:
			if (EditorPane.SYNTAX_STYLE_XML.equals(pane.getSyntaxEditingStyle())) {
				sourceTree = new XmlOutlineTree(sorted);
			} else
				sourceTree = null;
			break;
		}
		fontSize = pane.getFontSize();
		if (sourceTree == null) {
			setViewportView(new UnsupportedLangTree(pane));
		} else {
			sourceTree.setShowMajorElementsOnly(major);
			sourceTree.setFont(sourceTree.getFont().deriveFont(fontSize));
			sourceTree.listenTo(pane);
			setViewportView(sourceTree);
			setPopupMenu(sourceTree, pane);
		}
		revalidate();
	}

	private void setPopupMenu(final JTree tree, final EditorPane pane) {
		final JPopupMenu popup = new JPopupMenu();
		JMenuItem jmi;
		if (tree instanceof AbstractSourceTree) {
			jmi = new JMenuItem("Collapse All");
			jmi.addActionListener(e -> TextEditor.GuiUtils.collapseAllTreeNodes(tree));
			popup.add(jmi);
			jmi = new JMenuItem("Expand All");
			jmi.addActionListener(e -> TextEditor.GuiUtils.expandAllTreeNodes(tree));
			popup.add(jmi);
			popup.addSeparator();
			final JCheckBoxMenuItem jcmi1 = new JCheckBoxMenuItem("Hide 'Minor' Elements", major);
			jcmi1.setToolTipText("Whether non-proeminent elements (e.g., local variables) should be displayed");
			jcmi1.addItemListener(e -> {
				major = jcmi1.isSelected();
				((AbstractSourceTree) tree).setShowMajorElementsOnly(major);
				((AbstractSourceTree) tree).refresh();
			});
			popup.add(jcmi1);
			final JCheckBoxMenuItem jcmi2 = new JCheckBoxMenuItem("Sort Elements", sorted);
			jcmi2.addItemListener(e -> {
				sorted = jcmi2.isSelected();
				((AbstractSourceTree) tree).setSorted(sorted); // will refresh
			});
			popup.add(jcmi2);
			popup.addSeparator();
		}
		jmi = new JMenuItem("Rebuild");
		jmi.addActionListener(e -> rebuildSourceTree(pane));
		popup.add(jmi);
		tree.setComponentPopupMenu(popup);
	}

	private class UnsupportedLangTree extends JTree {

		private static final long serialVersionUID = 1L;
		private static final String HOLDER = "Outline not available... "
				+ "(Currently, only Java, JS, & XML are supported)";

		public UnsupportedLangTree() {
			super((TreeNode) null);
		}

		public UnsupportedLangTree(final EditorPane pane) {
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
