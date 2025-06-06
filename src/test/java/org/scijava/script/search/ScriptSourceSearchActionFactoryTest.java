/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2025 SciJava developers.
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

package org.scijava.script.search;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.script.ScriptInfo;
import org.scijava.search.SearchActionFactory;
import org.scijava.search.SearchResult;
import org.scijava.search.module.ModuleSearchResult;
import org.scijava.ui.swing.script.search.ScriptSourceSearchActionFactory;

import java.io.StringReader;

import static org.junit.Assert.assertTrue;

/**
 * Basic regression test for {@link ScriptSourceSearchActionFactory}
 *
 * @author Gabriel Selzer
 */
public class ScriptSourceSearchActionFactoryTest {

	private Context context;
	private final SearchActionFactory factory =
			new ScriptSourceSearchActionFactory();

	// -- Test setup --

	@Before public void setUp() {
		context = new Context();
	}

	@After public void tearDown() {
		context.dispose();
	}

	@Test public void testScriptSourceAction()
	{
		final String script = "\"Hello World!\"\n";
		final StringReader reader = new StringReader(script);
		final ScriptInfo info = new ScriptInfo(context, "test.groovy", reader);
		final SearchResult searchResult = new ModuleSearchResult(info, "");
		assertTrue(factory.supports(searchResult));
	}

}
