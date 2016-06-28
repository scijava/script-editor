/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
 * Wisconsin-Madison, Max Planck Institute of Molecular Cell Biology and Genetics,
 * and others.
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

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

/**
 * TODO
 *
 * @author Johannes Schindelin
 */
public class BookmarkDialog extends JDialog implements ActionListener {

	JList<Bookmark> list;
	JButton okay, cancel;

	public BookmarkDialog(final Frame owner, final Vector<Bookmark> bookmarks) {
		super(owner, "Bookmarks", true);

		getContentPane().setLayout(
			new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

		list = new JList<>(bookmarks);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2) {
					final int index = list.locationToIndex(e.getPoint());
					bookmarks.get(index).setCaret();
					BookmarkDialog.this.dispose();
				}
			}
		});
		getContentPane().add(list);

		okay = new JButton("OK");
		okay.addActionListener(this);
		cancel = new JButton("Cancel");
		cancel.addActionListener(this);
		final JPanel panel = new JPanel();
		panel.add(okay);
		panel.add(cancel);
		getContentPane().add(panel);

		final KeyListener keyListener = new KeyAdapter() {

			@Override
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					jumpToSelectedBookmark();
					dispose();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) dispose();
			}
		};
		getContentPane().addKeyListener(keyListener);
		list.addKeyListener(keyListener);
		okay.addKeyListener(keyListener);

		pack();
		setLocationRelativeTo(owner);
	}

	public boolean jumpToSelectedBookmark() {
		final Bookmark bookmark = list.getSelectedValue();
		if (bookmark == null) return false;
		bookmark.setCaret();
		return true;
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final Object source = e.getSource();
		if (source == cancel) dispose();
		else if (source == okay) {
			jumpToSelectedBookmark();
			dispose();
		}
	}
}
