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

package net.imagej.ui.swing.script;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Stack;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.scijava.prefs.PrefService;

/**
 * JMenu holding recently opened files (stored in the
 * <code>"script.editor.recent"</code> preference).
 *
 * @author Johannes Schindelin
 * @author Jonathan Hale
 */
public class RecentFilesMenuItem extends JMenu {

	/** Constant for maximum amount of recent files shown in recent files menu */
	protected final int maxCount = 10;

	/** Constant for maximum length of a filepath shown in recent files menu */
	protected final int maxLength = 35;

	protected final LinkedList<String> recentFiles = new LinkedList<>();

	protected final static String RECENT_FILES_PREFS_PREFIX =
		"script.editor.recent";

	private final PrefService prefService;

	private final TextEditor editor;

	/**
	 * Constructor.
	 *
	 * @param prefService Service to use for managing the preferences.
	 */
	public RecentFilesMenuItem(final PrefService prefService,
		final TextEditor editor)
	{
		super("Open Recent");
		this.editor = editor;
		this.prefService = prefService;

		// get up to 10 (maxCount) most recentently opened files
		final Stack<String> prefs = new Stack<>();
		for (int i = 1; i <= maxCount; i++) {
			final String item =
				prefService.get(getClass(), RECENT_FILES_PREFS_PREFIX + i, null);
			if (item == null) break;
			prefs.push(item);
		}

		if (prefs.empty()) setEnabled(false);
		else while (!prefs.empty())
			add(prefs.pop());
	}

	@Override
	public JMenuItem add(final String path) {
		setEnabled(true);

		// remove identical entries, if any
		int i = 0;
		final Iterator<String> iter = recentFiles.iterator();
		while (iter.hasNext()) {
			final String item = iter.next();
			if (item.equals(path)) {
				if (i == 0) return getItem(i);
				iter.remove();
				remove(i);
			}
			else i++;
		}

		// keep the maximum count
		if (recentFiles.size() + 1 >= maxCount) {
			recentFiles.removeLast(); // least recent
			remove(maxCount - 2);
		}

		recentFiles.add(0, path);

		// persist
		i = 1;
		for (final String item : recentFiles) {
			prefService.put(getClass(), RECENT_FILES_PREFS_PREFIX + i, item);
			i++;
		}

		// add the menu item
		String label = path;
		if (path.length() > maxLength) label =
			"..." + path.substring(path.length() - maxLength + 3);
		insert(label, 0);
		final JMenuItem result = getItem(0);
		result.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				editor.open(new File(path));
			}
		});

		return result;
	}
}
