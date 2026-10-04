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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.Test;
import org.scijava.code.api.Completion;

/**
 * Tests {@link Overloads}.
 *
 * @author Gabriel Selzer
 */
public class OverloadsTest {

	@Test
	public void testArgumentsAtCaret() {
		final RSyntaxTextArea area = new RSyntaxTextArea();
		area.setText("x = f(g(1, 2), 'a,b', [3, 4], y)");
		area.setCaretPosition(area.getText().indexOf('y'));
		assertEquals(Arrays.asList("g(1, 2)", " 'a,b'", " [3, 4]", " y"),
			Overloads.argumentsAtCaret(area));

		// Inside a nested call, the innermost call's arguments.
		area.setCaretPosition(area.getText().indexOf('2'));
		assertEquals(Arrays.asList("1", " 2"), Overloads.argumentsAtCaret(area));

		// Outside any call, or inside a list, there is no argument list.
		area.setCaretPosition(2);
		assertNull(Overloads.argumentsAtCaret(area));
		area.setCaretPosition(area.getText().indexOf('4'));
		assertNull(Overloads.argumentsAtCaret(area));
	}

	@Test
	public void testArgumentFit() {
		// Python ints match integral parameters, and convert to floating ones.
		assertEquals(Overloads.MATCH, Overloads.fit("long", "int"));
		assertEquals(Overloads.CONVERSION, Overloads.fit("long", "double"));
		// Python floats match floating parameters only.
		assertEquals(Overloads.MATCH, Overloads.fit("double", "float"));
		assertEquals(Overloads.NONE, Overloads.fit("double", "int"));
		// Reference types follow assignability, including boxing and arrays.
		assertEquals(Overloads.MATCH, Overloads.fit("java.util.ArrayList",
			"java.util.List"));
		assertEquals(Overloads.MATCH, Overloads.fit("java.lang.Integer", "int"));
		assertEquals(Overloads.MATCH, Overloads.fit("java.lang.String",
			"java.lang.Object"));
		assertEquals(Overloads.NONE, Overloads.fit("java.lang.String", "int[]"));
		assertEquals(Overloads.NONE, Overloads.fit("long", "java.lang.String"));
		// Unknown types never rule out or demote anything.
		assertEquals(Overloads.MATCH, Overloads.fit(null, "int"));
		assertEquals(Overloads.MATCH, Overloads.fit("no.such.Type", "int"));
	}

	@Test
	public void testOverloadFit() {
		final Completion floats = method("float", "float");
		// An overload is as good as its worst argument.
		assertEquals(Overloads.CONVERSION, Overloads.fit(floats, Arrays.asList(
			"double", "long")));
		assertEquals(Overloads.NONE, Overloads.fit(floats, Arrays.asList(
			"java.lang.String", "double")));
		// Unknown (null) arguments don't count, but extra known ones do.
		assertEquals(Overloads.MATCH, Overloads.fit(floats, Arrays.asList(null,
			"double")));
		assertEquals(Overloads.NONE, Overloads.fit(floats, Arrays.asList(null,
			null, "double")));
	}

	private static Completion method(final String... paramTypes) {
		final List<Completion.Parameter> params = new ArrayList<>();
		for (final String type : paramTypes) {
			params.add(new Completion.Parameter(null, type));
		}
		return Completion.builder("f").kind(Completion.Kind.METHOD).parameters(
			params).build();
	}
}
