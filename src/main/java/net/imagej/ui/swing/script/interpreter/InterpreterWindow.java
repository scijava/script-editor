/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2014 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
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

package net.imagej.ui.swing.script.interpreter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;
import org.scijava.script.ScriptLanguage;
import org.scijava.script.ScriptService;

/**
 * The main interpreter window.
 * 
 * @author Curtis Rueden
 * @author Johannes Schindelin
 */
public class InterpreterWindow extends JFrame {

	@Parameter
	private PrefService prefs;

	@Parameter
	private ScriptService scriptService;

	@Parameter
	private LogService log;

	private final List<InterpreterPane> tabs = new ArrayList<InterpreterPane>();
	private final JTabbedPane tabbedPane;

	/** Constructs the scripting interpreter window. */
	public InterpreterWindow(final Context context) {
		super("Scripting Interpreter");
		context.inject(this);

		tabbedPane = new JTabbedPane();
		setContentPane(tabbedPane);

		for (final ScriptLanguage language : languages()) {
			final String name = language.getLanguageName();
			final InterpreterPane tab =
				new InterpreterPane(prefs, scriptService, language, log);
			tabs.add(tab);
			tabbedPane.add(name, tab.getComponent());
		}

		// read in interpreter histories, etc.
		readState();

		pack();

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
	}

	@Override
	public void dispose() {
		// write out interpreter histories, etc., when frame goes away
		writeState();
		for (final InterpreterPane tab : tabs) try {
			tab.dispose();
		} catch (final Exception e) {
			e.printStackTrace();
		}
		super.dispose();
	}

	// -- Helper methods --

	/** Gets the list of available scripting languages, sorted by name. */
	private List<ScriptLanguage> languages() {
		final List<ScriptLanguage> languages =
			new ArrayList<ScriptLanguage>(scriptService.getLanguages());
		Collections.sort(languages, new Comparator<ScriptLanguage>() {

			@Override
			public int compare(final ScriptLanguage l1, final ScriptLanguage l2) {
				return l1.getLanguageName().compareTo(l2.getLanguageName());
			}

		});
		return languages;
	}

	/**
	 * Reads in persisted state, including the last active tab, as well as the
	 * history of each interpreter.
	 */
	private void readState() {
		setActiveLanguage(prefs.get(InterpreterWindow.class, "language"));
		for (final InterpreterPane tab : tabs) {
			tab.getInterpreter().readHistory();
		}
	}

	/**
	 * Writes out persisted state, including the currently active tab, as well as
	 * the history of each interpreter.
	 */
	private void writeState() {
		final String active = getActiveLanguage();
		if (active != null) prefs.put(InterpreterWindow.class, "language", active);
		for (final InterpreterPane tab : tabs) {
			tab.getInterpreter().writeHistory();
		}
	}

	/** Gets the name of the active language tab. */
	private String getActiveLanguage() {
		final int selected = tabbedPane.getSelectedIndex();
		if (selected < 0) return null;
		final InterpreterPane tab = tabs.get(selected);
		return tab.getInterpreter().getLanguage().getLanguageName();
	}

	/** Sets the active language tab by name. */
	private void setActiveLanguage(final String languageName) {
		for (int i = 0; i < tabs.size(); i++) {
			final InterpreterPane tab = tabs.get(i);
			final String name = tab.getInterpreter().getLanguage().getLanguageName();
			if (name.equals(languageName)) {
				tabbedPane.setSelectedIndex(i);
				break;
			}
		}
	}

}
