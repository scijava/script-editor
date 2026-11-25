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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests {@link SignatureLabel}'s reading of parameters, whichever way a
 * language writes them.
 *
 * @author Gabriel Selzer
 */
public class SignatureLabelTest {

	@Test
	public void testTypeAndName() {
		// Type first, e.g. Java's.
		assertArrayEquals(new String[] { "java.util.Map<K, V>", "m" },
			SignatureLabel.typeAndName("java.util.Map<K, V> m"));
		// Name first, e.g. Python's; with or without a default.
		assertArrayEquals(new String[] { "int | None", "indent" }, SignatureLabel
			.typeAndName("indent: int | None = None"));
		assertArrayEquals(new String[] { "float", "a" }, SignatureLabel
			.typeAndName("a: float"));
		// Untyped.
		assertArrayEquals(new String[] { null, "kw" }, SignatureLabel.typeAndName(
			"kw"));
		assertArrayEquals(new String[] { null, "kw" }, SignatureLabel.typeAndName(
			"kw=1"));
	}

	@Test
	public void testSimple() {
		assertEquals("String s", SignatureLabel.simple("java.lang.String s"));
		// Name first: as it is.
		assertEquals("p: os.PathLike", SignatureLabel.simple("p: os.PathLike"));
	}
}
