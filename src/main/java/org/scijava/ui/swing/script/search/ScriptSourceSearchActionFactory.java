/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2023 SciJava developers.
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

package org.scijava.ui.swing.script.search;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.module.ModuleInfo;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptInfo;
import org.scijava.script.ScriptService;
import org.scijava.search.DefaultSearchAction;
import org.scijava.search.SearchAction;
import org.scijava.search.SearchActionFactory;
import org.scijava.search.SearchResult;
import org.scijava.search.module.ModuleSearchResult;
import org.scijava.ui.swing.script.TextEditor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Search action for viewing the source code of a SciJava module.
 *
 * @author Curtis Rueden
 */
@Plugin(type = SearchActionFactory.class)
public class ScriptSourceSearchActionFactory implements SearchActionFactory {

	@Parameter private Context context;

	@Parameter private LogService log;
	@Parameter private ScriptService scriptService;

	@Override public boolean supports(final SearchResult result) {
		if (!(result instanceof ModuleSearchResult)) return false;
		final ModuleInfo info = ((ModuleSearchResult) result).info();
		return info instanceof ScriptInfo;
	}

	@Override public SearchAction create(final SearchResult result) {
		ModuleInfo info = ((ModuleSearchResult) result).info();
		return new DefaultSearchAction("Source", //
				() -> openScriptInTextEditor((ScriptInfo) info));
	}

	private void openScriptInTextEditor(final ScriptInfo script) {
		final TextEditor editor = new TextEditor(context);

		final File scriptFile = getScriptFile(script);
		if (scriptFile.exists()) {
			// script is a file on disk; open it
			editor.open(scriptFile);
			editor.setVisible(true);
			return;
		}

		// try to read the script from its associated reader
		final StringBuilder sb = new StringBuilder();
		try (final BufferedReader reader = script.getReader()) {
			if (reader != null) {
				// script is text from somewhere (a URL?); read it
				while (true) {
					final String line = reader.readLine();
					if (line == null) break; // eof
					sb.append(line);
					sb.append("\n");
				}
			}
		}
		catch (final IOException exc) {
			log.error("Error reading script: " + script.getPath(), exc);
		}

		if (sb.length() > 0) {
			// script came from somewhere, but not from a regular file
			editor.getEditorPane().setFileName(scriptFile);
			editor.getEditorPane().setText(sb.toString());
		}
		else {
			// give up, and report the problem
			final String error = "[Cannot load script: " + script.getPath() + "]";
			editor.getEditorPane().setText(error);
		}

		editor.setVisible(true);
	}

	private File getScriptFile(final ScriptInfo script) {
		final URL scriptURL = script.getURL();
		try {
			if (scriptURL != null) return new File(scriptURL.toURI());
		}
		catch (final URISyntaxException | IllegalArgumentException exc) {
			log.debug(exc);
		}
		final File scriptDir = scriptService.getScriptDirectories().get(0);
		return new File(scriptDir.getPath() + File.separator + script.getPath());
	}

}
