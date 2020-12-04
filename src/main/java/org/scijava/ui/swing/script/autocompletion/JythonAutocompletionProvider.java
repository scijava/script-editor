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
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.text.BadLocationException;
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
			simpleClassName = Pattern.compile("^(.*[ \\t]+|)([a-zA-Z0-9_]+)$"),
			staticMethodOrField = Pattern.compile("^((.*[ \\t]+|)([a-zA-Z0-9_]*)\\.)([a-zA-Z0-9_]*)$");
	
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
			final String packageName = m2.group(3);
			String className = m2.group(4); // incomplete or empty, or multiple separated by commas with the last one incomplete or empty

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
		try {

			String simpleClassName;
			String methodOrFieldSeed;
			String pre;
			boolean isStatic;

			if (m4.find()) {

				// a call to a static class
				pre = m4.group(1);
				simpleClassName   = m4.group(3); // expected complete, e.g. ImagePlus
				methodOrFieldSeed = m4.group(4).toLowerCase(); // incomplete: e.g. "GR", a string to search for in the class declared fields or methods
				isStatic = true;

			} else {

				// a call to an instantiated class
				final String[] varAndSeed = getVariableAnSeedAtCaretLocation();
				if (varAndSeed == null) return Collections.emptyList();

				simpleClassName = JythonAutoCompletion.findClassAliasOfVariable(varAndSeed[0], text_area.getText());
				if (simpleClassName == null) return Collections.emptyList();

				pre = varAndSeed[0] + ".";
				methodOrFieldSeed = varAndSeed[1];
				isStatic = false;

//				System.out.println("simpleClassName: " + simpleClassName);
//				System.out.println("methodOrFieldSeed: " + methodOrFieldSeed);

			}

			// Retrieve all methods and fields, if the seed is empty
			final boolean includeAll = methodOrFieldSeed.trim().isEmpty();

			// Scan the script, parse the imports, find first one matching
			final Import im = JythonAutoCompletion.findImportedClasses(text_area.getText()).get(simpleClassName);
			if (null != im) {
				try {
					final Class<?> c = Class.forName(im.className);
					final ArrayList<Completion> completions = new ArrayList<>();
					for (final Field f: c.getFields()) {
						if (isStatic == Modifier.isStatic(f.getModifiers()) &&
								(includeAll || f.getName().toLowerCase().contains(methodOrFieldSeed)))
							completions.add(getCompletion(pre, f, c));
					}
					for (final Method m: c.getMethods()) {
						if (isStatic == Modifier.isStatic(m.getModifiers()) &&
								(includeAll || m.getName().toLowerCase().contains(methodOrFieldSeed)))
						completions.add(getCompletion(pre, m, c));
					}

					Collections.sort(completions, new Comparator<Completion>() {
						int prefix1Index = Integer.MAX_VALUE;
						int prefix2Index = Integer.MAX_VALUE;
						@Override
						public int compare(final Completion o1, final Completion o2) {
							prefix1Index = Integer.MAX_VALUE;
							prefix2Index = Integer.MAX_VALUE;
							if (o1.getReplacementText().startsWith(pre))
								prefix1Index = 0;
							if (o2.getReplacementText().startsWith(pre))
								prefix2Index = 0;
							if (prefix1Index == prefix2Index)
								return o1.compareTo(o2);
							else
								return prefix1Index - prefix2Index;
						}
					});

					return completions;
				} catch (final ClassNotFoundException ignored) {
					return classUnavailableCompletions(simpleClassName + ".");
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return Collections.emptyList();
	}

	private String getJavaDocLink(final Class<?> c) {
		final String name = c.getCanonicalName();
		final String pkg = getDocPackage(name);
		if (pkg == null) return name;
		final String url = String.format("%s%s%s", ClassUtil.scijava_javadoc_URL, pkg, name.replace(".", "/"));
		return String.format("<a href='%s';>%s</a>", url, name);
	}

	private String getDocPackage(final String classCanonicalName) {
		//TODO: Do this programatically
		if (classCanonicalName.startsWith("ij."))
			return "ImageJ1/";
		else if (classCanonicalName.startsWith("sc.fiji"))
			return "Fiji/";
		else if (classCanonicalName.startsWith("net.imagej"))
			return "ImageJ/";
		else if (classCanonicalName.startsWith("net.imglib2"))
			return "ImgLib2/";
		else if (classCanonicalName.startsWith("org.scijava"))
			return "SciJava/";
		else if (classCanonicalName.startsWith("loci.formats"))
			return "Bio-Formats/";
		if (classCanonicalName.startsWith("java."))
			return "Java8/";
		else if (classCanonicalName.startsWith("sc.iview"))
			return "SciView/";
		else if (classCanonicalName.startsWith("weka."))
			return "Weka/";
		else if (classCanonicalName.startsWith("inra.ijpb"))
			return "MorphoLibJ/";
		return null;
	}

	private Completion getCompletion(final String pre, final Field field, final Class<?> c) {
		final StringBuffer summary = new StringBuffer();
		summary.append("<b>").append(field.getName()).append("</b>");
		summary.append(" ("+ field.getType().getSimpleName()).append(")");
		summary.append("<DL>");
		summary.append("<DT><b>Defined in:</b>");
		summary.append("<DD>").append(getJavaDocLink(c));
		summary.append("</DL>");
		return new BasicCompletion(JythonAutocompletionProvider.this, pre+field.getName(), null, summary.toString());
	}

	private Completion getCompletion(final String pre, final Method method, final Class<?> c) {
		final StringBuffer summary = new StringBuffer();
		final StringBuffer replacementHeader = new StringBuffer(method.getName());
		String replacementString;
		final int bIndex = replacementHeader.length(); // remember '(' position
		replacementHeader.append("(");
		final Parameter[] params = method.getParameters();
		if (params.length > 0) {
			for (final Parameter parameter : params) {
				replacementHeader.append(parameter.getType().getSimpleName()).append(", ");
			}
			replacementHeader.setLength(replacementHeader.length() - 2); // remove trailing ', ';
		}
		replacementHeader.append(")");
		replacementString = pre + replacementHeader.toString();

		replacementHeader.replace(bIndex, bIndex+1, "</b>("); // In header, highlight only method name for extra contrast
		summary.append("<b>").append(replacementHeader);
		summary.append("<DL>");
		summary.append("<DT><b>Returns:</b>");
		summary.append("<DD>").append(method.getReturnType().getSimpleName());
		summary.append("<DT><b>Defined in:</b>");
		summary.append("<DD>").append(getJavaDocLink(c));
		summary.append("</DL>");

		return new BasicCompletion(JythonAutocompletionProvider.this, replacementString, null, summary.toString());
	}

	private List<Completion> classUnavailableCompletions(final String pre) {
		// placeholder completions to warn users class was not available (repeated to force pop-up display)
		final List<Completion> list = new ArrayList<>();
		final String summary = "Class not found or invalid import. See "
				+ String.format("<a href='%s';>SciJavaDocs</a>", ClassUtil.scijava_javadoc_URL)
				+ " or <a href='https://search.imagej.net/';>search</a> for help";
		list.add(new BasicCompletion(JythonAutocompletionProvider.this, pre + "?", null, summary));
		list.add(new BasicCompletion(JythonAutocompletionProvider.this, pre + "?", null, summary));
		return list;
	}

	private String[] getVariableAnSeedAtCaretLocation() {
		try {
			final int caretOffset = text_area.getCaretPosition();
			final int lineNumber = text_area.getLineOfOffset(caretOffset);
			final int startOffset = text_area.getLineStartOffset(lineNumber);
			final String lineUpToCaret = text_area.getText(startOffset, caretOffset - startOffset);
			final String[] words = lineUpToCaret.split("\\s+");
			final String[] varAndSeed = words[words.length - 1].split("\\.");
			return (varAndSeed.length == 2) ? varAndSeed : new String[] { varAndSeed[varAndSeed.length - 1], "" };
		} catch (final BadLocationException e) {
			e.printStackTrace();
		}
		return null;
	}

}
