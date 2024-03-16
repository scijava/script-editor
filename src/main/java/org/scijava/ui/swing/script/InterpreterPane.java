/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2024 SciJava developers.
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

import java.awt.Dimension;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.border.EmptyBorder;

import net.miginfocom.swing.MigLayout;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.script.ScriptREPL;
import org.scijava.widget.UIComponent;

/**
 * A Swing UI pane for the SciJava scripting REPL.
 *
 * @author Curtis Rueden
 * @author Johannes Schindelin
 */
public class InterpreterPane implements UIComponent<JComponent> {

	private final ScriptREPL repl;

	private final JSplitPane mainPane;

	private final OutputPane output;
	private final PromptPane prompt;
	private final VarsPane vars;

	@Parameter(required = false)
	private LogService log;

	/**
	 * Constructs an interpreter UI pane for a SciJava scripting REPL.
	 *
	 * @param context The SciJava application context to use.
	 */
	public InterpreterPane(final Context context) {
		this(context, null);
	}

	/**
	 * Constructs an interpreter UI pane for a SciJava scripting REPL, with a
	 * given language preference.
	 *
	 * @param context The SciJava application context to use.
	 * @param languagePreference The given language to use, or null to fall back
	 *          to the default.
	 */
	public InterpreterPane(final Context context,
		final String languagePreference)
	{
		context.inject(this);
		output = new OutputPane(log);
		final JScrollPane outputScroll = new JScrollPane(output);
		outputScroll.setPreferredSize(new Dimension(440, 400));

		repl = new ScriptREPL(context, languagePreference, //
			output.getOutputStream());
		repl.initialize();

		final Writer writer = output.getOutputWriter();
		final ScriptContext ctx = repl.getInterpreter().getEngine().getContext();
		ctx.setErrorWriter(writer);
		ctx.setWriter(writer);

		vars = new VarsPane(context, repl);
		vars.setBorder(new EmptyBorder(0, 0, 8, 0));

		prompt = new PromptPane(repl, vars, output) {
			@Override
			public void quit() {
				dispose();
			}
		};
		final JScrollPane promptScroll = new JScrollPane(prompt.getComponent());

		final JButton clearButton = new JButton("Clear");
		clearButton.setToolTipText("Clears the text in the output pane.");
		clearButton.addActionListener(e -> output.setText(""));

		final JPanel bottomPane = new JPanel();
		bottomPane.setLayout(new MigLayout("", "[grow,fill][pref]",
			"[grow,fill,align top]"));
		bottomPane.add(promptScroll, "spany 2");
		bottomPane.add(clearButton, "w pref!, h pref!, wrap");

		final Object importGenerator = DefaultAutoImporters.getImportGenerator(
			log.getContext(), repl.getInterpreter().getLanguage());
		if (importGenerator != null) {
			final JButton autoImportButton = new JButton("Auto-Import");
			autoImportButton.setToolTipText("Auto-imports common classes.");
			autoImportButton.addActionListener(e -> {
				try {
					repl.getInterpreter().getEngine().eval(importGenerator.toString());
				}
				catch (final ScriptException e1) {
					e1.printStackTrace(new PrintWriter(output.getOutputWriter()));
				}
				autoImportButton.setEnabled(false);
				prompt.getComponent().requestFocus();
			});
			bottomPane.add(autoImportButton, "w pref!, h pref!, wrap");
		}

		final JSplitPane outputAndPromptPane =
			new JSplitPane(JSplitPane.VERTICAL_SPLIT, outputScroll, bottomPane);
		outputAndPromptPane.setResizeWeight(1);

		mainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, vars,
			outputAndPromptPane);
		mainPane.setDividerLocation(300);
	}

	// -- InterpreterPane methods --

	/** Gets the associated script REPL. */
	public ScriptREPL getREPL() {
		return repl;
	}

	/** Prints a message to the output panel. */
	public void print(final String string) {
		final Writer writer = output.getOutputWriter();
		try {
			writer.write(string + "\n");
		}
		catch (final IOException e) {
			e.printStackTrace(new PrintWriter(writer));
		}
	}

	public void dispose() {
		output.close();
	}

	// -- UIComponent methods --

	@Override
	public JComponent getComponent() {
		return mainPane;
	}

	@Override
	public Class<JComponent> getComponentType() {
		return JComponent.class;
	}

}
