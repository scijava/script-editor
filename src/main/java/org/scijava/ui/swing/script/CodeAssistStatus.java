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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.scijava.InstantiableException;
import org.scijava.lsp.LanguageServerPlugin;
import org.scijava.lsp.LanguageServerService;
import org.scijava.lsp.ScriptDialect;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;
import org.scijava.plugin.SciJavaPlugin;
import org.scijava.script.ScriptLanguage;
import org.scijava.util.Types;

/**
 * What code intelligence (completion, hover, problems) an editor has for its
 * language, and why. This includes which kind of support is installed, as well
 * as which pieces are missing or misconfigured if a broken state is detected.
 * Shown by the status bar, to help troubleshoot non-working code intelligence.
 *
 * @author Curtis Rueden
 */
final class CodeAssistStatus {

	/** How well the language is supported, as the status bar shows it. */
	enum Level {
			/** Full support: a language support plugin, or a language server. */
			OK,
			/** Some support, but not from a language server (e.g. legacy). */
			LIMITED,
			/** No code intelligence for the language: nothing is wrong. */
			NONE,
			/** Turned off by the user. */
			OFF,
			/** Something is installed but broken or incomplete. */
			ERROR
	}

	/** Which kind of support is installed. */
	enum Backend {
			/** A heavy RSTA language support plugin, e.g. the Java parser. */
			PLUGIN("Language support plugin"),
			/** Language servers, via scijava-lsp. */
			SERVER("Language server"),
			/** RSyntaxTextArea's own built-in support for the syntax style. */
			LEGACY("Built-in (legacy) completion"),
			/** Java completion, as a fallback for an unsupported language. */
			JAVA_FALLBACK("Java completion (fallback)"),
			/** Nothing. */
			NONE("None");

		private final String label;

		Backend(final String label) {
			this.label = label;
		}
	}

	/** One line of the report: a check that passed or failed. */
	static final class Check {

		final boolean ok;
		final String what;
		final String detail;

		Check(final boolean ok, final String what, final String detail) {
			this.ok = ok;
			this.what = what;
			this.detail = detail;
		}
	}

	/** The last failure of a language server, as seen while editing. */
	static final class Failure {

		final String what;
		final Throwable cause;
		final LocalTime time = LocalTime.now();

		Failure(final String what, final Throwable cause) {
			this.what = what;
			this.cause = cause;
		}
	}

	/**
	 * Whether language servers can serve a script language: its dialect, the
	 * server plugin for the dialect's output language, and any such plugins that
	 * are installed but cannot be loaded.
	 */
	static final class Servers {

		/** The dialect of the language, or null if none. */
		ScriptDialect dialect;
		/** The LSP language the dialect makes scripts into, or null. */
		String output;
		/** Whether a server plugin serves {@link #output}. */
		boolean served;
		/** Why asking the service failed unexpectedly, or null. */
		Throwable error;
		final List<Check> checks = new ArrayList<>();
		/** Plugins installed but not loadable: a likely cause of trouble. */
		final List<Check> broken = new ArrayList<>();

		/** Whether the language is served by language servers. */
		boolean ok() {
			return served && error == null;
		}

		/**
		 * Whether something is half-installed: a dialect without a server, or
		 * plugins that do not load.
		 */
		boolean misconfigured() {
			return error != null || !broken.isEmpty() || dialect != null && !served;
		}
	}

	final Level level;
	/** The level before any failure: what {@link #recovered()} goes back to. */
	private final Level configured;
	final Backend backend;
	final String languageName;
	/** The class of the installed support, if any. */
	final String supportClass;
	final boolean enabled;
	final boolean keyless;
	final Servers servers;
	final Failure failure;

	private CodeAssistStatus(final Level level, final Level configured,
		final Backend backend, final String languageName,
		final String supportClass, final boolean enabled, final boolean keyless,
		final Servers servers, final Failure failure)
	{
		this.level = level;
		this.configured = configured;
		this.backend = backend;
		this.languageName = languageName;
		this.supportClass = supportClass;
		this.enabled = enabled;
		this.keyless = keyless;
		this.servers = servers;
		this.failure = failure;
	}

