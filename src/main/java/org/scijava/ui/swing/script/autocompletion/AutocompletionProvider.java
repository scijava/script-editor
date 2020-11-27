package org.scijava.ui.swing.script.autocompletion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.scijava.ui.swing.script.ClassUtil;

public class AutocompletionProvider extends DefaultCompletionProvider {
	
	private final RSyntaxTextArea text_area;

	public AutocompletionProvider(final RSyntaxTextArea text_area) {
		this.text_area = text_area;
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
			fromImport = Pattern.compile("^((from|import)[ \\t]+)([a-zA-Z][a-zA-Z0-9._]*)$"),
			fastImport = Pattern.compile("^(from[ \\t]+)([a-zA-Z][a-zA-Z0-9._]*)[ \\t]+$"),
			importStatement = Pattern.compile("^((from[ \\t]+([a-zA-Z0-9._]+)[ \\t]+|[ \\t]*)import(Class\\(|[ \\t]+))([a-zA-Z0-9_., \\t]*)$"),
			simpleClassName = Pattern.compile("^(.*[ \\t]+|)([A-Z_][a-zA-Z0-9_]+)$"),
			staticMethodOrField = Pattern.compile("^((.*[ \\t]+|)([A-Z_][a-zA-Z0-9_]*)\\.)([a-zA-Z0-9_]*)$");
	
	private final List<Completion> asCompletionList(final Stream<String> stream, final String pre) {
		return stream
				.map((s) -> new BasicCompletion(AutocompletionProvider.this, pre + s))
				.collect(Collectors.toList());
	}
	
	@Override
	public List<Completion> getCompletionsImpl(final JTextComponent comp) {
		// don't block
		if (!ClassUtil.isCacheReady()) return Collections.emptyList();
		
		final String text = this.getAlreadyEnteredText(comp);

		// E.g. "from ij" to expand to a package name and class like ij or ij.gui or ij.plugin
		final Matcher m1 = fromImport.matcher(text);
		if (m1.find())
			return asCompletionList(ClassUtil.findClassNamesContaining(m1.group(3))
					.map(new Function<String, String>() {
						@Override
						public final String apply(final String s) {
							final int idot = s.lastIndexOf('.');
							return "from " + s.substring(0, Math.max(0, idot)) + " import " + s.substring(idot +1);
						}
					}),
					"");

		final Matcher m1f = fastImport.matcher(text);
		if (m1f.find())
			return asCompletionList(ClassUtil.findClassNamesForPackage(m1f.group(2)).map(s -> s.substring(m1f.group(2).length() + 1)),
					m1f.group(0) + "import ");
		
		// E.g. "from ij.gui import Roi, Po" to expand to PolygonRoi, PointRoi for Jython
		// or e.g. "importClass(Package.ij" to expand to a fully qualified class name for Javascript
		final Matcher m2 = importStatement.matcher(text);
		if (m2.find()) {
			String packageName = m2.group(3),
					 className = m2.group(5); // incomplete or empty, or multiple separated by commas with the last one incomplete or empty

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
			if (!m2.group(4).equals("Class(Package"))
				stream = stream.map((s) -> s.substring(Math.max(0, s.lastIndexOf('.') + 1))); // simple class name for Jython
			return asCompletionList(stream, m2.group(1) + precomma);
		}

		final Matcher m3 = simpleClassName.matcher(text);
		if (m3.find())
			return asCompletionList(ClassUtil.findSimpleClassNamesStartingWith(m3.group(2)).stream(), m3.group(1));

		final Matcher m4 = staticMethodOrField.matcher(text);
		if (m4.find()) {
			try {
				final String simpleClassName   = m4.group(3), // expected complete, e.g. ImagePlus
							 methodOrFieldSeed = m4.group(4).toLowerCase(); // incomplete: e.g. "GR", a string to search for in the class declared fields or methods
				// Scan the script, parse the imports, find first one matching
				String packageName = null;
				lines: for (final String line: text_area.getText().split("\n")) {
					System.out.println(line);
					final String[] comma = line.split(",");
					final Matcher m = importStatement.matcher(comma[0]);
					if (m.find()) {
						final String first = m.group(5);
						if (m.group(4).equals("Class(Package")) {
							// Javascript import
							final int lastdot = Math.max(0, first.lastIndexOf('.'));
							if (simpleClassName.equals(first.substring(lastdot + 1))) {
								packageName = first.substring(0, lastdot);
								break lines;
							}
						} else {
							// Jython import
							comma[0] = first;
							for (int i=0; i<comma.length; ++i)
								if (simpleClassName.equals(comma[i].trim())) {
									packageName = m.group(3);
									break lines;
								}
						}
					}
				}
				System.out.println("package name: " + packageName);
				if (null != packageName) {
					final Class<?> c = Class.forName(packageName + "." + simpleClassName);
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
