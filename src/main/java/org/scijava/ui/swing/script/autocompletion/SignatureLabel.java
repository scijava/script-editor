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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;

/**
 * A callable's signature as the editor shows it, read from a server's text:
 * its name, its parameters (e.g. {@code double a}) and its return type; from
 * a {@link SignatureInformation} (e.g. {@code max(double a, double b) ->
 * double}) or a completion's label details (e.g. {@code (double a, double b)}).
 *
 * @author Gabriel Selzer
 */
final class SignatureLabel {

	final String name;
	final List<String> parameters;
	final String returnType;

	private SignatureLabel(final String name, final List<String> parameters,
		final String returnType)
	{
		this.name = name;
		this.parameters = parameters;
		this.returnType = returnType;
	}

	/** Reads a signature (e.g. {@code def max(a, b) -> int}). */
	static SignatureLabel of(final SignatureInformation s) {
		final String label = s.getLabel();
		final int open = label.indexOf('(');
		final String head = (open < 0 ? label : label.substring(0, open)).trim();
		final String name = head.substring(head.lastIndexOf(' ') + 1);
		final List<String> params = new ArrayList<>();
		if (s.getParameters() != null && !s.getParameters().isEmpty()) {
			for (final ParameterInformation p : s.getParameters()) {
				params.add(label(label, p.getLabel()));
			}
		}
		else if (open >= 0) params.addAll(parameters(label.substring(open)));
		final int close = open < 0 ? -1 : closing(label, open);
		String returns = close < 0 ? "" : label.substring(close + 1).trim();
		if (returns.startsWith("->")) returns = returns.substring(2).trim();
		else if (returns.startsWith(":")) returns = returns.substring(1).trim();
		return new SignatureLabel(name, params, returns.isEmpty() ? null : returns);
	}

	/**
	 * Reads a parameter list, e.g. {@code (double a, Map<K, V> m)}: its
	 * parameters, split at the commas outside brackets.
	 */
	static List<String> parameters(final String list) {
		final String s = list.trim();
		if (!s.startsWith("(")) return Collections.emptyList();
		final int close = closing(s, 0);
		final String inner = s.substring(1, close < 0 ? s.length() : close);
		final List<String> out = new ArrayList<>();
		int depth = 0, start = 0;
		for (int i = 0; i < inner.length(); i++) {
			final char c = inner.charAt(i);
			if (c == '(' || c == '[' || c == '{' || c == '<') depth++;
			else if (c == ')' || c == ']' || c == '}' || c == '>') depth--;
			else if (c == ',' && depth == 0) {
				out.add(inner.substring(start, i).trim());
				start = i + 1;
			}
		}
		final String last = inner.substring(start).trim();
		if (!last.isEmpty() || !out.isEmpty()) out.add(last);
		return out;
	}

	/**
	 * A parameter (e.g. {@code java.lang.String name}) with simple type names
	 * ({@code String name}).
	 */
	static String simple(final String parameter) {
		final int space = parameter.lastIndexOf(' ');
		if (space < 0) return parameter;
		final String type = parameter.substring(0, space);
		return type.substring(type.lastIndexOf('.') + 1) + parameter.substring(
			space);
	}

	/** Splits a parameter into its type (or null) and its name. */
	static String[] typeAndName(final String parameter) {
		final int space = parameter.lastIndexOf(' ');
		return space < 0 ? new String[] { null, parameter } : new String[] {
			parameter.substring(0, space).trim(), parameter.substring(space + 1) };
	}

	private static String label(final String signature,
		final Either<String, Tuple.Two<Integer, Integer>> label)
	{
		if (label.isLeft()) return label.getLeft();
		return signature.substring(label.getRight().getFirst(), label.getRight()
			.getSecond());
	}

	private static int closing(final String s, final int open) {
		int depth = 0;
		for (int i = open; i < s.length(); i++) {
			final char c = s.charAt(i);
			if (c == '(') depth++;
			else if (c == ')' && --depth == 0) return i;
		}
		return -1;
	}
}
