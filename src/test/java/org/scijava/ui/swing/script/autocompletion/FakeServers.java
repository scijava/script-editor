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

import java.util.Collections;
import java.util.Map;

import javax.script.ScriptEngine;

import org.eclipse.lsp4j.services.LanguageServer;
import org.scijava.Context;
import org.scijava.code.lsp.Environment;
import org.scijava.code.lsp.LanguageServerService;
import org.scijava.code.lsp.ScriptDialect;
import org.scijava.code.lsp.ScriptDocument;
import org.scijava.code.lsp.ScriptSession;
import org.scijava.script.AbstractScriptLanguage;
import org.scijava.script.ScriptLanguage;
import org.scijava.service.AbstractService;

/**
 * A {@link LanguageServerService} serving every script with one fake server,
 * which sees scripts as they are: for tests of what editors do with sessions.
 *
 * @author Gabriel Selzer
 */
final class FakeServers extends AbstractService implements
	LanguageServerService
{

	/** A script language: any will do, as all are served alike here. */
	private static final ScriptLanguage LANGUAGE =
		new AbstractScriptLanguage()
	{

		@Override
		public ScriptEngine getScriptEngine() {
			return null;
		}
	};

	/** Shows scripts as they are. */
	private static final ScriptDialect AS_IS = new ScriptDialect() {

		@Override
		public boolean supports(final ScriptLanguage language) {
			return true;
		}

		@Override
		public String languageId() {
			return "plaintext";
		}

		@Override
		public Header header(final Map<String, Class<?>> parameters) {
			return new Header("", Collections.emptySet());
		}
	};

	/** Where sessions look for a log service: there is none. */
	private static final Context EMPTY = new Context(true);

	private final LanguageServer server;

	private FakeServers(final LanguageServer server) {
		this.server = server;
	}

	@Override
	public Context context() {
		return EMPTY;
	}

	@Override
	public Context getContext() {
		return EMPTY;
	}

	/** A session whose server is the given one. */
	static ScriptSession session(final LanguageServer server) {
		return new ScriptSession(new FakeServers(server), LANGUAGE);
	}

	@Override
	public ScriptDialect dialect(final ScriptLanguage language) {
		return AS_IS;
	}

	@Override
	public ScriptDocument document(final String script,
		final ScriptDialect dialect)
	{
		return ScriptDocument.of(script);
	}

	@Override
	public boolean supports(final ScriptLanguage language) {
		return true;
	}

	@Override
	public boolean supports(final String languageId) {
		return true;
	}

	@Override
	public LanguageServer launch(final String languageId,
		final Environment environment)
	{
		return server;
	}

	@Override
	public Map<String, Class<?>> scriptParameters(final String text) {
		return Collections.emptyMap();
	}
}