	/**
	 * The status of an editor of the given language.
	 *
	 * @param support The support installed, or null.
	 * @param servers What {@link #servers} says of the language.
	 */
	static CodeAssistStatus of(final String languageName, final Backend backend,
		final Object support, final boolean enabled, final boolean keyless,
		final Servers servers)
	{
		final Level level;
		if (servers.misconfigured() && backend != Backend.PLUGIN) level =
			Level.ERROR;
		else if (!enabled) level = Level.OFF;
		else if (backend == Backend.PLUGIN || backend == Backend.SERVER) level =
			Level.OK;
		else if (backend == Backend.NONE) level = Level.NONE;
		else level = Level.LIMITED;
		return new CodeAssistStatus(level, level, backend, languageName,
			support == null ? null : support.getClass().getName(), enabled, keyless,
			servers, null);
	}

	/** This status, after a language server failed. */
	CodeAssistStatus failed(final String what, final Throwable cause) {
		return new CodeAssistStatus(Level.ERROR, configured, backend,
			languageName, supportClass, enabled, keyless, servers, new Failure(what,
				cause));
	}

	/** This status, as before any failure of its language server. */
	CodeAssistStatus recovered() {
		if (failure == null) return this;
		return new CodeAssistStatus(configured, configured, backend, languageName,
			supportClass, enabled, keyless, servers, null);
	}

	/**
	 * Finds out whether language servers can serve a script language, and if
	 * not, why. Never throws: a broken installation is what this reports.
	 */
	static Servers servers(final LanguageServerService service,
		final PluginService plugins, final ScriptLanguage language)
	{
		final Servers s = new Servers();
		s.checks.add(new Check(true, "scijava-lsp", location(
			LanguageServerService.class)));
		final List<PluginInfo<ScriptDialect>> dialects = infos(plugins,
			ScriptDialect.class, s);
		final List<PluginInfo<LanguageServerPlugin>> servers = infos(plugins,
			LanguageServerPlugin.class, s);
		try {
			try {
				s.dialect = service.dialect(language);
			}
			catch (final IllegalArgumentException exc) {
				// NB: The language has no dialect.
			}
			if (s.dialect == null) {
				s.checks.add(new Check(false, "Dialect for " + language
					.getLanguageName(), "None, so language servers do not serve " +
						"this language (" + dialects.size() +
						" dialect plugin(s) installed: " + names(dialects) +
						"). If they should, the language's scripting plugin (e.g. " +
						"scripting-" + language.getLanguageName().toLowerCase() +
						") must be a version which provides a dialect."));
				return s;
			}
			s.output = s.dialect.outputLanguage();
			s.checks.add(new Check(true, "Dialect for " + language
				.getLanguageName(), s.dialect.getClass().getName() + " in " +
					location(s.dialect.getClass()) + ", making scripts into '" +
					s.output + "' code"));
			s.served = service.supports(s.output);
			if (s.served) {
				s.checks.add(new Check(true, "Language server for '" + s.output +
					"'", "Served (" + servers.size() + " server plugin(s) installed: " +
						names(servers) + ")"));
			}
			else {
				s.checks.add(new Check(false, "Language server for '" + s.output +
					"'", "No server plugin serves it (" + servers.size() +
						" server plugin(s) installed: " + names(servers) +
						"). Install " + serverArtifact(s.output) +
						" and its dependencies into the jars folder, then restart."));
			}
		}
		catch (final RuntimeException | LinkageError exc) {
			s.error = exc;
			s.checks.add(new Check(false, "Language server service",
				"Failed: " + exc + ". Some JARs are probably of incompatible " +
					"versions."));
		}
		return s;
	}

	/** The installed plugins of a type, noting those that do not load. */
	private static <P extends SciJavaPlugin> List<PluginInfo<P>> infos(
		final PluginService plugins, final Class<P> type, final Servers s)
	{
		final List<PluginInfo<P>> infos;
		try {
			infos = plugins.getPluginsOfType(type);
		}
		catch (final RuntimeException | LinkageError exc) {
			s.broken.add(new Check(false, type.getSimpleName() + " plugins",
				"Could not be listed: " + exc));
			return Collections.emptyList();
		}
		for (final PluginInfo<P> info : infos) {
			try {
				info.loadClass();
			}
			catch (final InstantiableException | RuntimeException
					| LinkageError exc)
			{
				s.broken.add(new Check(false, info.getClassName(),
					"Installed, but cannot be loaded: " + rootCause(exc) +
						". Is a dependency missing, or of the wrong version?"));
			}
		}
		return infos;
	}

