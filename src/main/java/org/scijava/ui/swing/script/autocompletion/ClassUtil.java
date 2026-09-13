/*-
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2026 SciJava developers.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.scijava.script.complete.ClassIndex;

/**
 * Thin facade over the toolkit-agnostic {@link ClassIndex} in scijava-common,
 * retained for the script editor's Swing-specific completion code. New code
 * should use {@link ClassIndex} directly; only the RSTA-specific
 * {@link #classUnavailableCompletions} helper is genuinely editor-side.
 *
 * @author Albert Cardona
 * @author Tiago Ferreira
 * @deprecated Use {@link ClassIndex} for class discovery and documentation; this
 *             facade exists to ease migration.
 */
@Deprecated
public class ClassUtil {

	public static void ensureCache() {
		ClassIndex.ensureCache();
	}

	public static boolean isCacheReady() {
		return ClassIndex.isCacheReady();
	}

	public static Stream<String> findPackageNamesStartingWith(final String t) {
		return ClassIndex.findPackageNamesStartingWith(t);
	}

	public static Stream<String> findClassNamesForPackage(final String pkg) {
		return ClassIndex.findClassNamesForPackage(pkg);
	}

	public static Stream<String> findClassNamesStartingWith(final String t) {
		return ClassIndex.findClassNamesStartingWith(t);
	}

	public static Stream<String> findClassNamesContaining(final String t) {
		return ClassIndex.findClassNamesContaining(t);
	}

	public static ArrayList<String> findSimpleClassNamesStartingWith(
		final String t)
	{
		return ClassIndex.findSimpleClassNamesStartingWith(t);
	}

	public static HashMap<String, ArrayList<String>> findDocumentationForClass(
		final String s)
	{
		return ClassIndex.findDocumentationForClass(s);
	}

	protected static String getSummaryCompletion(final Field field,
		final Class<?> c)
	{
		return ClassIndex.getSummaryCompletion(field, c);
	}

	protected static String getSummaryCompletion(final Method method,
		final Class<?> c)
	{
		return ClassIndex.getSummaryCompletion(method, c);
	}

	protected static String getSummaryCompletion(final Constructor<?> constructor,
		final Class<?> c)
	{
		return ClassIndex.getSummaryCompletion(constructor, c);
	}

	/**
	 * Placeholder RSTA completions warning that a class was unavailable. This is
	 * the one genuinely Swing/RSTA-specific helper; it stays editor-side.
	 */
	static List<Completion> classUnavailableCompletions(
		final CompletionProvider provider, final String pre)
	{
		final List<Completion> list = new ArrayList<>();
		final String summary = "Class not found or invalid import. See " + String
			.format("<a href='%s';>SciJavaDocs</a>", ClassIndex.SCIJAVA_JAVADOC_URL) +
			" or <a href='https://search.imagej.net/';>search</a> for help";
		// Repeated to force pop-up display.
		list.add(new BasicCompletion(provider, pre + "?", null, summary));
		list.add(new BasicCompletion(provider, pre + "?", null, summary));
		return list;
	}
}
