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

package org.scijava.script.parse;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

import javax.script.ScriptException;

import org.scijava.parse.Variable;
import org.scijava.parse.eval.DefaultEvaluator;
import org.scijava.parse.eval.Evaluator;
import org.scijava.script.AbstractScriptEngine;

/**
 * Script engine for the {@link ParsingtonScriptLanguage}.
 * 
 * @author Curtis Rueden
 */
public class ParsingtonScriptEngine extends AbstractScriptEngine {

	private final Evaluator e;

	/** Data structure for {@code foo.bar} pairs. */
	private static class MethodCall {

		private final Object object;
		private final String methodName;

		private MethodCall(final Object o, final String name) {
			object = o;
			methodName = name;
		}

		public Object invoke(final Object args) {
			final Object[] methodArgs;
			if (args instanceof Object[]) {
				methodArgs = (Object[]) args;
			}
			else if (args instanceof Collection) {
				methodArgs = ((Collection<?>) args).toArray();
			}
			else {
				// assume singleton argument
				methodArgs = new Object[] { args };
			}
			final Class<?>[] argTypes = //
				Arrays.stream(methodArgs).map(a -> a.getClass()).toArray(Class[]::new);
			// TODO: arg type detection is brittle. Need SJC Types matcher!
			// Should bake method sig matching into a method of Types, actually.
			try {
				final Method m = object.getClass().getMethod(methodName, argTypes);
				return m.invoke(object, methodArgs);
			}
			catch (final NoSuchMethodException | IllegalAccessException
					| InvocationTargetException exc)
			{
				exc.printStackTrace();
				return null;
			}
		}
	}

	public ParsingtonScriptEngine() {
		// NB: Create a default evaluator, but extended to support method calls.
		// This is a first-cut hack, just for fun -- fields are not supported yet.
		this(new DefaultEvaluator() {

			@Override
			public Object dot(final Object a, final Object b) {
				if (b instanceof Variable) {
					return new MethodCall(value(a), ((Variable) b).getToken());
				}
				return super.dot(a, b);
			}

			@Override
			public Object function(final Object a, final Object b) {
				if (a instanceof MethodCall) {
					return ((MethodCall) a).invoke(b);
				}
				return super.function(a, b);
			}
		});
		engineScopeBindings = new ParsingtonBindings(e);
	}

	public ParsingtonScriptEngine(final Evaluator e) {
		this.e = e;
	}

	// -- ScriptEngine methods --

	@Override
	public Object eval(final String script) throws ScriptException {
		try {
			return e.value(e.evaluate(script.trim()));
		}
		catch (final IllegalArgumentException exc) {
			// NB: Standardize script evaluation exceptions to the checked type.
			throw new ScriptException(exc);
		}
	}

	@Override
	public Object eval(final Reader reader) throws ScriptException {
		try {
			return eval(readString(reader));
		}
		catch (final IOException exc) {
			throw new ScriptException(exc);
		}
	}

	// -- Helper methods --

	private String readString(final Reader reader) throws IOException {
		final char[] buf = new char[8192];
		final StringBuilder sb = new StringBuilder();
		int r;
		while ((r = reader.read(buf, 0, buf.length)) != -1) {
			sb.append(buf, 0, r);
		}
		reader.close();
		return sb.toString();
	}

}