	/** The artifact which serves an LSP language, as far as known. */
	private static String serverArtifact(final String language) {
		switch (language) {
			case "groovy":
				return "scijava-lsp-groovy";
			case "jython":
				return "scijava-lsp-jython";
			case "python":
				return "scijava-lsp-ty";
			default:
				return "a scijava-lsp plugin for '" + language + "'";
		}
	}

	private static String names(final List<? extends PluginInfo<?>> infos) {
		if (infos.isEmpty()) return "none";
		final List<String> names = new ArrayList<>();
		for (final PluginInfo<?> info : infos) {
			final String c = info.getClassName();
			names.add(c.substring(c.lastIndexOf('.') + 1));
		}
		return String.join(", ", names);
	}

	/** Where a class comes from: its JAR (or folder), for spotting versions. */
	static String location(final Class<?> c) {
		try {
			final URL url = Types.location(c);
			return url == null ? "(unknown location)" : url.getPath();
		}
		catch (final RuntimeException exc) {
			return "(unknown location)";
		}
	}

	private static Throwable rootCause(final Throwable t) {
		Throwable c = t;
		while (c.getCause() != null && c.getCause() != c)
			c = c.getCause();
		return c;
	}

	// -- Presentation --

	/** One line, for the tooltip of the status bar. */
	String summary() {
		switch (level) {
			case OK:
				return languageName + " code intelligence: " + backend.label +
					(backend == Backend.SERVER && servers.output != null ? " ('" +
						servers.output + "')" : "") + ". Ctrl+Space to complete";
			case LIMITED:
				return languageName + " code intelligence: limited (" +
					backend.label + ")";
			case NONE:
				return "No code intelligence for " + languageName;
			case OFF:
				return "Code completion is turned off (Options > Enable Autocompletion)";
			default:
				return failure != null ? "Language server problem: " + failure.what +
					" failed" : languageName +
						" code intelligence is not set up correctly";
		}
	}

	/** The full report, as HTML: what is installed, what is not, and why. */
	String report() {
		final StringBuilder sb = new StringBuilder("<html><body>");
		sb.append("<h2>").append(esc(summary())).append("</h2>");
		sb.append("<p><b>Active support:</b> ").append(esc(backend.label));
		if (supportClass != null) sb.append(" <small>(").append(esc(
			supportClass)).append(")</small>");
		sb.append("<br><b>Completion:</b> ").append(enabled ? keyless
			? "on, Ctrl+Space or as you type" : "on, Ctrl+Space"
			: "off (Options &gt; Enable Autocompletion)").append("</p>");
		if (failure != null) {
			sb.append("<h3>Last language server failure</h3><p>").append(esc(
				failure.what)).append(" failed at ").append(failure.time.format(
					DateTimeFormatter.ofPattern("HH:mm:ss"))).append(":</p><pre>")
				.append(esc(trace(failure.cause))).append("</pre>");
		}
		if (backend == Backend.PLUGIN) {
			sb.append("<p>A language support plugin handles this language: " +
				"language servers are not used.</p>");
		}
		else {
			sb.append("<h3>Language server checks</h3><ul>");
			for (final Check c : servers.checks)
				item(sb, c);
			sb.append("</ul>");
		}
		if (!servers.broken.isEmpty()) {
			sb.append("<h3>Plugins that failed to load</h3><ul>");
			for (final Check c : servers.broken)
				item(sb, c);
			sb.append("</ul>");
		}
		if (servers.error != null) {
			sb.append("<pre>").append(esc(trace(servers.error))).append("</pre>");
		}
		sb.append("</body></html>");
		return sb.toString();
	}

	private static void item(final StringBuilder sb, final Check c) {
		sb.append("<li><b>").append(c.ok ? "&#10004; " : "&#10008; ").append(esc(
			c.what)).append(":</b> ").append(esc(c.detail)).append("</li>");
	}

	private static String trace(final Throwable t) {
		final StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	private static String esc(final String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
			.replace(">", "&gt;");
	}
}
