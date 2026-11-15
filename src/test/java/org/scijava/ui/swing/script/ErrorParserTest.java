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

package org.scijava.ui.swing.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.script.ScriptException;

import org.junit.Test;

public class ErrorParserTest {

	private static Set<Integer> scrape(final String log, final String ext) {
		return ErrorParser.scrape(log, "Script." + ext, List.of(ext), false);
	}

	@Test
	public void testRuntimeFormats() {
		assertEquals(Set.of(51), scrape("\tat Script1.run(Script1.groovy:51)", "groovy"));
		assertEquals(Set.of(51), scrape("File \"New_.py\", line 51, in <module>", "py"));
		assertEquals(Set.of(51), scrape("<main> at Batch_Convert.rb:51", "rb"));
		assertEquals(Set.of(51), scrape(" at line number 51 at column number 18", "scala"));
		assertEquals(Set.of(51), scrape("or class name: Systesm : at Line: 51 : in file: ", "bsh"));
		assertEquals(Set.of(51), scrape("\tat jdk.nashorn.internal.scripts.Script$15$Greeting.:program(Greeting.js:51)", "js"));
	}

	@Test
	public void testIgnoresLookalikes() {
		assertTrue(scrape("Connecting to localhost:8080", "groovy").isEmpty());
		assertTrue(scrape("Invalid command line argument 5", "py").isEmpty());
		assertTrue(scrape("at 12:30:45 inline pipeline deadline: 3", "py").isEmpty());
		assertTrue(scrape("\tat foo.Bar.run(Bar.java:123)", "r").isEmpty());
		assertTrue(scrape("\tat org.python.core.PyException.<init>(PyException.java:123)", "py").isEmpty());
		assertTrue(scrape("\tat org.codehaus.groovy.runtime.callsite.Foo.call(Foo.groovy:99)", "groovy").isEmpty());
	}

	@Test
	public void testPrefersOwnScript() {
		final String log = "at Helper.run(Helper.groovy:12)\nat Script.run(Script.groovy:5)";
		assertEquals(Set.of(5), scrape(log, "groovy"));
		// Names in frames may differ from the editor's: use what we have
		assertEquals(Set.of(12), scrape("at Helper.run(Helper.groovy:12)", "groovy"));
	}

	@Test
	public void testJava() {
		final String name = "Foo.java";
		assertEquals(Set.of(12), ErrorParser.scrape("/tmp/Foo.java:12: error: ';' expected", name, List.of("java"), true));
		assertEquals(Set.of(12), ErrorParser.scrape("Foo.java:12: error: ';' expected", name, List.of("java"), true));
		assertEquals(Set.of(7), ErrorParser.scrape("\tat Foo.run(Foo.java:7)", name, List.of("java"), true));
		assertTrue(ErrorParser.scrape("/tmp/MyFoo.java:12: error", name, List.of("java"), true).isEmpty());
	}

	@Test
	public void testSeveralLinesAndOverflow() {
		assertEquals(new TreeSet<>(List.of(3, 8)), scrape("x.groovy:3\nnoise\ny.groovy:8\nz.groovy:99999999999", "groovy"));
	}

	@Test
	public void testFromThrowable() {
		final ScriptException se = new ScriptException("boom", "/some/dir/Script.groovy", 4);
		final Exception wrapper = new RuntimeException("wrapped", se);
		wrapper.setStackTrace(new StackTraceElement[] { //
			new StackTraceElement("Script", "run", "Script.groovy", 9), //
			new StackTraceElement("org.Foo", "bar", "Foo.java", 77) });
		final TreeSet<Integer> lines = new TreeSet<>();
		ErrorParser.fromThrowable(wrapper, "Script.groovy", lines);
		assertEquals(Set.of(4, 9), lines);
		// Errors in other files are not ours
		lines.clear();
		ErrorParser.fromThrowable(new ScriptException("x", "Other.groovy", 5), "Script.groovy", lines);
		assertTrue(lines.isEmpty());
	}

	@Test
	public void testAverageColors() {
		final Color c = ErrorParser.averageColors(new Color(0, 0, 255), new Color(0, 255, 0));
		assertEquals(c.getGreen(), c.getBlue());
	}
}
