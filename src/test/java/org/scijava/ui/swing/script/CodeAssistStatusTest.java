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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import javax.script.ScriptEngine;

import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.lsp.Environment;
import org.scijava.lsp.LanguageServerService;
import org.scijava.lsp.ScriptDialect;
import org.scijava.plugin.PluginService;
import org.scijava.script.AbstractScriptLanguage;
import org.scijava.script.ScriptLanguage;
import org.scijava.service.AbstractService;
import org.scijava.ui.swing.script.CodeAssistStatus.Backend;
import org.scijava.ui.swing.script.CodeAssistStatus.Level;

/**
 * Tests {@link CodeAssistStatus}: that each way of being (half-)installed is
 * told apart, and says what is missing.
 *
 * @author Curtis Rueden
 */
public class CodeAssistStatusTest {

	private static final ScriptLanguage GROOVY = new AbstractScriptLanguage() {

		@Override
		public String getLanguageName() {
			return "Groovy";
		}

		@Override
		public ScriptEngine getScriptEngine() {
			return null;
		}
	};

	private static final ScriptDialect DIALECT = new ScriptDialect() {

		@Override
		public boolean supports(final ScriptLanguage language) {
			return true;
		}

		@Override
		public String outputLanguage() {
			return "groovy";
		}

		@Override
		public Header header(final Map<String, Variable> variables) {
			return null;
		}
	};

	private Context context;
	private PluginService plugins;

	@Before
	public void setUp() {
		context = new Context(PluginService.class);
		plugins = context.service(PluginService.class);
	}

	@After
	public void tearDown() {
		context.dispose();
	}

	@Test
	public void testServed() {
		final CodeAssistStatus.Servers s = CodeAssistStatus.servers(new Servers(
			DIALECT, true, null), plugins, GROOVY);
		assertTrue(s.ok());
		assertFalse(s.misconfigured());
		assertEquals("groovy", s.output);
		final CodeAssistStatus status = CodeAssistStatus.of("Groovy",
			Backend.SERVER, null, true, false, s);
		assertEquals(Level.OK, status.level);

		final CodeAssistStatus failed = status.failed("Code completion",
			new IllegalStateException("boom"));
		assertEquals(Level.ERROR, failed.level);
		assertNotNull(failed.failure);
		assertTrue(failed.report().contains("boom"));
		final CodeAssistStatus recovered = failed.recovered();
		assertEquals(Level.OK, recovered.level);
		assertNull(recovered.failure);
		assertSame(status, status.recovered());
	}

	@Test
	public void testDialectWithoutServer() {
		final CodeAssistStatus.Servers s = CodeAssistStatus.servers(new Servers(
			DIALECT, false, null), plugins, GROOVY);
		assertFalse(s.ok());
		assertTrue(s.misconfigured());
		final CodeAssistStatus status = CodeAssistStatus.of("Groovy",
			Backend.NONE, null, true, false, s);
		assertEquals(Level.ERROR, status.level);
		assertTrue(status.report().contains("scijava-lsp-groovy"));
	}

	@Test
	public void testNoDialect() {
		final CodeAssistStatus.Servers s = CodeAssistStatus.servers(new Servers(
			null, false, null), plugins, GROOVY);
		assertFalse(s.ok());
		assertFalse(s.misconfigured());
		assertEquals(Level.NONE, CodeAssistStatus.of("Groovy", Backend.NONE, null,
			true, false, s).level);
		assertEquals(Level.LIMITED, CodeAssistStatus.of("Groovy", Backend.LEGACY,
			null, true, false, s).level);
		assertTrue(CodeAssistStatus.of("Groovy", Backend.NONE, null, true, false,
			s).report().contains("scripting-groovy"));
	}

	@Test
	public void testBrokenService() {
		final CodeAssistStatus.Servers s = CodeAssistStatus.servers(new Servers(
			DIALECT, true, new NoClassDefFoundError("org/apache/groovy/Missing")),
			plugins, GROOVY);
		assertFalse(s.ok());
		assertTrue(s.misconfigured());
		final CodeAssistStatus status = CodeAssistStatus.of("Groovy",
			Backend.NONE, null, true, false, s);
		assertEquals(Level.ERROR, status.level);
		assertTrue(status.report().contains("org/apache/groovy/Missing"));
	}

	@Test
	public void testPluginWins() {
		final CodeAssistStatus.Servers s = CodeAssistStatus.servers(new Servers(
			DIALECT, false, null), plugins, GROOVY);
		// NB: A language support plugin needs no servers.
		assertEquals(Level.OK, CodeAssistStatus.of("Groovy", Backend.PLUGIN, null,
			true, false, s).level);
	}

	@Test
	public void testDisabled() {
		final CodeAssistStatus.Servers s = CodeAssistStatus.servers(new Servers(
			DIALECT, true, null), plugins, GROOVY);
		assertEquals(Level.OFF, CodeAssistStatus.of("Groovy", Backend.SERVER,
			null, false, false, s).level);
	}

	/** Servers as configured: a dialect or none, served or not, or broken. */
	private static final class Servers extends AbstractService implements
		LanguageServerService
	{

		private final ScriptDialect dialect;
		private final boolean served;
		private final Error broken;

		private Servers(final ScriptDialect dialect, final boolean served,
			final Error broken)
		{
			this.dialect = dialect;
			this.served = served;
			this.broken = broken;
		}

		@Override
		public ScriptDialect dialect(final ScriptLanguage language) {
			if (broken != null) throw broken;
			if (dialect == null) throw new IllegalArgumentException("none");
			return dialect;
		}

		@Override
		public boolean supports(final ScriptLanguage language) {
			return dialect != null && served;
		}

		@Override
		public boolean supports(final String language) {
			return served;
		}

		@Override
		public LanguageServer launch(final String language,
			final Environment environment)
		{
			throw new UnsupportedOperationException();
		}
	}
}
