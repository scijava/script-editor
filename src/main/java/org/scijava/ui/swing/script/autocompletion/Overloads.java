/*
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

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import org.scijava.code.api.Completion;
import org.scijava.code.api.TypeResolver;

/**
 * Helpers for ranking a callable's overloads against the arguments typed so
 * far.
 *
 * @author Gabriel Selzer
 */
final class Overloads {

	/** Fit of an argument to a parameter: incompatible. */
	static final int NONE = 0;

	/** Fit of an argument to a parameter: compatible after widening. */
	static final int CONVERSION = 1;

	/** Fit of an argument to a parameter: compatible as is. */
	static final int MATCH = 2;

	private static final Map<String, Class<?>> PRIMITIVES = new HashMap<>();
	static {
		for (final Class<?> c : new Class<?>[] { boolean.class, byte.class,
			char.class, short.class, int.class, long.class, float.class,
			double.class, void.class })
		{
			PRIMITIVES.put(c.getName(), c);
		}
	}

	private Overloads() {
		// NB: Prevent instantiation of utility class.
	}

	/**
	 * Gets the types of the arguments of the call enclosing the caret, or null
	 * if the caret is not inside an argument list. An argument's type is null
	 * if it is unknown, empty, or still RSTA's placeholder (the parameter name
	 * of the accepted overload).
	 */
	static List<String> argumentTypes(final JTextComponent comp,
		final TypeResolver resolver, final Completion accepted)
	{
		final List<String> args = argumentsAtCaret(comp);
		if (args == null) return null;
		final List<Completion.Parameter> params = accepted.parameters();
		final List<String> types = new ArrayList<>(args.size());
		for (int i = 0; i < args.size(); i++) {
			final String arg = args.get(i).trim();
			final boolean placeholder = i < params.size() && //
				arg.equals(params.get(i).name());
			types.add(arg.isEmpty() || placeholder || resolver == null ? null
				: typeOf(resolver, arg));
		}
		return types;
	}

	/**
	 * Rates how well {@code overload} fits arguments of the given types: the
	 * worst fit among its arguments, or {@link #NONE} if more arguments were
	 * given than it has parameters.
	 *
	 * @return {@link #MATCH}, {@link #CONVERSION} or {@link #NONE}
	 */
	static int fit(final Completion overload, final List<String> argTypes) {
		final List<Completion.Parameter> params = overload.parameters();
		int fit = MATCH;
		for (int i = 0; i < argTypes.size(); i++) {
			if (argTypes.get(i) == null) continue;
			if (i >= params.size()) return NONE;
			fit = Math.min(fit, fit(argTypes.get(i), params.get(i).type()));
		}
		return fit;
	}

	/**
	 * Rates passing a value of type {@code argType} to a parameter of type
	 * {@code paramType}, following Jython's conversions:
	 * <ul>
	 * <li>{@link #MATCH}: the same type, an assignable one, or the same kind of
	 * number (a Python integer to an integral parameter, or a Python float to a
	 * floating-point one). Unknown types match too, so they never rule out or
	 * demote an overload.</li>
	 * <li>{@link #CONVERSION}: a Python integer to a floating-point
	 * parameter.</li>
	 * <li>{@link #NONE}: anything else.</li>
	 * </ul>
	 */
	static int fit(final String argType, final String paramType) {
		if (argType == null || paramType == null || argType.equals(paramType)) {
			return MATCH;
		}
		final Class<?> a = loadType(argType), p = loadType(paramType);
		if (a == null || p == null) return MATCH;
		final Class<?> ba = box(a), bp = box(p);
		if (bp.isAssignableFrom(ba)) return MATCH;
		if (isIntegral(ba)) {
			if (isIntegral(bp)) return MATCH;
			if (isFloating(bp)) return CONVERSION;
		}
		if (isFloating(ba) && isFloating(bp)) return MATCH;
		return NONE;
	}

	/**
	 * Splits the argument list enclosing the caret into its arguments' source
	 * text, or returns null if the caret is not inside an argument list.
	 */
	static List<String> argumentsAtCaret(final JTextComponent comp) {
		final String text;
		try {
			text = comp.getText(0, comp.getDocument().getLength());
		}
		catch (final BadLocationException exc) {
			return null;
		}
		// Find the unmatched opening parenthesis before the caret.
		int open = -1, depth = 0;
		for (int i = Math.min(comp.getCaretPosition(), text.length()) - 1; //
			i >= 0; i--)
		{
			final char c = text.charAt(i);
			if (c == '\n') return null;
			if (c == ')' || c == ']' || c == '}') depth++;
			else if (c == '(' || c == '[' || c == '{') {
				if (depth > 0) depth--;
				else if (c == '(') {
					open = i;
					break;
				}
				else return null; // Inside a list or dict, not a call.
			}
		}
		if (open < 0) return null;
		// Split on top-level commas, up to the matching closing parenthesis.
		final List<String> args = new ArrayList<>();
		int start = open + 1;
		char quote = 0;
		depth = 0;
		for (int i = start; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (quote != 0) {
				if (c == '\\') i++;
				else if (c == quote) quote = 0;
				continue;
			}
			if (c == '"' || c == '\'') quote = c;
			else if (c == '(' || c == '[' || c == '{') depth++;
			else if (c == ')' || c == ']' || c == '}') {
				if (depth == 0) {
					args.add(text.substring(start, i));
					return args;
				}
				depth--;
			}
			else if (c == ',' && depth == 0) {
				args.add(text.substring(start, i));
				start = i + 1;
			}
			else if (c == '\n') break;
		}
		args.add(text.substring(start));
		return args;
	}

	// -- Helper methods --

	private static String typeOf(final TypeResolver resolver,
		final String expression)
	{
		try {
			return resolver.typeOf(expression);
		}
		catch (final RuntimeException exc) {
			return null;
		}
	}

	/** Loads a type such as {@code int}, {@code long[]} or {@code a.b.C}. */
	private static Class<?> loadType(final String name) {
		if (name.endsWith("[]")) {
			final Class<?> component = loadType(name.substring(0, name.length() -
				2));
			return component == null ? null : Array.newInstance(component, 0)
				.getClass();
		}
		final Class<?> primitive = PRIMITIVES.get(name);
		if (primitive != null) return primitive;
		try {
			return Class.forName(name, false, Thread.currentThread()
				.getContextClassLoader());
		}
		catch (final ClassNotFoundException | LinkageError exc) {
			return null;
		}
	}

	private static Class<?> box(final Class<?> c) {
		if (!c.isPrimitive()) return c;
		if (c == int.class) return Integer.class;
		if (c == long.class) return Long.class;
		if (c == double.class) return Double.class;
		if (c == float.class) return Float.class;
		if (c == boolean.class) return Boolean.class;
		if (c == char.class) return Character.class;
		if (c == short.class) return Short.class;
		if (c == byte.class) return Byte.class;
		return Void.class;
	}

	private static boolean isIntegral(final Class<?> c) {
		return c == Integer.class || c == Long.class || c == Short.class ||
			c == Byte.class || c == BigInteger.class;
	}

	private static boolean isFloating(final Class<?> c) {
		return c == Double.class || c == Float.class;
	}
}
