/*-
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2020 SciJava developers.
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
package org.scijava.ui.swing.script.autocompletion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.Vector;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.scijava.ui.swing.script.autocompletion.JythonAutoCompletion.Import;

public class JythonAutocompletionProvider extends DefaultCompletionProvider {
	
	static private final Vector<AutoCompletionListener> autocompletion_listeners = new Vector<>();
	
	private final RSyntaxTextArea text_area;
	private final ImportFormat formatter;

	public JythonAutocompletionProvider(final RSyntaxTextArea text_area, final ImportFormat formatter) {
		this.text_area = text_area;
		this.formatter = formatter;
		new Thread(new Runnable() {
			@Override
			public void run() {
				ClassUtil.ensureCache();
			}
		}).start();
	}
	
	/**
	 * Override parent implementation to allow letters, digits, the period and a space, to be able to match e.g.:
	 * 
	 * "from "
	 * "from ij"
	 * "from ij.Im"
	 * etc.
	 * 
	 * @param c
	 */
	@Override
	public boolean isValidChar(final char c) {
		return Character.isLetterOrDigit(c) || '.' == c || ' ' == c;
	}
	
	static private final Pattern
			fromImport = Pattern.compile("^((from|import)[ \\t]+)([a-zA-Z_][a-zA-Z0-9._]*)$"),
			fastImport = Pattern.compile("^(from[ \\t]+)([a-zA-Z_][a-zA-Z0-9._]*)[ \\t]+$"),
			importStatement = Pattern.compile("^((from[ \\t]+([a-zA-Z0-9._]+)[ \\t]+|[ \\t]*)import[ \\t]+)([a-zA-Z0-9_., \\t]*)$"),
			simpleClassName = Pattern.compile("^(.*[ \\t]+|)([A-Z_][a-zA-Z0-9_]+)$"),
			staticMethodOrField = Pattern.compile("^((.*[ \\t]+|)([A-Z_][a-zA-Z0-9_]*)\\.)([a-zA-Z0-9_]*)$");
	
	private final List<Completion> asCompletionList(final Stream<String> stream, final String pre) {
		return stream
				.map((s) -> new BasicCompletion(JythonAutocompletionProvider.this, pre + s))
				.collect(Collectors.toList());
	}
	
	static public void addAutoCompletionListener(final AutoCompletionListener listener) {
		if (!autocompletion_listeners.contains(listener))
			autocompletion_listeners.add(listener);
	}
	
	static public void removeAutoCompletionListener(final AutoCompletionListener listener) {
		autocompletion_listeners.remove(listener);
	}
	
	@Override
	public List<Completion> getCompletionsImpl(final JTextComponent comp) {
		final ArrayList<Completion> completions = new ArrayList<>();
		final String text = this.getAlreadyEnteredText(comp);
		completions.addAll(getCompletions(text));
		for (final AutoCompletionListener listener: new Vector<>(autocompletion_listeners)) {
			try {
				final List<Completion> cs = listener.completionsFor(text);
				if ( null != cs)
					completions.addAll(cs);
			} catch (Exception e) {
				System.out.println("Failed to get autocompletions from " + listener);
				e.printStackTrace();
			}
		}
		return completions;
	}

	public List<Completion> getCompletions(final String text) {
		// don't block
		if (!ClassUtil.isCacheReady()) return Collections.emptyList();

		// E.g. "from ij" to expand to a package name and class like ij or ij.gui or ij.plugin
		final Matcher m1 = fromImport.matcher(text);
		if (m1.find())
			return asCompletionList(ClassUtil.findClassNamesContaining(m1.group(3)).map(formatter::singleToImportStatement), "");

		final Matcher m1f = fastImport.matcher(text);
		if (m1f.find())
			return asCompletionList(ClassUtil.findClassNamesForPackage(m1f.group(2)).map(formatter::singleToImportStatement), "");
		
		// E.g. "from ij.gui import Roi, Po" to expand to PolygonRoi, PointRoi for Jython
		// or e.g. "importClass(Package.ij" to expand to a fully qualified class name for Javascript
		final Matcher m2 = importStatement.matcher(text);
		if (m2.find()) {
			String packageName = m2.group(3),
					 className = m2.group(4); // incomplete or empty, or multiple separated by commas with the last one incomplete or empty

			System.out.println("m2 matches className: " + className);
			final String[] bycomma = className.split(",");
			String precomma = "";
			if (bycomma.length > 1) {
				className = bycomma[bycomma.length -1].trim(); // last one
				for (int i=0; i<bycomma.length -1; ++i)
					precomma += bycomma[0] + ", ";
			}
			Stream<String> stream;
			if (className.length() > 0)
				stream = ClassUtil.findClassNamesStartingWith(null == packageName ? className : packageName + "." + className);
			else
				stream = ClassUtil.findClassNamesForPackage(packageName);
			// Simple class names
			stream = stream.map((s) -> s.substring(Math.max(0, s.lastIndexOf('.') + 1)));
			return asCompletionList(stream, m2.group(1) + precomma);
		}

		final Matcher m3 = simpleClassName.matcher(text);
		if (m3.find()) {
			// Side effect: insert the import at the top of the file if necessary
			//return asCompletionList(ClassUtil.findSimpleClassNamesStartingWith(m3.group(2)).stream(), m3.group(1));
			return ClassUtil.findSimpleClassNamesStartingWith(m3.group(2)).stream()
					.map(className -> new ImportCompletion(JythonAutocompletionProvider.this,
							m3.group(1) + className.substring(className.lastIndexOf('.') + 1),
							className,
							formatter.singleToImportStatement(className)))
					.collect(Collectors.toList());
		}

		final Matcher m4 = staticMethodOrField.matcher(text);
		if (m4.find()) {
			try {
				final String simpleClassName   = m4.group(3), // expected complete, e.g. ImagePlus
							 methodOrFieldSeed = m4.group(4).toLowerCase(); // incomplete: e.g. "GR", a string to search for in the class declared fields or methods

				// Scan the script, parse the imports, find first one matching
				final Import im = JythonAutoCompletion.findImportedClasses(text_area.getText()).get(simpleClassName);
				if (null != im) {
					final Class<?> c = Class.forName(im.className);
					final ArrayList<String> matches = new ArrayList<>();
					for (final Field f: c.getFields()) {
						if (Modifier.isStatic(f.getModifiers()) && f.getName().toLowerCase().startsWith(methodOrFieldSeed))
							matches.add(f.getName());
					}
					for (final Method m: c.getMethods()) {
						if (Modifier.isStatic(m.getModifiers()) && m.getName().toLowerCase().startsWith(methodOrFieldSeed))
							matches.add(m.getName() + "(");
					}
					return asCompletionList(matches.stream(), m4.group(1));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return Collections.emptyList();
	}
}
