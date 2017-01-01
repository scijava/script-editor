/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
 * Wisconsin-Madison, Max Planck Institute of Molecular Cell Biology and
 * Genetics, and others.
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

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;
import org.scijava.script.ScriptInterpreter;
import org.scijava.script.ScriptREPL;
import org.scijava.script.ScriptService;

/**
 * The main interpreter window.
 *
 * @author Curtis Rueden
 * @author Johannes Schindelin
 */
public class InterpreterWindow extends JFrame {

	private final InterpreterPane pane;

	@Parameter
	private PrefService prefs;

	@Parameter
	private ScriptService scriptService;

	@Parameter
	private LogService log;

	/** Constructs the scripting interpreter window. */
	public InterpreterWindow(final Context context) {
		super("Script Interpreter");
		context.inject(this);

		pane = new InterpreterPane(context) {
			@Override
			public void dispose() {
				super.dispose();
				InterpreterWindow.super.dispose();
			}
		};
		setContentPane(pane.getComponent());

		pack();

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
	}

	/** Gets the window's associated {@link ScriptREPL}. */
	public ScriptREPL getREPL() {
		return pane.getREPL();
	}

	/**
	 * Gets the associated REPL's active {@link ScriptInterpreter}.
	 *
	 * @see #getREPL()
	 */
	public ScriptInterpreter getInterpreter() {
		return pane.getREPL().getInterpreter();
	}

	@Override
	public void dispose() {
		// write out interpreter histories, etc., when frame goes away
		pane.dispose();
		super.dispose();
	}

	// -- Helper methods --

	/** Print a message to the current language's output panel */
	public void print(final String string) {
		pane.print(string);
	}
}
