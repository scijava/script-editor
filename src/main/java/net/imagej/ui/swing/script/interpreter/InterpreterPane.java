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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import net.imagej.ui.swing.script.DefaultAutoImporters;
import net.miginfocom.swing.MigLayout;

import org.scijava.log.LogService;
import org.scijava.prefs.PrefService;
import org.scijava.script.DefaultScriptInterpreter;
import org.scijava.script.ScriptInterpreter;
import org.scijava.script.ScriptLanguage;
import org.scijava.script.ScriptService;

/**
 * A Swing UI pane for a language-specific script interpreter.
 * 
 * @author Curtis Rueden
 * @author Johannes Schindelin
 */
public class InterpreterPane {

	private final ScriptInterpreter interpreter;

	private final JSplitPane splitPane;
	private final OutputPane output;
	private final PromptPane prompt;

	/**
	 * Constructs an interpreter UI pane for a specific scripting language.
	 * 
	 * @param prefs service to use for persisting the history
	 * @param scriptService service to use for scripting
	 * @param language scripting language for which to construct a UI pane
	 * @param log service to use for logging
	 */
	public InterpreterPane(final PrefService prefs, final ScriptService scriptService,
		final ScriptLanguage language, final LogService log)
	{
		this(createInterpreter(prefs, scriptService, language), log);
	}

	/**
	 * Constructs an interpreter UI pane for a specific script interpreter.
	 * 
	 * @param interpreter script interpreter to use for script execution
	 * @param log service to use for logging
	 */
	public InterpreterPane(final ScriptInterpreter interpreter,
		final LogService log)
	{
		this.interpreter = interpreter;

		output = new OutputPane(log);
		final JScrollPane outputScroll = new JScrollPane(output);
		outputScroll.setPreferredSize(new Dimension(440, 400));

		final Writer writer = output.getOutputWriter();
		final ScriptContext context = interpreter.getEngine().getContext();
		context.setErrorWriter(writer);
		context.setWriter(writer);

		prompt = new PromptPane(interpreter, output);
		final JScrollPane promptScroll = new JScrollPane(prompt);

		final JButton clearButton = new JButton("Clear");
		clearButton.setToolTipText("Clears the text in the output pane.");
		clearButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				output.setText("");
			}
		});

		final JPanel bottomPane = new JPanel();
		bottomPane.setLayout(new MigLayout("", "[grow,fill][pref]",
			"[grow,fill,align top]"));
		bottomPane.add(promptScroll, "spany 2");
		bottomPane.add(clearButton, "w pref!, h pref!, wrap");

		final Object importGenerator =
				DefaultAutoImporters.getImportGenerator(log.getContext(), interpreter.getLanguage());
		if (importGenerator != null) {
			final JButton autoImportButton = new JButton("Auto-Import");
			autoImportButton.setToolTipText("Auto-imports common classes.");
			autoImportButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					try {
						interpreter.getEngine().eval(importGenerator.toString());
					}
					catch (ScriptException e1) {
						e1.printStackTrace(new PrintWriter(output.getOutputWriter()));
					}
					autoImportButton.setEnabled(false);
					prompt.requestFocus();
				}
			});
			bottomPane.add(autoImportButton, "w pref!, h pref!, wrap");
		}

		splitPane =
			new JSplitPane(JSplitPane.VERTICAL_SPLIT, outputScroll, bottomPane);
		splitPane.setResizeWeight(1);
	}

	public void dispose() throws IOException {
		output.close();
	}

	// -- InterpreterPane methods --

	/** Gets the associated script interpreter. */
	public ScriptInterpreter getInterpreter() {
		return interpreter;
	}

	/** Gets the pane's Swing UI component. */
	public JSplitPane getComponent() {
		return splitPane;
	}

	// -- Utility methods --

	public static ScriptInterpreter createInterpreter(
		final PrefService prefs, final ScriptService scriptService, final ScriptLanguage language)
	{
		return new DefaultScriptInterpreter(prefs, scriptService, language);
	}

}
