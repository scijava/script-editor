/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2015 Board of Regents of the University of
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

package net.imagej.ui.swing.script;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imagej.ui.swing.script.interpreter.InterpreterWindow;

import org.scijava.Context;
import org.scijava.app.SciJavaApp;
import org.scijava.plugin.Plugin;

/**
 * Interactive test for the script interpreter.
 *
 * @author Johannes Schindelin
 */
public class ScriptInterpreterTestDrive {
	private static InterpreterWindow interpreter;

	public static void main(String[] args) throws Exception {
		final Context context = new Context();
		interpreter = new InterpreterWindow(context);
		interpreter.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(final WindowEvent e) {
				context.dispose();
			}
		});
		interpreter.setVisible(true);
	}

	@Plugin(type = AutoImporter.class)
	public static class XYZ implements AutoImporter {

		@Override
		public Map<String, List<String>> getDefaultImports() {
			final String name = SciJavaApp.class.getName();
			final int dot = name.lastIndexOf('.');
			final String base = name.substring(dot + 1);
			interpreter.print("Imported " + base + "; Try:\n\n\tprint(" + base + ".NAME);");

			final Map<String, List<String>> map = new HashMap<>();
			map.put(name.substring(0, dot), Arrays.asList(base));
			return map;
		}

	}
}
