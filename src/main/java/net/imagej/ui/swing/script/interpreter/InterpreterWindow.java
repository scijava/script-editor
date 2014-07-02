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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.script.ScriptContext;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import org.scijava.log.LogService;
import org.scijava.prefs.PrefService;
import org.scijava.script.DefaultScriptInterpreter;
import org.scijava.script.ScriptInterpreter;
import org.scijava.script.ScriptLanguage;
import org.scijava.script.ScriptService;

/**
 * The main interpreter window.
 * 
 * @author Johannes Schindelin
 */
public class InterpreterWindow extends JFrame {

	private static final long serialVersionUID = 1L;

	private final static String NO_LANGUAGE = "<None>";

	private final JSplitPane split;
	private final JComboBox languageChoice;
	private final JTextArea prompt;
	private final OutputPane output;
	private Writer writer;

	private final PrefService prefs;
	private final ScriptService scriptService;
	private final Map<String, ScriptLanguage> languages =
		new TreeMap<String, ScriptLanguage>();
	private final Map<String, ScriptInterpreter> interpreters =
		new HashMap<String, ScriptInterpreter>();

	private ScriptInterpreter interpreter;

	/**
	 * Constructs the window.
	 * 
	 * @param scriptService the script service
	 */
	public InterpreterWindow(final PrefService prefs, final ScriptService scriptService, final LogService log) {
		this.prefs = prefs;
		this.scriptService = scriptService;

		for (final ScriptLanguage language : scriptService.getLanguages()) {
			languages.put(language.getLanguageName(), language);
		}

		split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		output = new OutputPane(log);
		final Dimension outputDimensions = new Dimension(800, 600);
		output.setPreferredSize(outputDimensions);
		split.add(output);
		prompt = new Prompt(this, output);
		prompt.setEnabled(false);
		split.add(prompt);
		getContentPane().add(split, BorderLayout.SOUTH);
		languageChoice = new JComboBox(getLanguageNames());
		languageChoice.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				final String name = languageChoice.getSelectedItem().toString();
				selectLanguage(name);
				if (prompt.isEnabled()) {
					prompt.requestFocusInWindow();
				}
			}

		});
		getContentPane().add(languageChoice, BorderLayout.NORTH);
		pack();

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		addFocusListener(new FocusListener() {

			@Override
			public void focusGained(FocusEvent arg0) {
				interpreter.readHistory();
			}

			@Override
			public void focusLost(FocusEvent arg0) {
				interpreter.writeHistory();
			}

		});
	}

	@Override
	public void dispose() {
		try {
			writer.close();
		} catch (final Exception e) {
			e.printStackTrace();
		}
		super.dispose();
	}

	private Object[] getLanguageNames() {
		final List<Object> names = new ArrayList<Object>();
		names.add(NO_LANGUAGE);
		names.addAll(languages.keySet());
		return names.toArray();
	}

	private synchronized void selectLanguage(String name) {
		if (NO_LANGUAGE.equals(name)) {
			prompt.setEnabled(false);
			interpreter = null;
			return;
		}
		interpreter = interpreters.get(name);
		if (interpreter == null) {
			final ScriptLanguage language = languages.get(name);
			if (language == null) {
				prompt.setEnabled(false);
				throw new RuntimeException("Internal error: cannot resolve language: " + name);
			}
			interpreter = new DefaultScriptInterpreter(prefs, scriptService, language);
			interpreters.put(name, interpreter);
		}
		if (writer != null) try {
			writer.close();
		} catch (final Exception e) {
			e.printStackTrace();
		}
		writer = output.getOutputWriter();
		final ScriptContext context = interpreter.getEngine().getContext();
		context.setErrorWriter(writer);
		context.setWriter(writer);
		prompt.setEnabled(true);
	}

	public ScriptInterpreter getInterpreter() {
		return interpreter;
	}

}
