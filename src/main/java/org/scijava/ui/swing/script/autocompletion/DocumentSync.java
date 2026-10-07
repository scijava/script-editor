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

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;
import org.scijava.code.lsp.Positions;
import org.scijava.script.ScriptLanguage;

/**
 * Keeps a language server's copy of an edited script up to date: opens it,
 * sends its changes (the whole text), and closes it. Its URI is its file's,
 * if saved; otherwise an {@code untitled:} one. Saving it under another name
 * closes the old document and opens the new one.
 *
 * @author Gabriel Selzer
 */
public class DocumentSync {

	private static final AtomicInteger UNTITLED = new AtomicInteger();

	private final LanguageServer server;
	private final ScriptLanguage language;
	private final String untitled;
	private volatile Supplier<File> file;

	private String uri;
	private String sent;
	private int version;

	public DocumentSync(final LanguageServer server,
		final ScriptLanguage language)
	{
		this.server = server;
		this.language = language;
		final List<String> extensions = language == null ? Collections
			.emptyList() : language.getExtensions();
		untitled = "untitled:/script-" + UNTITLED.incrementAndGet() + (extensions
			.isEmpty() ? "" : "." + extensions.get(0));
	}

	/** The server documents are synchronized with. */
	public LanguageServer server() {
		return server;
	}

	/** Sets a supplier of the script's file (which may supply null). */
	public void setFile(final Supplier<File> file) {
		this.file = file;
	}

	/**
	 * Brings the server's copy up to date with the given text (opening it, if
	 * needed).
	 *
	 * @return The document's URI.
	 */
	public synchronized String sync(final String text) {
		final String current = currentUri();
		if (uri != null && !uri.equals(current)) close();
		if (uri == null) {
			uri = current;
			version = 1;
			sent = text;
			server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri, languageId(), version, text)));
		}
		else if (!text.equals(sent)) {
			sent = text;
			version++;
			server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
				new VersionedTextDocumentIdentifier(uri, version), Collections
					.singletonList(new TextDocumentContentChangeEvent(text))));
		}
		return uri;
	}

	/** Closes the server's copy, if open. */
	public synchronized void close() {
		if (uri == null) return;
		server.getTextDocumentService().didClose(new DidCloseTextDocumentParams(
			new TextDocumentIdentifier(uri)));
		uri = null;
		sent = null;
	}

	/** The open document's URI, or null if none. */
	public synchronized String uri() {
		return uri;
	}

	/** The text last sent, or null if none. */
	public synchronized String text() {
		return sent;
	}

	/** The position of an offset of the given text, for requests. */
	public static Position position(final String text, final int offset) {
		return Positions.position(text, Math.max(0, Math.min(offset, text
			.length())));
	}

	/** The offset of a position in the given text. */
	public static int offset(final String text, final Position position) {
		return Positions.offset(text, position);
	}

	private String currentUri() {
		final Supplier<File> f = file;
		final File path = f == null ? null : f.get();
		return path == null ? untitled : path.getAbsoluteFile().toURI()
			.toString();
	}

	private String languageId() {
		return language == null ? "plaintext" : language.getLanguageName();
	}
}
