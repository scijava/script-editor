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
	private float fontSize;
	private boolean sorted;
	private boolean major;

	OutlineTreePanel() {
		super();
		fontSize = getFont().getSize();
		setViewportView(new UnsupportedLangTree());
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
			jmi.addActionListener(e -> GuiUtils.collapseAllTreeNodes(tree));
			popup.add(jmi);
			jmi = new JMenuItem("Expand All");
			jmi.addActionListener(e -> GuiUtils.expandAllTreeNodes(tree));
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
				try {
					sorted = jcmi2.isSelected();
					((AbstractSourceTree) tree).setSorted(sorted); // will refresh
				} catch (final Throwable throwable) {
					GuiUtils.error(OutlineTreePanel.this, "Outline could not be sorted.");
					jcmi2.setSelected(sorted = false);
				}
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

		public UnsupportedLangTree() {
			super((TreeNode) null);
			setFont(getFont().deriveFont(fontSize));
		}

		public UnsupportedLangTree(final EditorPane pane) {
			this();
			setPopupMenu(this, pane);
		}

		@Override
		protected void paintComponent(final java.awt.Graphics g) {
			super.paintComponent(g);
			GuiUtils.paintEmptyHint(g, this, "Outline not available",
					"Currently, only Java, JS, and XML are supported");
		}

	}
}
